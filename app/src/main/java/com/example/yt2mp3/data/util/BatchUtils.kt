package com.example.yt2mp3.data.util

/** Parses playlist range text like "1-10,15,20-30" (1-based) into 0-based indices. Pure logic. */
object PlaylistRange {
    /** Returns null when the text is invalid. Empty/blank means "all". */
    fun parse(text: String, size: Int): Set<Int>? {
        val t = text.trim()
        if (t.isEmpty()) return (0 until size).toSet()
        val out = LinkedHashSet<Int>()
        for (part in t.split(',')) {
            val p = part.trim()
            if (p.isEmpty()) return null
            if ('-' in p) {
                val bounds = p.split('-').map { it.trim() }
                if (bounds.size != 2) return null
                val a = bounds[0].toIntOrNull() ?: return null
                val b = bounds[1].toIntOrNull() ?: return null
                if (a < 1 || b < 1 || a > b || b > size) return null
                for (i in a..b) out += i - 1
            } else {
                val n = p.toIntOrNull() ?: return null
                if (n < 1 || n > size) return null
                out += n - 1
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }
}

/**
 * Rename pattern with placeholders: {title} {artist} {id} {site} {date}.
 * Unknown placeholders are kept literally; result is sanitized.
 */
object FileNamePattern {
    const val DEFAULT = "{title}"
    private val illegal = Regex("""[/\\:*?"<>|]""")

    fun apply(
        pattern: String,
        title: String,
        artist: String?,
        id: String?,
        site: String?,
        date: String,
        fallbackBase: String = "audio",
    ): String {
        var base = pattern.ifBlank { DEFAULT }
            .replace("{title}", title)
            .replace("{artist}", artist.orEmpty())
            .replace("{id}", id.orEmpty())
            .replace("{site}", site.orEmpty())
            .replace("{date}", date)
        base = illegal.replace(base.trim(), "_")
        base = base.replace(Regex("""\s+"""), " ").trim(' ', '.', '_')
        // Collapse empty segments from missing placeholders ("A -  [x]" -> "A [x]").
        base = base.replace(Regex("""\s{2,}"""), " ").replace(" -  ", " ").trim(' ', '.', '_', '-')
        if (base.isEmpty()) base = fallbackBase
        if (base.length > 120) base = base.take(120).trimEnd(' ', '.', '_')
        return base
    }
}

/** Splits free-form extra args respecting single/double quotes. Filters login/DRM tokens. */
object ArgSplitter {
    private val blockedSubstrings = listOf(
        "--cookies", "--username", "--password", "--login", "--drm",
        "--allow-unplayable", "--allow-drm",
    )

    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quote != null -> {
                    if (c == quote) quote = null else cur.append(c)
                }
                c == '"' || c == '\'' -> quote = c
                c.isWhitespace() -> {
                    if (cur.isNotEmpty()) {
                        out += cur.toString()
                        cur.clear()
                    }
                }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** Returns the args with blocked login/DRM tokens removed. */
    fun sanitized(args: List<String>): List<String> {
        val out = mutableListOf<String>()
        var skipNext = false
        for (a in args) {
            if (skipNext) {
                skipNext = false
                continue
            }
            val low = a.lowercase()
            if (blockedSubstrings.any { low.startsWith(it) }) {
                // "--username=x" form is self-contained; bare "--username" eats the next token too.
                if (!a.contains('=')) skipNext = true
                continue
            }
            if (a == "-u" || a == "-p") {
                skipNext = true
                continue
            }
            out += a
        }
        return out
    }
}
