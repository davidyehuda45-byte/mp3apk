package com.example.yt2mp3.data.util

/** URL validation + Share-Intent extraction (PRD §6, §45). Pure JVM logic, unit-tested. */
object UrlValidator {
    private val urlRegex = Regex("""https?://[^\s'"]+""", RegexOption.IGNORE_CASE)

    private val supportedHosts = listOf(
        "youtube.com",
        "youtu.be",
        "music.youtube.com",
        "m.youtube.com",
        "www.youtube.com",
        "youtube-nocookie.com",
    )

    /** Query params that are safe to keep. Everything else (si, feature, pp, fbclid…) is tracking. */
    private val keepParams = setOf("v", "list", "index", "t", "start", "end")

    /** Scheme-less links typed by hand, e.g. "youtu.be/ID" or "m.youtube.com/watch?v=ID". */
    private val bareYoutubeRegex = Regex(
        """(?<![\w./])(?:(?:www|m|music)\.)?(?:youtube\.com|youtu\.be)/[^\s'"]+""",
        RegexOption.IGNORE_CASE,
    )

    private fun candidates(raw: String): List<String> {
        val trimChars = charArrayOf('.', ',', ')', ']', '!', ';', '\'')
        val withScheme = urlRegex.findAll(raw).map { it.value.trimEnd(*trimChars) }.toList()
        if (withScheme.isNotEmpty()) return withScheme
        return bareYoutubeRegex.findAll(raw).map { "https://" + it.value.trimEnd(*trimChars) }.toList()
    }

    fun extractFirstSupportedUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        // Only single videos: playlist/channel URLs would make yt-dlp walk every entry.
        return candidates(raw).filter { isSupported(it) }
            .map { normalizeSupportedUrl(it) }
            .firstOrNull { singleVideo.matches(it) }
    }

    private val singleVideo = Regex("""https://www\.youtube\.com/watch\?v=[A-Za-z0-9_-]{6,}(&.*)?""")

    /**
     * Playlist link for a playlist page (`/playlist?list=ID`). For a video opened
     * inside a playlist, use [playlistOfVideo] instead.
     */
    fun extractPlaylistUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        for (c in candidates(raw).filter { isSupported(it) }) {
            val uri = runCatching { java.net.URI(c.trim()) }.getOrNull() ?: continue
            val list = parseQuery(uri.rawQuery)["list"]?.takeIf { validListId.matches(it) } ?: continue
            if (parseQuery(uri.rawQuery)["v"] == null) return "https://www.youtube.com/playlist?list=$list"
        }
        return null
    }

    /** For `watch?v=ID&list=L` returns a URL yt-dlp expands as the playlist (incl. Mixes). */
    fun playlistOfVideo(normalizedVideoUrl: String): String? {
        val uri = runCatching { java.net.URI(normalizedVideoUrl) }.getOrNull() ?: return null
        val params = parseQuery(uri.rawQuery)
        val v = params["v"] ?: return null
        val list = params["list"]?.takeIf { validListId.matches(it) } ?: return null
        return "https://www.youtube.com/watch?v=$v&list=$list"
    }

    fun videoId(normalizedVideoUrl: String): String? {
        val uri = runCatching { java.net.URI(normalizedVideoUrl) }.getOrNull() ?: return null
        return parseQuery(uri.rawQuery)["v"]?.takeIf { validVideoId.matches(it) }
    }

    /** True when the text looks like it was meant as a link rather than a search query. */
    fun looksLikeUrl(raw: String): Boolean =
        raw.contains("://") || raw.contains("youtu", ignoreCase = true) ||
            Regex("""(?i)\b(?:www\.|[a-z0-9-]+\.(?:com|be|net|org|id|co|io|me|tv)\b/?)""").containsMatchIn(raw)

    private val validListId = Regex("""[A-Za-z0-9_-]{2,}""")
    private val validVideoId = Regex("""[A-Za-z0-9_-]{6,}""")

    fun isSupported(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (' ' in host || '.' !in host) return false
        // Exact or subdomain match — "notyoutube.com" must not pass.
        return supportedHosts.any { h -> host == h || host.endsWith(".$h") }
    }

    /**
     * Normalizes any supported YouTube URL to a form the extractor reliably accepts.
     *
     * - `https://youtu.be/ID?si=xxx&t=30` → `https://www.youtube.com/watch?v=ID&t=30`
     *   (strips the `si` share-tracking param that breaks old extractors).
     * - `/shorts/ID`, `/live/ID`, `/embed/ID`, `/v/ID` → `/watch?v=ID`
     * - watch URLs keep only v/list/index/t/start/end, tracking params dropped.
     */
    fun normalizeSupportedUrl(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return trimmed
        val host = uri.host?.lowercase() ?: return trimmed
        val params = parseQuery(uri.rawQuery)

        // Short youtu.be link: ID is the first path segment.
        if (host == "youtu.be" || host.endsWith(".youtu.be")) {
            val id = uri.path?.trim('/')?.substringBefore('/')?.substringBefore('?')?.trim()
            if (!id.isNullOrBlank()) {
                val t = params["t"] ?: params["start"]
                return buildWatchUrl(id, t)
            }
            return trimmed
        }

        val path = uri.path ?: ""
        // /shorts/ID, /live/ID, /embed/ID, /v/ID → canonical watch URL.
        val prefixId = when {
            path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
            path.startsWith("/live/") -> path.removePrefix("/live/").substringBefore('/')
            path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/')
            path.startsWith("/v/") -> path.removePrefix("/v/").substringBefore('/')
            else -> null
        }?.substringBefore('?')?.trim()
        if (!prefixId.isNullOrBlank()) {
            val t = params["t"] ?: params["start"]
            return buildWatchUrl(prefixId, t)
        }

        // Standard watch URL: drop tracking params, keep playback-relevant ones.
        val v = params["v"]
        if (v != null && (host == "youtube.com" || host.endsWith(".youtube.com") ||
                    host == "music.youtube.com" || host == "m.youtube.com" ||
                    host == "www.youtube.com" || host.endsWith(".youtube-nocookie.com"))
        ) {
            val kept = params.filterKeys { it in keepParams }
            if (kept.isNotEmpty()) {
                val query = kept.entries.joinToString("&") { (k, value) -> "$k=$value" }
                return "https://www.youtube.com/watch?$query"
            }
            return "https://www.youtube.com/watch?v=$v"
        }
        return trimmed
    }

    private fun buildWatchUrl(videoId: String, t: String?): String =
        if (!t.isNullOrBlank()) "https://www.youtube.com/watch?v=$videoId&t=$t"
        else "https://www.youtube.com/watch?v=$videoId"

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }.toMap()
    }

    fun isBlank(input: String?): Boolean = input.isNullOrBlank()
}
