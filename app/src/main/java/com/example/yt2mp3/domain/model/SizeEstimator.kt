package com.example.yt2mp3.domain.model

import java.util.Locale

/** Data-usage and file-size estimates shown before downloading. Pure logic, unit-tested. */
object SizeEstimator {
    /** Typical YouTube audio stream bitrate when the exact size is unknown. */
    private const val TYPICAL_SOURCE_KBPS = 130

    data class Estimate(val downloadBytes: Long, val fileBytes: Long, val exact: Boolean)

    fun forVideo(
        meta: VideoMetadata,
        format: AudioFormat,
        quality: AudioQuality,
        sectionStart: Int? = null,
        sectionEnd: Int? = null,
    ): Estimate? {
        val duration = meta.durationSecs?.takeIf { it > 0 }
        // Mirrors the engine's source choice: M4A output and any trim download the M4A stream.
        val trimmed = sectionStart != null || sectionEnd != null
        val known = if (format == AudioFormat.M4A || trimmed) {
            meta.m4aBytes ?: meta.bestAudioBytes
        } else {
            meta.bestAudioBytes
        }
        val fullSource = known ?: duration?.let { it * TYPICAL_SOURCE_KBPS * 125 } ?: return null
        val fraction = sectionFraction(duration, sectionStart, sectionEnd)
        val download = (fullSource * fraction).toLong()
        val keptSecs = duration?.let { it * fraction }
        val file = when (format) {
            AudioFormat.M4A -> download
            AudioFormat.MP3 -> keptSecs?.let { (it * quality.bitrateKbps * 125).toLong() } ?: download
        }
        return Estimate(download, file, exact = known != null)
    }

    fun forDurations(durations: List<Long?>, format: AudioFormat, quality: AudioQuality): Estimate {
        val total = durations.sumOf { it ?: 0L }
        val download = total * TYPICAL_SOURCE_KBPS * 125
        val file = if (format == AudioFormat.MP3) total * quality.bitrateKbps * 125 else download
        return Estimate(download, file, exact = false)
    }

    private fun sectionFraction(duration: Long?, start: Int?, end: Int?): Double {
        if (duration == null || (start == null && end == null)) return 1.0
        val s = (start ?: 0).coerceIn(0, duration.toInt())
        val e = (end ?: duration.toInt()).coerceIn(s, duration.toInt())
        return (e - s).toDouble() / duration
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes / 1_048_576.0
        return if (mb >= 10) String.format(Locale("in", "ID"), "%.0f MB", mb)
        else String.format(Locale("in", "ID"), "%.1f MB", mb.coerceAtLeast(0.1))
    }
}
