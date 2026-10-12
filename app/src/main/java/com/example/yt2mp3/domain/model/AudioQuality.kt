package com.example.yt2mp3.domain.model

/** Single source of truth for MP3 quality. PRD §9: no scattered hard-coded bitrates. */
enum class AudioQuality(val bitrateKbps: Int, val label: String) {
    Q64(64, "64 kbps"),
    Q96(96, "96 kbps"),
    Q128(128, "128 kbps"),
    Q192(192, "192 kbps"),
    Q320(320, "320 kbps");

    companion object {
        val DEFAULT = Q192
        fun fromBitrate(kbps: Int): AudioQuality =
            entries.firstOrNull { it.bitrateKbps == kbps } ?: DEFAULT
    }
}

/**
 * MP3/OPUS/OGG are re-encoded at [AudioQuality]; M4A keeps the original AAC
 * stream when available (no conversion: faster, smaller, no generational loss).
 * FLAC/WAV are lossless (quality setting ignored for size estimate).
 */
enum class AudioFormat(
    val label: String,
    val ext: String,
    val mime: String,
    /** yt-dlp --audio-format value. */
    val ytdlpFormat: String,
    /** True when [AudioQuality.bitrateKbps] applies to this format. */
    val usesQuality: Boolean,
) {
    MP3("MP3", "mp3", "audio/mpeg", "mp3", true),
    M4A("M4A (asli)", "m4a", "audio/mp4", "m4a", false),
    OPUS("OPUS", "opus", "audio/opus", "opus", true),
    OGG("OGG", "ogg", "audio/ogg", "vorbis", true),
    FLAC("FLAC", "flac", "audio/flac", "flac", false),
    WAV("WAV", "wav", "audio/wav", "wav", false);

    companion object {
        val DEFAULT = MP3
        fun fromName(name: String?): AudioFormat = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
