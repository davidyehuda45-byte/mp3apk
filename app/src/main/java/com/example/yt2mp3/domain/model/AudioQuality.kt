package com.example.yt2mp3.domain.model

/** Single source of truth for MP3 quality. PRD §9: no scattered hard-coded bitrates. */
enum class AudioQuality(val bitrateKbps: Int, val label: String) {
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
 * MP3 is re-encoded at [AudioQuality]; M4A keeps YouTube's original AAC stream
 * (no conversion: faster, smaller, no generational quality loss).
 */
enum class AudioFormat(val label: String, val ext: String, val mime: String) {
    MP3("MP3", "mp3", "audio/mpeg"),
    M4A("M4A (asli)", "m4a", "audio/mp4");

    companion object {
        val DEFAULT = MP3
        fun fromName(name: String?): AudioFormat = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
