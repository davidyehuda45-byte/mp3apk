package com.example.yt2mp3.domain.usecase

import com.example.yt2mp3.data.util.FileNamePattern
import com.example.yt2mp3.data.util.SupportedSites
import com.example.yt2mp3.data.util.UrlValidator
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.DownloadRequest
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.repository.DownloadRepository
import com.example.yt2mp3.domain.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StartDownloadUseCase(
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val onQueueChanged: () -> Unit,
) {
    suspend fun execute(request: DownloadRequest): Result<Long> =
        executeAll(listOf(request)).map { it.first() }

    suspend fun executeAll(requests: List<DownloadRequest>): Result<List<Long>> {
        if (requests.isEmpty()) return Result.success(emptyList())
        settings.getDestinationUri()
            ?: return Result.failure(IllegalStateException(DownloadErrorKind.STORAGE_ERROR.name))
        var list = requests
        // Skip yang sudah ada di antrean/riwayat (arsip sederhana per sourceUrl).
        if (runCatching { settings.getSkipExisting() }.getOrDefault(false)) {
            val known = downloads.existingUrls(list.map { it.sourceUrl })
            list = list.filterNot { it.sourceUrl in known }
            if (list.isEmpty()) return Result.failure(IllegalStateException("SKIP_EXISTING"))
        }
        val pattern = runCatching { settings.getRenamePattern() }.getOrDefault(FileNamePattern.DEFAULT)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        list = list.map { r ->
            if (r.outputBaseName != null) r else r.copy(
                outputBaseName = FileNamePattern.apply(
                    pattern = pattern,
                    title = r.title,
                    artist = r.artist,
                    id = UrlValidator.videoId(r.sourceUrl),
                    site = SupportedSites.siteNameForUrl(r.sourceUrl),
                    date = date,
                    fallbackBase = r.title,
                ),
            )
        }
        val ids = downloads.enqueueAll(list)
        onQueueChanged()
        return Result.success(ids)
    }
}

class CancelDownloadUseCase(
    private val downloads: DownloadRepository,
    private val cancelRunner: (id: Long, processId: String) -> Unit,
) {
    suspend fun execute(id: Long) {
        val item = downloads.get(id) ?: return
        if (item.status == DownloadStatus.QUEUED) {
            downloads.updateStatus(id, DownloadStatus.CANCELLED)
        } else if (item.status.isActive || item.status == DownloadStatus.DOWNLOADING) {
            cancelRunner(id, "dl_$id")
        }
    }
}

class RetryDownloadUseCase(
    private val downloads: DownloadRepository,
    private val onQueueChanged: () -> Unit,
) {
    /** Re-queues a FAILED/CANCELLED entry (PRD §48). Returns false if not retryable. */
    suspend fun execute(id: Long): Boolean {
        val item = downloads.get(id) ?: return false
        if (item.status != DownloadStatus.FAILED && item.status != DownloadStatus.CANCELLED) return false
        if (!DownloadStatus.canTransition(item.status, DownloadStatus.QUEUED)) return false
        downloads.updateStatus(id, DownloadStatus.QUEUED)
        downloads.updateProgress(id, null, "Waiting")
        onQueueChanged()
        return true
    }

    suspend fun retryAll(): Int {
        val n = downloads.retryAllFailed()
        if (n > 0) onQueueChanged()
        return n
    }
}

class UpdateEngineUseCase(
    private val versionManager: com.example.yt2mp3.data.engine.EngineVersionManager,
) {
    suspend fun checkLatest(): Result<String> = versionManager.checkLatest()
    suspend fun update(): Result<String> = versionManager.updateToLatest()
}
