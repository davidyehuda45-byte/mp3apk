package com.example.yt2mp3.data.util

/**
 * URL validation + Share-Intent extraction.
 *
 * Fase 1: generic yt-dlp support. Any public http(s) URL is accepted and
 * passed to the on-device engine (1000+ sites). YouTube URLs are still
 * normalized to a canonical watch URL; other sites pass through trimmed.
 *
 * Legal: no cookies/login, no DRM bypass. Private/restricted content
 * surfaces an error downstream, never a credential prompt.
 */
object UrlValidator {
    private val urlRegex = Regex("""https?://[^\s'"]+""", RegexOption.IGNORE_CASE)

    private val youtubeHosts = listOf(
        "youtube.com",
        "youtu.be",
        "music.youtube.com",
        "m.youtube.com",
        "www.youtube.com",
        "youtube-nocookie.com",
    )

    /** Query params that are safe to keep on YouTube watch URLs. */
    private val keepParams = setOf("v", "list", "index", "t", "start", "end")

    /** Scheme-less links typed by hand, e.g. "youtu.be/ID" or "tiktok.com/@x/video/1". */
    private val bareUrlRegex = Regex(
        """(?<![\w./])(?:(?:www|m|music|vm|vt)\.)?[a-z0-9-]+(?:\.[a-z]{2,})(?::\d+)?/[^\s'"]*""",
        RegexOption.IGNORE_CASE,
    )

    /** Max batch links accepted from one paste/import (Fase 1). */
    const val MAX_BATCH = 20

    private fun candidates(raw: String): List<String> {
        val trimChars = charArrayOf('.', ',', ')', ']', '!', ';', '\'')
        val withScheme = urlRegex.findAll(raw).map { it.value.trimEnd(*trimChars) }.toList()
        if (withScheme.isNotEmpty()) return withScheme
        return bareUrlRegex.findAll(raw).map { "https://" + it.value.trimEnd(*trimChars) }.toList()
    }

    /** First URL in the text that the engine can attempt (any http(s) URL). */
    fun extractFirstSupportedUrl(raw: String?): String? =
        extractAllSupportedUrls(raw).firstOrNull()

    /** YouTube pages that list many items (playlist/channel/user/handle/hashtag). */
    val youtubeListable = Regex(
        """^https://www\.youtube\.com/(playlist|channel|c|user|@|hashtag|feed|results|podcasts)""",
    )

    /** All supported URLs in the text, deduped, order-preserving, capped at [MAX_BATCH]. */
    fun extractAllSupportedUrls(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        for (c in candidates(raw)) {
            if (!isSupported(c)) continue
            val normalized = normalizeSupportedUrl(c)
            // Only single media items: playlist pages are opened via extractPlaylistUrl.
            // Generic sites: accept the URL as-is (yt-dlp decides single vs playlist).
            if (isYoutubeUrl(normalized)) {
                if (singleVideo.matches(normalized) || youtubeListable.containsMatchIn(normalized)) out += normalized
            } else {
                out += normalized
            }
            if (out.size >= MAX_BATCH) break
        }
        // YouTube playlist-only links (no v=) are not single videos; surface them
        // when no single video was found so the caller can open the playlist flow.
        if (out.isEmpty()) {
            extractPlaylistUrl(raw)?.let { out += it }
        }
        return out.toList()
    }

    private val singleVideo = Regex("""https://www\.youtube\.com/watch\?v=[A-Za-z0-9_-]{6,}(&.*)?""")

    /**
     * Playlist link for a YouTube playlist page (`/playlist?list=ID`).
     * For generic sites, playlist/channel/album URLs are handled by
     * yt-dlp's flat-playlist expansion in fetchPlaylist().
     */
    fun extractPlaylistUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        for (c in candidates(raw).filter { isSupported(it) }) {
            val uri = runCatching { java.net.URI(c.trim()) }.getOrNull() ?: continue
            val host = uri.host?.lowercase() ?: continue
            if (isYoutubeHost(host)) {
                val list = parseQuery(uri.rawQuery)["list"]?.takeIf { validListId.matches(it) } ?: continue
                if (parseQuery(uri.rawQuery)["v"] == null) return "https://www.youtube.com/playlist?list=$list"
            }
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
        raw.contains("://") ||
            Regex("""(?i)\b(?:www\.|[a-z0-9-]+\.(?:com|be|net|org|id|co|io|me|tv|ru|jp)\b/?)""").containsMatchIn(raw)

    private val validListId = Regex("""[A-Za-z0-9_-]{2,}""")
    private val validVideoId = Regex("""[A-Za-z0-9_-]{6,}""")

    fun isYoutubeHost(host: String): Boolean {
        val h = host.lowercase()
        return youtubeHosts.any { y -> h == y || h.endsWith(".$y") }
    }

    fun isYoutubeUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull() ?: return false
        return isYoutubeHost(host)
    }

    /**
     * Generic support: any http(s) URL with a dotted host.
     * “notyoutube.com” style lookalikes are fine — they go to yt-dlp,
     * which reports “Unsupported URL” when it really can't handle them.
     */
    fun isSupported(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (' ' in host || '.' !in host) return false
        if (host.startsWith(".") || host.endsWith(".")) return false
        return true
    }

    /**
     * Normalizes supported URLs:
     * - YouTube: canonical watch URL (strips si/feature/fbclid tracking, maps
     *   youtu.be/shorts/live/embed/v → /watch?v=).
     * - Other sites: trimmed as-is (tracking params left to yt-dlp).
     */
    fun normalizeSupportedUrl(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return trimmed
        val host = uri.host?.lowercase() ?: return trimmed
        if (!isYoutubeHost(host)) return trimmed
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
        if (v != null) {
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

    /** Stable cache key: YouTube video id, else URL hash (generic sites have no video id). */
    fun cacheKeyFor(normalizedUrl: String): String =
        videoId(normalizedUrl) ?: "g${kotlin.math.abs(normalizedUrl.hashCode())}"
}
