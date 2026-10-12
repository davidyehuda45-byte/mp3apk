package com.example.yt2mp3.data.queue

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.yt2mp3.data.engine.DownloadOptions
import com.example.yt2mp3.data.engine.VideoDownloadOptions
import com.example.yt2mp3.data.engine.YtDlpEngine
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.data.storage.Id3Writer
import com.example.yt2mp3.data.storage.StorageManager
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.DownloadProgress
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.domain.model.errorDetail
import com.example.yt2mp3.domain.model.toDownloadErrorKind
import com.example.yt2mp3.domain.model.userMessage
import com.example.yt2mp3.domain.repository.DownloadRepository
import com.example.yt2mp3.domain.repository.SettingsRepository
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Parallel queue: up to N active items (Settings.maxParallel, default 1).
 * Failed items never block the rest. Survives Activity recreation
 * (lives in Application scope + Room persistence).
 */
class QueueRunner(
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val engine: YtDlpEngine,
    private val storage: StorageManager,
    private val appContext: Context,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val claim = Mutex()
    private var worker: Job? = null
    private val cancelledIds = mutableSetOf<Long>()
    private val lock = Any()
    private val attempts = mutableMapOf<Long, Int>()
    private val activeCount = AtomicInteger(0)

    private val _events = MutableSharedFlow<DownloadProgress>(extraBufferCapacity = 128)
    val events = _events.asSharedFlow()

    fun isCancelled(id: Long): Boolean = synchronized(lock) { id in cancelledIds }
    fun cancel(id: Long, processId: String? = null) {
        synchronized(lock) { cancelledIds.add(id) }
        if (processId != null) runCatching { engine.cancel(processId) }
    }

    /** Idempotent kick: starts worker if idle and a QUEUED item exists. */
    fun kick() {
        if (worker?.isActive == true) return
        worker = externalScope.launch { pump() }
    }

    private suspend fun pump() {
        refreshTuning()
        // Wi-Fi only: wait (cancellable) instead of burning mobile data.
        while (runCatching { settings.getWifiOnly() }.getOrDefault(false) && !isWifiConnected()) {
            AppLog.i("QUEUE", "waiting for Wi-Fi")
            runCatching {
                downloads.nextQueued()?.let {
                    downloads.updateProgress(it.id, null, "Menunggu Wi-Fi…")
                    emit(it.id, DownloadStatus.QUEUED, null, "Menunggu Wi-Fi…")
                }
            }
            delay(60_000)
        }
        val parallel = runCatching { settings.getMaxParallel() }.getOrDefault(1).coerceIn(1, 3)
        coroutineScope {
            (0 until parallel).map {
                async { workerLoop() }
            }.awaitAll()
        }
    }

    private suspend fun workerLoop() {
        while (true) {
            val next = claim.withLock {
                val n = downloads.nextQueued() ?: return@withLock null
                if (isCancelled(n.id)) {
                    downloads.updateStatus(n.id, DownloadStatus.CANCELLED)
                    synchronized(lock) { cancelledIds.remove(n.id) }
                    return@withLock Long.MIN_VALUE // claimed as handled; look for more
                }
                if (!DownloadStatus.canTransition(n.status, DownloadStatus.DOWNLOADING)) {
                    return@withLock Long.MIN_VALUE
                }
                downloads.updateStatus(n.id, DownloadStatus.DOWNLOADING)
                n.id
            } ?: break
            if (next == Long.MIN_VALUE) continue
            runItem(next)
        }
    }

    private suspend fun refreshTuning() {
        val impl = engine as? com.example.yt2mp3.data.engine.YtDlpEngineImpl ?: return
        runCatching {
            impl.tuning = com.example.yt2mp3.data.engine.EngineTuning(
                retries = settings.getMaxRetries().coerceIn(0, 10),
                fragmentRetries = settings.getFragmentRetries().coerceIn(0, 10),
                rateLimitKbps = settings.getRateLimitKbps().coerceAtLeast(0),
                proxyUrl = settings.getProxyUrl().trim(),
                userAgent = settings.getUserAgent(),
                customArgs = com.example.yt2mp3.data.util.ArgSplitter.split(settings.getCustomYtdlpArgs()).take(40),
                customFfmpegArgs = settings.getCustomFfmpegArgs().trim(),
                customFormat = settings.getCustomFormat().trim(),
                debug = settings.getDebugMode(),
            )
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (t: Throwable) {
            AppLog.w("QUEUE", "connectivity check failed", t)
            true // fail open: don't stall the queue when the check itself breaks
        }
    }

    private suspend fun runItem(id: Long) {
        activeCount.incrementAndGet()
        var tempDir: File? = null
        try {
            val item = downloads.get(id) ?: return
            downloads.updateStatus(id, DownloadStatus.DOWNLOADING)
            emit(id, DownloadStatus.DOWNLOADING, null, "Preparing")

            if (!storage.isDestinationValid()) {
                fail(id, DownloadErrorKind.STORAGE_ERROR, null)
                return
            }
            tempDir = storage.newTempDir("dl_$id")
            val meta = VideoMetadata(item.sourceUrl, item.title, item.artist, null, item.thumbnailUrl)
            val processId = "dl_$id"

            downloads.updateProgress(id, null, "Downloading…")
            val post = item.post
            val mainFile: File
            var extraFiles: List<File> = emptyList()
            if (item.mediaKind == MediaKind.VIDEO) {
                val out = engine.downloadVideo(
                    url = item.sourceUrl,
                    processId = processId,
                    workDir = tempDir,
                    options = VideoDownloadOptions(
                        format = item.videoFormat,
                        quality = item.videoQuality,
                        title = item.title,
                        artist = item.artist,
                        sectionStart = item.sectionStart,
                        sectionEnd = item.sectionEnd,
                        post = post,
                    ),
                    onProgress = { pct, stage ->
                        runBlocking {
                            downloads.updateProgress(id, pct, stage)
                            emit(id, DownloadStatus.DOWNLOADING, pct, stage)
                        }
                    },
                    isCancelled = { isCancelled(id) },
                )
                mainFile = out.main
                extraFiles = out.extras
            } else {
                mainFile = engine.downloadAudio(
                    url = item.sourceUrl,
                    processId = processId,
                    workDir = tempDir,
                    options = DownloadOptions(
                        format = item.format,
                        quality = item.quality,
                        title = item.title,
                        artist = item.artist,
                        sectionStart = item.sectionStart,
                        sectionEnd = item.sectionEnd,
                        post = post,
                    ),
                    // Called on yt-dlp's stdout reader thread; blocking keeps events ordered so a late
                    // "Downloading" can never arrive after "Completed".
                    onProgress = { pct, stage ->
                        runBlocking {
                            downloads.updateProgress(id, pct, stage)
                            emit(id, DownloadStatus.DOWNLOADING, pct, stage)
                        }
                    },
                    isCancelled = { isCancelled(id) },
                )
            }
            if (isCancelled(id)) throw CancellationException()

            downloads.updateStatus(id, DownloadStatus.CONVERTING)
            emit(id, DownloadStatus.CONVERTING, 100, "Converting")
            downloads.updateStatus(id, DownloadStatus.CONVERTING) // persist stage
            if (item.mediaKind == MediaKind.AUDIO && item.format == AudioFormat.MP3 && !post.stripMetadata) {
                // MP3 tags + cover via Id3Writer (other formats are tagged by yt-dlp/ffmpeg).
                Id3Writer.writeMp3Tags(mainFile, meta)
                emit(id, DownloadStatus.SAVING, 100, "Writing metadata")
            }

            downloads.updateStatus(id, DownloadStatus.SAVING)
            emit(id, DownloadStatus.SAVING, 100, "Saving")
            val mime = if (item.mediaKind == MediaKind.VIDEO) item.videoFormat.mime else item.format.mime
            val uniqueName = storage.createUniqueFileNameIn(item.subfolder, item.outputFileName)
            val uri = storage.saveToSubfolder(mainFile, item.subfolder, uniqueName, mime)
            if (uri == null) {
                // Distinguish disk-full when possible
                fail(id, DownloadErrorKind.STORAGE_ERROR, null)
                return
            }
            // Sidecar files (separate subtitles, separate thumbnail).
            for (extra in extraFiles) {
                runCatching {
                    val extraName = uniqueName.substringBeforeLast('.') + "." + extra.extension
                    storage.saveToSubfolder(extra, item.subfolder, extraName, "text/plain")
                }
            }
            if (post.saveThumbSeparate) {
                runCatching { saveThumbSeparate(item, uniqueName) }
            }
            downloads.setOutputUri(id, uri.toString())
            downloads.updateStatus(id, DownloadStatus.COMPLETED)
            emit(id, DownloadStatus.COMPLETED, 100, "Completed")
            synchronized(lock) { attempts.remove(id) }
            // Incognito: keep the file, drop the history row.
            if (!runCatching { settings.getSaveHistory() }.getOrDefault(true)) {
                downloads.deleteEntry(id)
            }
        } catch (ce: CancellationException) {
            downloads.updateStatus(id, DownloadStatus.CANCELLED)
            emit(id, DownloadStatus.CANCELLED, null, "Cancelled")
            AppLog.i("QUEUE", "item $id cancelled")
        } catch (t: Throwable) {
            fail(id, t.toDownloadErrorKind(), t)
        } finally {
            storage.cleanup(tempDir)
            synchronized(lock) { cancelledIds.remove(id) }
            activeCount.decrementAndGet()
        }
    }

    private suspend fun saveThumbSeparate(item: com.example.yt2mp3.domain.model.DownloadItem, uniqueName: String) {
        val thumb = item.thumbnailUrl ?: return
        val bytes = try {
            val conn = java.net.URL(thumb).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            conn.getInputStream().use { it.readBytes() }
        } catch (t: Throwable) {
            AppLog.w("QUEUE", "thumb fetch skipped", t)
            return
        }
        if (bytes.isEmpty() || bytes.size > 5_000_000) return
        val base = uniqueName.substringBeforeLast('.')
        storage.saveBytesToDestination(item.subfolder, "$base.jpg", "image/jpeg", bytes)
    }

    private suspend fun fail(id: Long, kind: DownloadErrorKind, t: Throwable?) {
        // Auto-retry transient failures with backoff.
        val maxRetries = runCatching { settings.getMaxRetries() }.getOrDefault(2)
        val retryable = kind == DownloadErrorKind.NETWORK_ERROR ||
            kind == DownloadErrorKind.EXTRACTOR_ERROR ||
            kind == DownloadErrorKind.CONVERSION_ERROR ||
            kind == DownloadErrorKind.UNKNOWN
        val n = synchronized(lock) { (attempts[id] ?: 0) + 1.also { attempts[id] = it } }
        if (retryable && n <= maxRetries) {
            val waitMs = (n * n * 10_000L).coerceAtMost(120_000L)
            AppLog.i("QUEUE", "item $id retry $n/$maxRetries in ${waitMs}ms ($kind)")
            downloads.updateProgress(id, null, "Retry $n/$maxRetries…")
            delay(waitMs)
            if (!isCancelled(id)) {
                downloads.updateStatus(id, DownloadStatus.QUEUED)
                downloads.updateProgress(id, null, "Waiting")
                emit(id, DownloadStatus.QUEUED, null, "Waiting")
                kick()
                return
            }
        }
        synchronized(lock) { attempts.remove(id) }
        val msg = com.example.yt2mp3.domain.model.AppError(kind, "").userMessage()
        val detail = t?.takeIf { it.message?.trim() != kind.name }?.errorDetail()
        downloads.updateStatus(id, DownloadStatus.FAILED, if (detail != null) "$msg\nDetail: $detail" else msg)
        emit(id, DownloadStatus.FAILED, null, msg)
        AppLog.w("QUEUE", "item $id failed: $kind", t)
    }

    private suspend fun emit(id: Long, s: DownloadStatus, pct: Int?, stage: String) {
        _events.emit(DownloadProgress(id, s, pct, stage))
    }

    suspend fun stop() { worker?.cancelAndJoin() }

    suspend fun nextPending() = downloads.nextQueued()
}
