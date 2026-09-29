package com.example.yt2mp3.domain.model

data class VideoMetadata(
    val sourceUrl: String,
    val title: String,
    val artist: String?,
    val durationSecs: Long?,
    val thumbnailUrl: String?,
    /** Bytes of the stream MP3 conversion downloads (best audio), if YouTube reports it. */
    val bestAudioBytes: Long? = null,
    /** Bytes of the best original M4A/AAC stream, if one exists. */
    val m4aBytes: Long? = null,
    /** Set when the link was a video inside a playlist (watch?v=…&list=…). */
    val playlistUrl: String? = null,
)

/** A lightweight entry from search results or a playlist listing (no full extraction). */
data class VideoEntry(
    val url: String,
    val title: String,
    val channel: String?,
    val durationSecs: Long?,
    val thumbnailUrl: String?,
)

data class PlaylistInfo(
    val title: String,
    val entries: List<VideoEntry>,
    /** True when the playlist had more entries than the app lists. */
    val truncated: Boolean,
)

/** Everything the queue needs to produce one file. Section bounds are in seconds. */
data class DownloadRequest(
    val sourceUrl: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val quality: AudioQuality,
    val format: AudioFormat,
    val sectionStart: Int? = null,
    val sectionEnd: Int? = null,
)

data class DownloadItem(
    val id: Long = 0,
    val sourceUrl: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val outputFileName: String,
    val outputUri: String? = null,
    val quality: AudioQuality,
    val format: AudioFormat = AudioFormat.MP3,
    val sectionStart: Int? = null,
    val sectionEnd: Int? = null,
    val status: DownloadStatus,
    /** 0..100 when known, null when indeterminate (PRD §16: never fake progress). */
    val progress: Int?,
    val stage: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

data class DownloadProgress(
    val itemId: Long,
    val status: DownloadStatus,
    /** 0..100 or null if indeterminate. */
    val percent: Int?,
    val stage: String,
)
