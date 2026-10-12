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

    private fun DownloadRequest.toEntity(now: Long): DownloadEntity {
        val ext = if (mediaKind == com.example.yt2mp3.domain.model.MediaKind.VIDEO) videoFormat.ext else format.ext
        val base = outputBaseName?.takeIf { it.isNotBlank() } ?: title
        return DownloadEntity(
            sourceUrl = sourceUrl,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            outputFileName = FileNameSanitizer.sanitizeWithExtension(base, ".$ext"),
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
            mediaKind = mediaKind.name,
            videoFormat = videoFormat.name,
            videoQuality = videoQuality.name,
            subtitleMode = post.subtitleMode.name,
            subLangs = post.subLangs,
            embedChapters = post.embedChapters,
            sponsorBlock = post.sponsorBlock,
            normalizeAudio = post.normalizeAudio,
            saveThumb = post.saveThumbSeparate,
            stripMetadata = post.stripMetadata,
            subfolder = subfolder?.takeIf { it.isNotBlank() },
            favorite = false,
        )
    }

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

    override suspend fun existingUrls(urls: List<String>): Set<String> =
        if (urls.isEmpty()) emptySet() else dao.existingUrls(urls).toSet()

    override suspend fun updateCreatedAt(id: Long, ts: Long) = dao.updateCreatedAt(id, ts)

    override suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)

    override suspend fun updateOutput(id: Long, name: String, uri: String) = dao.updateOutput(id, name, uri)

    override suspend fun retryAllFailed(): Int = dao.retryAllFailed()

    override suspend fun clearTerminal(): Int = dao.clearTerminal()

    override suspend fun reorder(id: Long, dir: Int) {
        val snap = dao.queueSnapshot().sortedWith(compareBy({ it.createdAt }, { it.id }))
        val i = snap.indexOfFirst { it.id == id }
        if (i < 0) return
        val j = i + dir
        if (j !in snap.indices) return
        val ta = snap[i].createdAt
        dao.updateCreatedAt(snap[i].id, snap[j].createdAt)
        dao.updateCreatedAt(snap[j].id, ta)
    }

    override suspend fun moveToTop(id: Long) {
        val snap = dao.queueSnapshot()
        val min = snap.minOfOrNull { it.createdAt } ?: System.currentTimeMillis()
        dao.updateCreatedAt(id, min - 1)
    }
}
