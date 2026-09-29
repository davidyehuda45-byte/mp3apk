package com.example.yt2mp3.data.repository

import com.example.yt2mp3.data.database.DownloadDao
import com.example.yt2mp3.data.database.DownloadEntity
import com.example.yt2mp3.data.database.toDomain
import com.example.yt2mp3.data.util.FileNameSanitizer
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadRequest
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadRepositoryImpl(private val dao: DownloadDao) : DownloadRepository {
    override fun observeQueue(): Flow<List<DownloadItem>> =
        dao.observeActiveQueue().map { list -> list.map { it.toDomain() } }

    override fun observeHistory(): Flow<List<DownloadItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun enqueue(request: DownloadRequest): Long =
        dao.insert(request.toEntity(System.currentTimeMillis()))

    override suspend fun enqueueAll(requests: List<DownloadRequest>): List<Long> {
        val now = System.currentTimeMillis()
        // Same createdAt keeps playlist order via the id tiebreaker in queue queries.
        return dao.insertAll(requests.map { it.toEntity(now) })
    }

    private fun DownloadRequest.toEntity(now: Long) = DownloadEntity(
        sourceUrl = sourceUrl,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        outputFileName = FileNameSanitizer.sanitizeWithExtension(title, ".${format.ext}"),
        qualityKbps = quality.bitrateKbps,
        format = format.name,
        sectionStart = sectionStart,
        sectionEnd = sectionEnd,
        status = DownloadStatus.QUEUED,
        progress = null,
        stage = "Waiting",
        errorMessage = null,
        createdAt = now,
        completedAt = null,
    )

    override suspend fun get(id: Long): DownloadItem? = dao.getById(id)?.toDomain()

    override suspend fun updateStatus(id: Long, status: DownloadStatus, error: String?) {
        val done = if (status.isTerminal) System.currentTimeMillis() else null
        dao.updateStatus(id, status, error, done)
    }

    override suspend fun updateProgress(id: Long, percent: Int?, stage: String?) {
        dao.updateProgress(id, percent?.coerceIn(0, 100), stage)
    }

    override suspend fun setOutputUri(id: Long, uri: String) = dao.updateOutputUri(id, uri)

    override suspend fun deleteEntry(id: Long) = dao.delete(id)

    override suspend fun nextQueued(excludeId: Long?): DownloadItem? =
        dao.nextQueued()?.toDomain()

    override suspend fun hasActive(): Boolean = dao.activeCount() > 0

    override suspend fun requeueInterrupted() = dao.requeueInterrupted()
}
