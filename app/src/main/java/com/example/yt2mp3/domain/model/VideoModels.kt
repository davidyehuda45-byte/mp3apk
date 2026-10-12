package com.example.yt2mp3.domain.model

/** Audio vs video output. */
enum class MediaKind { AUDIO, VIDEO }

/** Video container for downloads (merged via ffmpeg). */
enum class VideoFormat(val label: String, val ext: String, val mime: String) {
    MP4("MP4", "mp4", "video/mp4"),
    MKV("MKV", "mkv", "video/x-matroska"),
    WEBM("WEBM", "webm", "video/webm");

    companion object {
        val DEFAULT = MP4
        fun fromName(name: String?): VideoFormat = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Max height cap; null = best available. Approx bitrate only for size estimates. */
enum class VideoQuality(val label: String, val maxHeight: Int?, val approxKbps: Int) {
    BEST("Terbaik", null, 8000),
    Q2160("4K", 2160, 16000),
    Q1440("1440p", 1440, 9000),
    Q1080("1080p", 1080, 5000),
    Q720("720p", 720, 2500),
    Q480("480p", 480, 1200),
    Q360("360p", 360, 700),
    Q240("240p", 240, 400),
    Q144("144p", 144, 200);

    companion object {
        val DEFAULT = BEST
        fun fromName(name: String?): VideoQuality = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Subtitle handling for video downloads. */
enum class SubtitleMode(val label: String) {
    NONE("Tanpa subtitle"),
    EMBED("Embed ke video"),
    SEPARATE("File terpisah (SRT)");

    companion object {
        fun fromName(name: String?): SubtitleMode = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Post-processing flags attached to one queued item. */
data class PostOptions(
    val subtitleMode: SubtitleMode = SubtitleMode.NONE,
    val subLangs: String = "en,id",
    val embedChapters: Boolean = false,
    val sponsorBlock: Boolean = false,
    val normalizeAudio: Boolean = false,
    val saveThumbSeparate: Boolean = false,
    /** Privacy: skip all metadata/thumbnail embedding. */
    val stripMetadata: Boolean = false,
)

/** UI theme choice. */
enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap"),
    AMOLED("AMOLED hitam");

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** History sort order. */
enum class SortMode(val label: String) {
    NEWEST("Terbaru"),
    OLDEST("Terlama"),
    TITLE("Judul A–Z"),
    ARTIST("Artis A–Z"),
    STATUS("Status");

    companion object {
        fun fromName(name: String?): SortMode = entries.firstOrNull { it.name == name } ?: NEWEST
    }
}

/** Named preset for one-tap format/quality/post options. */
data class DownloadProfile(
    val name: String,
    val mediaKind: MediaKind = MediaKind.AUDIO,
    val audioFormat: AudioFormat = AudioFormat.DEFAULT,
    val audioQuality: AudioQuality = AudioQuality.DEFAULT,
    val videoFormat: VideoFormat = VideoFormat.DEFAULT,
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    val post: PostOptions = PostOptions(),
)
