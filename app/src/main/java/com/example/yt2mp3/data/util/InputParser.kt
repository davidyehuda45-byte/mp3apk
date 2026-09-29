package com.example.yt2mp3.data.util

/** What the user typed or pasted into the single Home input. Pure logic, unit-tested. */
sealed interface ParsedInput {
    data object Empty : ParsedInput
    data class Video(val url: String) : ParsedInput
    data class Playlist(val url: String) : ParsedInput
    data class Search(val query: String) : ParsedInput
    /** A link, but not one the app can handle (channel page, other site…). */
    data object Unsupported : ParsedInput
}

object InputParser {
    fun parse(raw: String?): ParsedInput {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ParsedInput.Empty
        UrlValidator.extractFirstSupportedUrl(text)?.let { return ParsedInput.Video(it) }
        UrlValidator.extractPlaylistUrl(text)?.let { return ParsedInput.Playlist(it) }
        if (UrlValidator.looksLikeUrl(text)) return ParsedInput.Unsupported
        return ParsedInput.Search(text.take(200))
    }
}
