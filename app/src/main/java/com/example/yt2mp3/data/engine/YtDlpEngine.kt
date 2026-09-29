package com.example.yt2mp3.data.engine

import android.content.Context
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.data.util.UrlValidator
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.PlaylistInfo
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoMetadata
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** How one queued item should be produced. Section bounds are in seconds. */
data class DownloadOptions(
    val format: AudioFormat,
    val quality: AudioQuality,
    val title: String,
    val artist: String?,
    val sectionStart: Int? = null,
    val sectionEnd: Int? = null,
)

/**
 * Engine abstraction so UI/queue never touch yt-dlp directly (PRD §53.4).
 * Runs fully on-device; no app backend. Only fetches content the extractor
 * can access normally — no DRM bypass, no credential injection (PRD §1.2).
 */
interface YtDlpEngine {
    suspend fun fetchMetadata(url: String): VideoMetadata
    suspend fun search(query: String): List<VideoEntry>
    suspend fun fetchPlaylist(url: String): PlaylistInfo

    /** Real cancellation: [cancel] kills the native process via destroyProcessById. */
    fun cancel(processId: String)

    suspend fun downloadAudio(
        url: String,
        processId: String,
        workDir: File,
        options: DownloadOptions,
        onProgress: (percent: Int?, stage: String) -> Unit,
        isCancelled: () -> Boolean,
    ): File
}

/**
 * Readers = yt-dlp runs, writer = engine update. Keeps an update from
 * replacing the yt-dlp binary while a preview or download is using it.
 */
object EngineLock {
    val rw = ReentrantReadWriteLock()
}

class YtDlpEngineImpl(context: Context) : YtDlpEngine {
    private val appContext = context.applicationContext
    @Volatile private var initialized = false

    /** yt-dlp's own cache (signature functions etc.). The library passes --no-cache-dir otherwise. */
    private val ytCacheDir = File(appContext.cacheDir, "ytdlp-cache")

    /** Preview's --dump-json output, reused by the download so extraction runs once. */
    private val infoDir = File(appContext.cacheDir, "yt-info")

    fun warmUp() {
        runCatching { ensureInit() }
    }

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                    initialized = true
                }
            }
        }
    }

    override fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /*
     * No --extractor-args player_client override: yt-dlp's own default client
     * set is what its maintainers keep working against YouTube. Forcing
     * android/mweb/web was verified (2026-09) to fail with "Requested format
     * is not available" on both the bundled and the latest yt-dlp.
     */
    private fun request(urls: List<String>) = YoutubeDLRequest(urls).apply {
        addOption("--cache-dir", ytCacheDir.absolutePath)
        addOption("--socket-timeout", "20")
        addOption("--retries", "3")
        addOption("--extractor-retries", "3")
    }

    /** Blocking yt-dlp run; coroutine cancellation interrupts it, which kills the process. */
    private suspend fun runCancellable(req: YoutubeDLRequest): YoutubeDLResponse {
        ensureInitIo()
        return runInterruptible(Dispatchers.IO) {
            EngineLock.rw.read { YoutubeDL.getInstance().execute(req) }
        }
    }

    private suspend fun ensureInitIo() = withContext(Dispatchers.IO) { ensureInit() }

    // ---------------------------------------------------------------- preview

    override suspend fun fetchMetadata(url: String): VideoMetadata {
        val clean = UrlValidator.extractFirstSupportedUrl(url)
            ?: throw IllegalArgumentException(DownloadErrorKind.INVALID_URL.name)
        val line = try {
            dumpInfo(clean)
        } catch (t: Throwable) {
            AppLog.e("ENGINE", "metadata failed url=$clean", t)
            throw t
        }
        return withContext(Dispatchers.Default) { YtJson.metadata(clean, JSONObject(line)) }
    }

    /** Runs full extraction once and caches the JSON for the download step. */
    private suspend fun dumpInfo(cleanUrl: String): String {
        val req = request(listOf(cleanUrl)).apply {
            addOption("--no-playlist")
            addOption("-f", "bestaudio/best")
            addOption("--dump-json")
        }
        val line = YtJson.firstJsonLine(runCancellable(req).out)
            ?: throw IllegalStateException("Failed to fetch video information")
        UrlValidator.videoId(cleanUrl)?.let { id ->
            withContext(Dispatchers.IO) {
                runCatching {
                    infoDir.mkdirs()
                    infoDir.listFiles()?.forEach {
                        if (System.currentTimeMillis() - it.lastModified() > INFO_MAX_AGE_MS) it.delete()
                    }
                    File(infoDir, "$id.json").writeText(line)
                }.onFailure { AppLog.w("ENGINE", "info cache write failed", it) }
            }
        }
        return line
    }

    private fun cachedInfo(videoId: String): String? {
        val f = File(infoDir, "$videoId.json")
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > INFO_MAX_AGE_MS) return null
        return runCatching { f.readText() }.getOrNull()
    }

    // --------------------------------------------------------- search/playlist

    override suspend fun search(query: String): List<VideoEntry> {
        val req = request(listOf("ytsearch$SEARCH_LIMIT:${query.trim()}")).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            preferredLang()?.let { addOption("--extractor-args", "youtube:lang=$it") }
        }
        val json = singleJson(runCancellable(req))
        return withContext(Dispatchers.Default) { YtJson.entries(json) }
    }

    override suspend fun fetchPlaylist(url: String): PlaylistInfo {
        val req = request(listOf(url)).apply {
            addOption("--flat-playlist")
            addOption("--yes-playlist")
            addOption("--dump-single-json")
            // One extra entry tells us whether the list was cut off.
            addOption("--playlist-items", "1:${PLAYLIST_LIMIT + 1}")
            preferredLang()?.let { addOption("--extractor-args", "youtube:lang=$it") }
        }
        val json = singleJson(runCancellable(req))
        return withContext(Dispatchers.Default) {
            val entries = YtJson.entries(json)
            PlaylistInfo(
                title = YtJson.title(json) ?: "Playlist",
                entries = entries.take(PLAYLIST_LIMIT),
                truncated = entries.size > PLAYLIST_LIMIT,
            )
        }
    }

    private fun singleJson(res: YoutubeDLResponse): JSONObject {
        val line = YtJson.firstJsonLine(res.out)
            ?: throw IllegalStateException("Failed to fetch video information")
        return JSONObject(line)
    }

    /** Device language when YouTube supports it, so titles aren't auto-translated to English. */
    private fun preferredLang(): String? {
        val loc = Locale.getDefault()
        val lang = when (loc.language) {
            "in" -> "id"
            "he" -> "iw"
            else -> loc.language
        }
        val candidates = listOf("$lang-${loc.country}", lang)
        return candidates.firstOrNull { it in SUPPORTED_LANGS }?.takeIf { it != "en" }
    }

    // --------------------------------------------------------------- download

    override suspend fun downloadAudio(
        url: String,
        processId: String,
        workDir: File,
        options: DownloadOptions,
        onProgress: (Int?, String) -> Unit,
        isCancelled: () -> Boolean,
    ): File {
        ensureInitIo()
        val clean = UrlValidator.extractFirstSupportedUrl(url) ?: url.trim()
        val videoId = UrlValidator.videoId(clean)
        var info = videoId?.let { withContext(Dispatchers.IO) { cachedInfo(it) } }
        val fromCache = info != null
        if (info == null) {
            onProgress(null, "Fetching info…")
            info = dumpInfo(clean)
        }
        if (isCancelled()) throw CancellationException()
        return try {
            runDownload(info, processId, workDir, options, onProgress, isCancelled)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (isCancelled()) throw CancellationException()
            // Stream URLs in cached info expire; refresh once instead of failing.
            if (fromCache && looksExpired(t)) {
                AppLog.w("ENGINE", "cached info rejected, refreshing", t)
                videoId?.let { File(infoDir, "$it.json").delete() }
                onProgress(null, "Fetching info…")
                val fresh = dumpInfo(clean)
                runDownload(fresh, processId, workDir, options, onProgress, isCancelled)
            } else {
                AppLog.e("ENGINE", "download failed url=$clean", t)
                throw t
            }
        }
    }

    private suspend fun runDownload(
        infoJson: String,
        processId: String,
        workDir: File,
        options: DownloadOptions,
        onProgress: (Int?, String) -> Unit,
        isCancelled: () -> Boolean,
    ): File = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        workDir.listFiles()?.forEach { it.delete() }
        val infoFile = File(workDir, "info.json").apply {
            writeText(YtJson.withEditedTags(infoJson, options.title, options.artist))
        }
        val format = options.format
        val req = request(emptyList()).apply {
            addOption("--load-info-json", infoFile.absolutePath)
            addOption("-f", sourceFormat(format, trimmed = sectionArg(options) != null))
            addOption("--fragment-retries", "3")
            addOption("-x")
            addOption("--audio-format", format.ext)
            if (format == AudioFormat.MP3) {
                addOption("--audio-quality", "${options.quality.bitrateKbps}K")
            } else {
                // MP3 gets ID3 tags + cover from Id3Writer; M4A is tagged by yt-dlp/ffmpeg.
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
            }
            sectionArg(options)?.let { addOption("--download-sections", it) }
            addOption("--no-mtime")
            addOption("-o", File(workDir, "audio.%(ext)s").absolutePath)
        }
        val result = try {
            EngineLock.rw.read {
                YoutubeDL.getInstance().execute(req, processId) { progress, _, _ ->
                    if (isCancelled()) {
                        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
                        return@execute
                    }
                    val pct = if (progress >= 0) progress.toInt().coerceIn(0, 100) else null
                    onProgress(pct, if (pct != null) "Downloading $pct%" else "Downloading…")
                }
            }
            null
        } catch (e: YoutubeDL.CanceledException) {
            throw CancellationException()
        } catch (t: Throwable) {
            if (isCancelled()) throw CancellationException()
            t
        }
        val output = workDir.listFiles()?.firstOrNull {
            it.extension.equals(format.ext, true) && it.length() > 0 && it.nameWithoutExtension == "audio"
        }
        if (result != null) {
            // Cover-art embedding is cosmetic: keep the audio if only that step failed.
            val errorLines = result.message.orEmpty().lines().filter { it.trim().startsWith("ERROR") }
            val coverOnly = errorLines.isNotEmpty() && errorLines.all { it.contains("thumbnail", ignoreCase = true) }
            if (!(coverOnly && output != null)) throw result
            AppLog.w("ENGINE", "cover embed failed, keeping audio", result)
        }
        output ?: throw IllegalStateException(DownloadErrorKind.CONVERSION_ERROR.name)
    }

    /**
     * Trims always cut from the M4A stream: ffmpeg's stream-copy seek in WebM/Opus
     * snaps to ~10 s clusters (verified: "30-inf" started at ~20 s), while AAC in
     * MP4 cuts within ~0.04 s. MP3 is re-encoded anyway, so quality is unaffected.
     */
    private fun sourceFormat(format: AudioFormat, trimmed: Boolean): String =
        if (format == AudioFormat.M4A || trimmed) "bestaudio[ext=m4a]/bestaudio/best" else "bestaudio/best"

    private fun sectionArg(o: DownloadOptions): String? {
        if (o.sectionStart == null && o.sectionEnd == null) return null
        val start = (o.sectionStart ?: 0).coerceAtLeast(0)
        val end = o.sectionEnd?.toString() ?: "inf"
        return "*$start-$end"
    }

    private fun looksExpired(t: Throwable): Boolean {
        val m = (t.message ?: "").lowercase()
        return "http error 403" in m || "http error 410" in m || "http error 404" in m ||
            "forbidden" in m || "expired" in m
    }

    companion object {
        private const val INFO_MAX_AGE_MS = 2 * 60 * 60 * 1000L
        private const val SEARCH_LIMIT = 15
        const val PLAYLIST_LIMIT = 100

        /** yt-dlp's YouTube _SUPPORTED_LANG_CODES; any other value makes extraction fail. */
        private val SUPPORTED_LANGS = setOf(
            "af", "az", "id", "ms", "bs", "ca", "cs", "da", "de", "et", "en-IN", "en-GB", "en", "es",
            "es-419", "es-US", "eu", "fil", "fr", "fr-CA", "gl", "hr", "zu", "is", "it", "sw", "lv",
            "lt", "hu", "nl", "no", "uz", "pl", "pt-PT", "pt", "ro", "sq", "sk", "sl", "sr-Latn", "fi",
            "sv", "vi", "tr", "be", "bg", "ky", "kk", "mk", "mn", "ru", "sr", "uk", "el", "hy", "iw",
            "ur", "ar", "fa", "ne", "mr", "hi", "as", "bn", "pa", "gu", "or", "ta", "te", "kn", "ml",
            "si", "th", "lo", "my", "ka", "am", "km", "zh-CN", "zh-TW", "zh-HK", "ja", "ko",
        )
    }
}

