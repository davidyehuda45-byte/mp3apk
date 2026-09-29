package com.example.yt2mp3.domain.usecase

import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.DownloadRequest
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.repository.DownloadRepository
import com.example.yt2mp3.domain.repository.SettingsRepository

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
        val ids = downloads.enqueueAll(requests)
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
}

class UpdateEngineUseCase(
    private val versionManager: com.example.yt2mp3.data.engine.EngineVersionManager,
) {
    suspend fun checkLatest(): Result<String> = versionManager.checkLatest()
    suspend fun update(): Result<String> = versionManager.updateToLatest()
}
