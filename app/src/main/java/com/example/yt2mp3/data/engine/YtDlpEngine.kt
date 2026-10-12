package com.example.yt2mp3.data.engine

import android.content.Context
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.data.util.ArgSplitter
import com.example.yt2mp3.data.util.UrlValidator
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.PlaylistInfo
import com.example.yt2mp3.domain.model.PostOptions
import com.example.yt2mp3.domain.model.SubtitleMode
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoFormat
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.domain.model.VideoQuality
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

/** How one queued audio item should be produced. Section bounds are in seconds. */
data class DownloadOptions(
    val format: AudioFormat,
    val quality: AudioQuality,
    val title: String,
    val artist: String?,
    val sectionStart: Int? = null,
    val sectionEnd: Int? = null,
    val post: PostOptions = PostOptions(),
)

/** How one queued video item should be produced. */
data class VideoDownloadOptions(
    val format: VideoFormat,
    val quality: VideoQuality,
    val title: String,
    val artist: String?,
    val sectionStart: Int? = null,
    val sectionEnd: Int? = null,
    val post: PostOptions = PostOptions(),
)

/** Result of a video download: merged file + sidecar files (e.g. separate subtitles). */
data class EngineOutput(val main: File, val extras: List<File> = emptyList())

/**
 * Runtime tuning snapshot (retries, rate limit, proxy, UA, custom args).
 * Updated by AppContainer from Settings; engine never stores credentials.
 */
data class EngineTuning(
    val retries: Int = 3,
    val fragmentRetries: Int = 3,
    /** KiB/s, 0 = unlimited. yt-dlp --limit-rate takes bytes, we pass "N K". */
    val rateLimitKbps: Int = 0,
    val proxyUrl: String = "",
    val userAgent: String = "",
    val customArgs: List<String> = emptyList(),
    val customFfmpegArgs: String = "",
    val customFormat: String = "",
    val debug: Boolean = false,
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

    suspend fun downloadVideo(
        url: String,
        processId: String,
        workDir: File,
        options: VideoDownloadOptions,
        onProgress: (percent: Int?, stage: String) -> Unit,
        isCancelled: () -> Boolean,
    ): EngineOutput
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

    /** Updated from Settings by AppContainer; safe defaults when unset. */
    @Volatile var tuning: EngineTuning = EngineTuning()

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
        val t = tuning
        addOption("--cache-dir", ytCacheDir.absolutePath)
        addOption("--socket-timeout", "20")
        addOption("--retries", t.retries.coerceIn(0, 10).toString())
        addOption("--extractor-retries", t.retries.coerceIn(0, 10).toString())
        addOption("--fragment-retries", t.fragmentRetries.coerceIn(0, 10).toString())
        if (t.rateLimitKbps > 0) addOption("--limit-rate", "${t.rateLimitKbps}K")
        if (t.proxyUrl.isNotBlank() && isSafeProxy(t.proxyUrl)) addOption("--proxy", t.proxyUrl.trim())
        if (t.userAgent.isNotBlank()) addOption("--user-agent", t.userAgent.trim())
        if (t.debug) addOption("--verbose")
        applyCustomArgs(this, t.customArgs)
    }

    private fun applyCustomArgs(req: YoutubeDLRequest, args: List<String>) {
        val clean = ArgSplitter.sanitized(args).filterNot { "://" in it }
        var i = 0
        while (i < clean.size) {
            val a = clean[i]
            if (a.startsWith("--") && i + 1 < clean.size && !clean[i + 1].startsWith("-") && "=" !in a) {
                req.addOption(a, clean[i + 1])
                i += 2
            } else if (a.startsWith("-") && "=" !in a) {
                req.addOption(a)
                i += 1
            } else {
                // Positional or --opt=val tokens are ignored (no URL/option smuggling).
                if (a.startsWith("--") && "=" in a) req.addOption(a)
                i += 1
            }
        }
    }

    /** Only http(s)/socks proxies; never credentials in URL. */
    private fun isSafeProxy(proxy: String): Boolean {
        val p = proxy.trim()
        if ("@" in p) return false
        return p.startsWith("http://") || p.startsWith("https://") ||
            p.startsWith("socks4://") || p.startsWith("socks5://")
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
            // Single item only: playlist/channel/album URLs are expanded via fetchPlaylist().
            addOption("--no-playlist")
            addOption("-f", "bestaudio/best")
            addOption("--dump-json")
            // No cookies/credentials: private/login-only content must fail as
            // CONTENT_UNAVAILABLE, never prompt for bypass (PRD §1.2).
            addOption("--no-cookies")
        }
        val line = YtJson.firstJsonLine(runCancellable(req).out)
            ?: throw IllegalStateException("Failed to fetch video information")
        val key = UrlValidator.cacheKeyFor(cleanUrl)
        withContext(Dispatchers.IO) {
            runCatching {
                infoDir.mkdirs()
                infoDir.listFiles()?.forEach {
                    if (System.currentTimeMillis() - it.lastModified() > INFO_MAX_AGE_MS) it.delete()
                }
                File(infoDir, "$key.json").writeText(line)
            }.onFailure { AppLog.w("ENGINE", "info cache write failed", it) }
        }
        return line
    }

    private fun cachedInfo(key: String): String? {
        val f = File(infoDir, "$key.json")
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
        // Accept YouTube playlist pages and generic playlist/channel/album/user URLs.
        val clean = UrlValidator.extractPlaylistUrl(url)
            ?: UrlValidator.extractFirstSupportedUrl(url)
            ?: throw IllegalArgumentException(DownloadErrorKind.INVALID_URL.name)
        val req = request(listOf(clean)).apply {
            addOption("--flat-playlist")
            addOption("--yes-playlist")
            addOption("--dump-single-json")
            addOption("--no-cookies")
            // One extra entry tells us whether the list was cut off.
            addOption("--playlist-items", "1:${PLAYLIST_LIMIT + 1}")
            if (UrlValidator.isYoutubeUrl(clean)) {
                preferredLang()?.let { addOption("--extractor-args", "youtube:lang=$it") }
            }
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
        val key = UrlValidator.cacheKeyFor(clean)
        var info = withContext(Dispatchers.IO) { cachedInfo(key) }
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
                File(infoDir, "$key.json").delete()
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
        val post = options.post
        val trimmed = options.sectionStart != null || options.sectionEnd != null
        val req = request(emptyList()).apply {
            addOption("--load-info-json", infoFile.absolutePath)
            addOption("-f", audioSourceFormat(format, trimmed))
            addOption("--no-cookies")
            addOption("-x")
            addOption("--audio-format", format.ytdlpFormat)
            if (format.usesQuality) {
                addOption("--audio-quality", "${options.quality.bitrateKbps}K")
            }
            if (!post.stripMetadata) {
                if (format != AudioFormat.MP3) {
                    // MP3 gets ID3 tags + cover from Id3Writer; other formats are
                    // tagged by yt-dlp/ffmpeg.
                    addOption("--embed-metadata")
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")
                }
                if (post.embedChapters) addOption("--embed-chapters")
            }
            if (post.normalizeAudio) {
                addOption("--postprocessor-args", "ffmpeg:-af loudnorm=I=-16:TP=-1.5:LRA=11")
            }
            tuning.customFfmpegArgs.trim().takeIf { it.isNotEmpty() }?.let {
                addOption("--postprocessor-args", "ffmpeg:$it")
            }
            sectionArg(options.sectionStart, options.sectionEnd)?.let { addOption("--download-sections", it) }
            addOption("--no-mtime")
            addOption("-o", File(workDir, "audio.%(ext)s").absolutePath)
        }
        val result = executeReq(req, processId, onProgress, isCancelled)
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

    override suspend fun downloadVideo(
        url: String,
        processId: String,
        workDir: File,
        options: VideoDownloadOptions,
        onProgress: (Int?, String) -> Unit,
        isCancelled: () -> Boolean,
    ): EngineOutput {
        ensureInitIo()
        val clean = UrlValidator.extractFirstSupportedUrl(url) ?: url.trim()
        val key = UrlValidator.cacheKeyFor(clean)
        var info = withContext(Dispatchers.IO) { cachedInfo(key) }
        val fromCache = info != null
        if (info == null) {
            onProgress(null, "Fetching info…")
            info = dumpInfo(clean)
        }
        if (isCancelled()) throw CancellationException()
        return try {
            runVideoDownload(info, processId, workDir, options, onProgress, isCancelled)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (isCancelled()) throw CancellationException()
            if (fromCache && looksExpired(t)) {
                AppLog.w("ENGINE", "cached info rejected, refreshing", t)
                File(infoDir, "$key.json").delete()
                onProgress(null, "Fetching info…")
                runVideoDownload(dumpInfo(clean), processId, workDir, options, onProgress, isCancelled)
            } else {
                AppLog.e("ENGINE", "video download failed url=$clean", t)
                throw t
            }
        }
    }

    private suspend fun runVideoDownload(
        infoJson: String,
        processId: String,
        workDir: File,
        options: VideoDownloadOptions,
        onProgress: (Int?, String) -> Unit,
        isCancelled: () -> Boolean,
    ): EngineOutput = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        workDir.listFiles()?.forEach { it.delete() }
        val infoFile = File(workDir, "info.json").apply {
            writeText(YtJson.withEditedTags(infoJson, options.title, options.artist))
        }
        val post = options.post
        val req = request(emptyList()).apply {
            addOption("--load-info-json", infoFile.absolutePath)
            addOption("-f", videoSourceFormat(options.quality))
            addOption("--no-cookies")
            addOption("--merge-output-format", options.format.ext)
            if (!post.stripMetadata) {
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                if (post.embedChapters) addOption("--embed-chapters")
            }
            if (post.sponsorBlock) addOption("--sponsorblock-remove", "sponsor")
            when (post.subtitleMode) {
                SubtitleMode.EMBED -> {
                    addOption("--write-subs")
                    addOption("--embed-subs")
                    addOption("--sub-langs", cleanLangs(post.subLangs))
                    addOption("--sub-format", "vtt/best")
                }
                SubtitleMode.SEPARATE -> {
                    addOption("--write-subs")
                    addOption("--no-embed-subs")
                    addOption("--sub-langs", cleanLangs(post.subLangs))
                    addOption("--sub-format", "srt/vtt/best")
                }
                SubtitleMode.NONE -> Unit
            }
            if (post.normalizeAudio) {
                addOption("--postprocessor-args", "ffmpeg:-af loudnorm=I=-16:TP=-1.5:LRA=11")
            }
            tuning.customFfmpegArgs.trim().takeIf { it.isNotEmpty() }?.let {
                addOption("--postprocessor-args", "ffmpeg:$it")
            }
            sectionArg(options.sectionStart, options.sectionEnd)?.let { addOption("--download-sections", it) }
            addOption("--no-mtime")
            addOption("-o", File(workDir, "video.%(ext)s").absolutePath)
        }
        val result = executeReq(req, processId, onProgress, isCancelled)
        val main = workDir.listFiles()?.firstOrNull {
            it.extension.equals(options.format.ext, true) && it.length() > 0 && it.nameWithoutExtension == "video"
        }
        if (result != null && main == null) throw result
        if (result != null) AppLog.w("ENGINE", "video post step warning", result)
        val output = main ?: throw IllegalStateException(DownloadErrorKind.CONVERSION_ERROR.name)
        val extras = workDir.listFiles()?.filter {
            it.length() > 0 && it.nameWithoutExtension.startsWith("video.") && it != output &&
                it.extension.lowercase() in setOf("srt", "vtt")
        } ?: emptyList()
        EngineOutput(output, extras)
    }

    private suspend fun executeReq(
        req: YoutubeDLRequest,
        processId: String,
        onProgress: (Int?, String) -> Unit,
        isCancelled: () -> Boolean,
    ): Throwable? {
        return try {
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
    }

    /**
     * Trims always cut from the M4A stream: ffmpeg's stream-copy seek in WebM/Opus
     * snaps to ~10 s clusters, while AAC in MP4 cuts within ~0.04 s.
     */
    private fun audioSourceFormat(format: AudioFormat, trimmed: Boolean): String {
        val custom = tuning.customFormat.trim()
        if (custom.isNotEmpty()) return custom
        return if (format == AudioFormat.M4A || trimmed) "bestaudio[ext=m4a]/bestaudio/best" else "bestaudio/best"
    }

    private fun videoSourceFormat(quality: VideoQuality): String {
        val custom = tuning.customFormat.trim()
        if (custom.isNotEmpty()) return custom
        val h = quality.maxHeight
        return if (h == null) "bestvideo+bestaudio/best"
        else "bestvideo[height<=$h]+bestaudio/best[height<=$h]/best[height<=$h]/best"
    }

    private fun cleanLangs(langs: String): String {
        val parts = langs.split(',').map { it.trim().lowercase() }.filter { it.matches(Regex("[a-z-]{2,12}")) }
        return parts.take(4).joinToString(",").ifEmpty { "en" }
    }

    private fun sectionArg(start: Int?, end: Int?): String? {
        if (start == null && end == null) return null
        val s = (start ?: 0).coerceAtLeast(0)
        val e = end?.toString() ?: "inf"
        return "*$s-$e"
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

