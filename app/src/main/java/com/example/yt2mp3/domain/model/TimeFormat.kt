package com.example.yt2mp3.domain.model

import java.util.Locale

/** "m:ss" / "h:mm:ss" parsing and formatting for trim fields and durations. Pure logic, unit-tested. */
object TimeFormat {
    /** Accepts "90", "1:30", "01:02:03" (also "." as separator). Returns seconds or null. */
    fun parse(text: String): Int? {
        val t = text.trim().replace('.', ':')
        if (t.isEmpty()) return null
        val parts = t.split(':')
        if (parts.size > 3 || parts.any { it.isEmpty() || it.length > 4 || !it.all(Char::isDigit) }) return null
        val nums = parts.map { it.toInt() }
        // Minutes/seconds after the first field must be < 60 ("1:75" is a typo, not 2:15).
        if (nums.drop(1).any { it >= 60 }) return null
        return nums.fold(0) { acc, n -> acc * 60 + n }
    }

    fun format(totalSecs: Long): String {
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    sealed interface TrimResult {
        data class Ok(val start: Int?, val end: Int?) : TrimResult
        data class Invalid(val message: String) : TrimResult
    }

    /** Validates trim inputs against the video duration. Blank start = 0, blank end = until the end. */
    fun validateTrim(startText: String, endText: String, durationSecs: Long?): TrimResult {
        val start = if (startText.isBlank()) 0 else parse(startText)
            ?: return TrimResult.Invalid("Waktu mulai tidak valid. Contoh: 1:30")
        val end = if (endText.isBlank()) null else parse(endText)
            ?: return TrimResult.Invalid("Waktu selesai tidak valid. Contoh: 2:45")
        val dur = durationSecs?.toInt()
        if (dur != null && start >= dur) return TrimResult.Invalid("Waktu mulai melebihi durasi (${format(dur.toLong())}).")
        if (dur != null && end != null && end > dur) return TrimResult.Invalid("Waktu selesai melebihi durasi (${format(dur.toLong())}).")
        if (end != null && end - start < 1) return TrimResult.Invalid("Waktu selesai harus setelah waktu mulai.")
        val endOrOpen = end?.takeIf { dur == null || it < dur }
        if (start == 0 && endOrOpen == null) return TrimResult.Ok(null, null)
        return TrimResult.Ok(start.takeIf { it > 0 }, endOrOpen)
    }
}
