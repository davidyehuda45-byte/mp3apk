package com.example.yt2mp3.domain.repository

import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadRequest
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.PlaylistInfo
import com.example.yt2mp3.domain.model.PostOptions
import com.example.yt2mp3.domain.model.SubtitleMode
import com.example.yt2mp3.domain.model.ThemeMode
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoFormat
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.domain.model.VideoQuality
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

    suspend fun existingUrls(urls: List<String>): Set<String>
    suspend fun updateCreatedAt(id: Long, ts: Long)
    suspend fun setFavorite(id: Long, fav: Boolean)
    suspend fun updateOutput(id: Long, name: String, uri: String)
    /** Re-queues all FAILED/CANCELLED entries. Returns rows affected. */
    suspend fun retryAllFailed(): Int
    /** Deletes COMPLETED/FAILED/CANCELLED entries (history cleanup, never files). */
    suspend fun clearTerminal(): Int
    /** Priority: dir -1 = up, +1 = down. No-op at the edges. */
    suspend fun reorder(id: Long, dir: Int)
    /** Moves an item to the front of the queue. */
    suspend fun moveToTop(id: Long)
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

    // ---- Fase 2: semua preferensi (tanpa login/cookies) ----
    fun observeMediaKind(): Flow<MediaKind>
    suspend fun setMediaKind(kind: MediaKind)
    fun observeVideoFormat(): Flow<VideoFormat>
    suspend fun setVideoFormat(format: VideoFormat)
    fun observeVideoQuality(): Flow<VideoQuality>
    suspend fun setVideoQuality(quality: VideoQuality)
    fun observeSubtitleMode(): Flow<SubtitleMode>
    suspend fun setSubtitleMode(mode: SubtitleMode)
    suspend fun getSubLangs(): String
    suspend fun setSubLangs(langs: String)
    suspend fun getPostFlags(): PostOptions
    suspend fun setPostFlags(post: PostOptions)
    suspend fun getRenamePattern(): String
    suspend fun setRenamePattern(pattern: String)
    suspend fun getSubfolderPerPlaylist(): Boolean
    suspend fun setSubfolderPerPlaylist(enabled: Boolean)
    suspend fun getSkipExisting(): Boolean
    suspend fun setSkipExisting(enabled: Boolean)
    suspend fun getMaxParallel(): Int
    suspend fun setMaxParallel(n: Int)
    suspend fun getMaxRetries(): Int
    suspend fun setMaxRetries(n: Int)
    suspend fun getFragmentRetries(): Int
    suspend fun setFragmentRetries(n: Int)
    suspend fun getRateLimitKbps(): Int
    suspend fun setRateLimitKbps(kbps: Int)
    suspend fun getWifiOnly(): Boolean
    suspend fun setWifiOnly(enabled: Boolean)
    suspend fun getProxyUrl(): String
    suspend fun setProxyUrl(url: String)
    suspend fun getUserAgent(): String
    suspend fun setUserAgent(ua: String)
    suspend fun getCustomYtdlpArgs(): String
    suspend fun setCustomYtdlpArgs(args: String)
    suspend fun getCustomFfmpegArgs(): String
    suspend fun setCustomFfmpegArgs(args: String)
    suspend fun getCustomFormat(): String
    suspend fun setCustomFormat(format: String)
    suspend fun getDebugMode(): Boolean
    suspend fun setDebugMode(enabled: Boolean)
    suspend fun getSaveHistory(): Boolean
    suspend fun setSaveHistory(enabled: Boolean)
    suspend fun getAutoPaste(): Boolean
    suspend fun setAutoPaste(enabled: Boolean)
    suspend fun getSeenDisclaimer(): Boolean
    suspend fun setSeenDisclaimer()
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun getDynamicColor(): Boolean
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun getLockEnabled(): Boolean
    suspend fun setLockEnabled(enabled: Boolean)
    suspend fun getPinHash(): String
    suspend fun setPinHash(hash: String)
    suspend fun getSchedule(): Pair<Boolean, Pair<Int, Int>>
    suspend fun setSchedule(enabled: Boolean, hour: Int, minute: Int)
    suspend fun getRssChannel(): String
    suspend fun setRssChannel(channel: String)
    suspend fun getRssAuto(): Boolean
    suspend fun setRssAuto(enabled: Boolean)
    suspend fun getProfilesJson(): String
    suspend fun setProfilesJson(json: String)
    suspend fun clearAll()
}
