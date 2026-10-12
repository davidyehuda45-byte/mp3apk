package com.example.yt2mp3.data.util

/** What the user typed or pasted into the single Home input. Pure logic, unit-tested. */
sealed interface ParsedInput {
    data object Empty : ParsedInput
    data class Video(val url: String) : ParsedInput
    data class Playlist(val url: String) : ParsedInput
    /** Multiple links pasted at once (batch). Capped at [UrlValidator.MAX_BATCH]. */
    data class Batch(val urls: List<String>) : ParsedInput
    data class Search(val query: String) : ParsedInput
    /** Text that looks like a link but has no usable URL. */
    data object Unsupported : ParsedInput
}

object InputParser {
    fun parse(raw: String?): ParsedInput {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ParsedInput.Empty
        val all = UrlValidator.extractAllSupportedUrls(text)
        if (all.size >= 2) return ParsedInput.Batch(all)
        if (all.size == 1) {
            val single = all.first()
            // YouTube playlist/channel pages open the playlist flow.
            if (UrlValidator.isYoutubeUrl(single) &&
                (UrlValidator.extractPlaylistUrl(text) != null || UrlValidator.youtubeListable.containsMatchIn(single))
            ) {
                return ParsedInput.Playlist(single)
            }
            return ParsedInput.Video(single)
        }
        UrlValidator.extractPlaylistUrl(text)?.let { return ParsedInput.Playlist(it) }
        if (UrlValidator.looksLikeUrl(text)) return ParsedInput.Unsupported
        return ParsedInput.Search(text.take(200))
    }
}
