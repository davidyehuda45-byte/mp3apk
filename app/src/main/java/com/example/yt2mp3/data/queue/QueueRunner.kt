package com.example.yt2mp3.data.queue

import com.example.yt2mp3.data.engine.DownloadOptions
import com.example.yt2mp3.data.engine.YtDlpEngine
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.data.storage.Id3Writer
import com.example.yt2mp3.data.storage.StorageManager
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.DownloadProgress
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.domain.model.errorDetail
import com.example.yt2mp3.domain.model.toDownloadErrorKind
import com.example.yt2mp3.domain.model.userMessage
import com.example.yt2mp3.domain.repository.DownloadRepository
import com.example.yt2mp3.domain.repository.SettingsRepository
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

/**
 * Sequential queue: one active item at a time (PRD §15).
 * Failed items never block the rest. Survives Activity recreation
 * (lives in Application scope + Room persistence).
 */
class QueueRunner(
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val engine: YtDlpEngine,
    private val storage: StorageManager,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private var worker: Job? = null
    private val cancelledIds = mutableSetOf<Long>()
    private val lock = Any()

    private val _events = MutableSharedFlow<DownloadProgress>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    val currentItemId = AtomicLong(-1L)

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
        while (true) {
            val next = downloads.nextQueued() ?: break
            // Skip cancelled-before-start items
            if (isCancelled(next.id)) {
                downloads.updateStatus(next.id, DownloadStatus.CANCELLED)
                synchronized(lock) { cancelledIds.remove(next.id) }
                continue
            }
            runItem(next.id)
        }
    }

    private suspend fun runItem(id: Long) {
        currentItemId.set(id)
        var tempDir: File? = null
        try {
            val item = downloads.get(id) ?: return
            if (!DownloadStatus.canTransition(item.status, DownloadStatus.DOWNLOADING)) return
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
            val audio = engine.downloadAudio(
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
            if (isCancelled(id)) throw CancellationException()

            downloads.updateStatus(id, DownloadStatus.CONVERTING)
            emit(id, DownloadStatus.CONVERTING, 100, "Converting")
            downloads.updateStatus(id, DownloadStatus.CONVERTING) // persist stage
            if (item.format == AudioFormat.MP3) {
                Id3Writer.writeMp3Tags(audio, meta)
                emit(id, DownloadStatus.SAVING, 100, "Writing metadata")
            }

            downloads.updateStatus(id, DownloadStatus.SAVING)
            emit(id, DownloadStatus.SAVING, 100, "Saving")
            val uniqueName = storage.createUniqueFileName(item.outputFileName)
            val uri = storage.saveToDestination(audio, uniqueName, item.format.mime)
            if (uri == null) {
                // Distinguish disk-full when possible
                fail(id, DownloadErrorKind.STORAGE_ERROR, null)
                return
            }
            downloads.setOutputUri(id, uri.toString())
            downloads.updateStatus(id, DownloadStatus.COMPLETED)
            emit(id, DownloadStatus.COMPLETED, 100, "Completed")
        } catch (ce: CancellationException) {
            downloads.updateStatus(id, DownloadStatus.CANCELLED)
            emit(id, DownloadStatus.CANCELLED, null, "Cancelled")
            AppLog.i("QUEUE", "item $id cancelled")
        } catch (t: Throwable) {
            fail(id, t.toDownloadErrorKind(), t)
        } finally {
            storage.cleanup(tempDir)
            synchronized(lock) { cancelledIds.remove(id) }
            currentItemId.set(-1L)
        }
    }

    private suspend fun fail(id: Long, kind: DownloadErrorKind, t: Throwable?) {
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
