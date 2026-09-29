package com.example.yt2mp3.domain.repository

import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadRequest
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.PlaylistInfo
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

interface MetadataRepository {
    /** Throws on invalid/unsupported/private content. Never bypasses access controls (PRD §1.2). */
    suspend fun fetchMetadata(url: String): VideoMetadata
    suspend fun search(query: String): List<VideoEntry>
    suspend fun fetchPlaylist(url: String): PlaylistInfo
}

interface DownloadRepository {
    fun observeQueue(): Flow<List<DownloadItem>>
    fun observeHistory(): Flow<List<DownloadItem>>
    suspend fun enqueue(request: DownloadRequest): Long
    suspend fun enqueueAll(requests: List<DownloadRequest>): List<Long>
    suspend fun get(id: Long): DownloadItem?
    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String? = null)
    suspend fun updateProgress(id: Long, percent: Int?, stage: String?)
    suspend fun setOutputUri(id: Long, uri: String)
    suspend fun deleteEntry(id: Long)
    suspend fun nextQueued(excludeId: Long? = null): DownloadItem?
    suspend fun hasActive(): Boolean

    /** Re-queues items left mid-run by a killed process. Call only before the queue worker starts. */
    suspend fun requeueInterrupted()
}

interface SettingsRepository {
    fun observeDestinationUri(): Flow<String?>
    suspend fun getDestinationUri(): String?
    suspend fun setDestinationUri(uri: String)
    fun observeDefaultQuality(): Flow<AudioQuality>
    suspend fun getDefaultQuality(): AudioQuality
    suspend fun setDefaultQuality(quality: AudioQuality)
    fun observeDefaultFormat(): Flow<AudioFormat>
    suspend fun setDefaultFormat(format: AudioFormat)
    suspend fun getEngineVersion(): String?
    suspend fun setEngineVersion(version: String)
    fun observeEngineVersion(): Flow<String?>
    suspend fun getLastEngineCheck(): Long
    suspend fun setLastEngineCheck(epochMillis: Long)
}
