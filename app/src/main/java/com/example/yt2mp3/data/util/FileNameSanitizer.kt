package com.example.yt2mp3.data.util

/** Filename sanitization (PRD §18). Pure logic, unit-tested. */
object FileNameSanitizer {
    private val illegal = Regex("""[/\\:*?"<>|]""")
    private const val MAX_BASE_LEN = 120

    fun sanitizeWithExtension(title: String, extension: String): String {
        var base = illegal.replace(title.trim(), "_").trim()
        base = base.replace(Regex("""\s+"""), " ").trim(' ', '.', '_')
        if (base.isEmpty()) base = "audio"
        if (base.length > MAX_BASE_LEN) base = base.take(MAX_BASE_LEN).trimEnd(' ', '.', '_')
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        return base + ext.lowercase()
    }

    /** Resolves collisions: Song.mp3, Song (1).mp3, ... (PRD §18). */
    fun resolveCollision(desired: String, exists: (String) -> Boolean): String {
        if (!exists(desired)) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }
}
