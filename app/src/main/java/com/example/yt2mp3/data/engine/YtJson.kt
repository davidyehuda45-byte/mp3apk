package com.example.yt2mp3.data.engine

import com.example.yt2mp3.data.util.SupportedSites
import com.example.yt2mp3.data.util.UrlValidator
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoMetadata
import org.json.JSONArray
import org.json.JSONObject

/** Parses yt-dlp's --dump-json / --dump-single-json output. Unit-tested against real yt-dlp output. */
object YtJson {
    fun firstJsonLine(stdout: String): String? =
        stdout.lineSequence().firstOrNull { it.trimStart().startsWith("{") }

    fun metadata(cleanUrl: String, json: JSONObject): VideoMetadata {
        val id = json.str("id")
        val duration = json.durationSecs()
        val extractor = json.str("extractor")?.lowercase()
        val isYoutube = extractor == null || extractor.contains("youtube")
        // YouTube: i.ytimg.com JPEG renders as ID3 cover in most players.
        // Generic: use extractor thumbnail (may be webp/jpg/png).
        val thumb = if (isYoutube && id != null) {
            "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        } else {
            bestThumbnail(json) ?: json.str("thumbnail")
        }
        return VideoMetadata(
            sourceUrl = cleanUrl,
            title = json.str("title") ?: json.str("fulltitle") ?: json.str("track") ?: "Unknown title",
            artist = (json.str("artist") ?: json.str("uploader") ?: json.str("channel") ?: json.str("creator"))?.trim(),
            durationSecs = duration,
            thumbnailUrl = thumb,
            bestAudioBytes = json.sizeBytes(duration),
            m4aBytes = bestM4a(json.optJSONArray("formats"))?.sizeBytes(duration),
            playlistUrl = UrlValidator.playlistOfVideo(cleanUrl),
            extractor = extractor,
            siteName = SupportedSites.siteNameForUrl(cleanUrl),
        )
    }

    private fun bestThumbnail(json: JSONObject): String? {
        val thumbs = json.optJSONArray("thumbnails") ?: return null
        var best: String? = null
        var bestArea = -1
        for (i in 0 until thumbs.length()) {
            val t = thumbs.optJSONObject(i) ?: continue
            val url = t.str("url") ?: continue
            val w = t.optInt("width", -1)
            val h = t.optInt("height", -1)
            val area = if (w > 0 && h > 0) w * h else 0
            if (area >= bestArea) {
                bestArea = area
                best = url
            }
        }
        return best
    }

    private fun bestM4a(formats: JSONArray?): JSONObject? {
        if (formats == null) return null
        var best: JSONObject? = null
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val audioOnly = f.str("vcodec") == "none" && f.str("acodec").let { it != null && it != "none" }
            if (!audioOnly || f.str("ext") != "m4a") continue
            if (best == null || f.optDouble("abr", 0.0) > best.optDouble("abr", 0.0)) best = f
        }
        return best
    }

    fun entries(json: JSONObject): List<VideoEntry> {
        val entries = json.optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<VideoEntry>(entries.length())
        val dotted = dottedMinutes(entries)
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val url = entryUrl(e) ?: continue
            val title = e.str("title") ?: e.str("fulltitle") ?: e.str("track") ?: continue
            val duration = e.listDurationSecs(dotted)
            // "[Private video]" / "[Deleted video]" placeholders can't be downloaded.
            if (duration == null && title.startsWith("[") && title.endsWith("]")) continue
            if (e.str("live_status") == "is_live") continue
            val id = e.str("id")
            val extractor = e.str("extractor_key") ?: e.str("extractor")
            val thumb = if (id != null && (extractor == null || extractor.contains("youtube", ignoreCase = true))) {
                "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            } else {
                bestThumbnail(e) ?: e.str("thumbnail")
            }
            out += VideoEntry(
                url = url,
                title = title,
                channel = (e.str("channel") ?: e.str("uploader") ?: e.str("artist"))?.trim(),
                durationSecs = duration,
                // hqdefault doubles as cover art; lists show the tiny variant (see listThumb).
                thumbnailUrl = thumb,
                extractor = extractor?.lowercase(),
            )
        }
        return out
    }

    /**
     * Entry URL for generic extractors. Prefers real page URLs over
     * stream URLs: webpage_url > url (http only) > YouTube watch URL.
     */
    private fun entryUrl(e: JSONObject): String? {
        e.str("webpage_url")?.takeIf { it.startsWith("http") }?.let {
            val n = UrlValidator.normalizeSupportedUrl(it)
            if (UrlValidator.isSupported(n)) return n
        }
        e.str("url")?.takeIf { it.startsWith("http") }?.let {
            if (UrlValidator.isSupported(it)) return it
        }
        val id = e.str("id") ?: return null
        val extractor = (e.str("extractor_key") ?: e.str("extractor"))?.lowercase()
        // Bare ids from generic flat playlists are not downloadable without a page URL.
        if (extractor != null && !extractor.contains("youtube")) return null
        return UrlValidator.extractFirstSupportedUrl("https://www.youtube.com/watch?v=$id")
    }

    fun title(json: JSONObject): String? = json.str("title")

    /** meta_* take priority in yt-dlp's --embed-metadata, so edited title/artist land in the file. */
    fun withEditedTags(infoJson: String, title: String, artist: String?): String =
        JSONObject(infoJson).apply {
            put("meta_title", title)
            artist?.takeIf { it.isNotBlank() }?.let { put("meta_artist", it) }
        }.toString()

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.durationSecs(): Long? =
        optDouble("duration", Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toLong()

    private fun JSONObject.rawDuration(): Double? =
        optDouble("duration", Double.NaN).takeIf { !it.isNaN() && it > 0 }

    /**
     * Flat search/playlist durations come from YouTube's localized "lengthText". Locales like
     * Indonesian write "3.45" instead of "3:45", which yt-dlp parses as 3.45 *seconds*.
     * Real durations are whole seconds, so any fractional value means the whole list is "m.ss"
     * (including "3.00", which arrives as a plain 3).
     */
    private fun dottedMinutes(entries: JSONArray): Boolean =
        (0 until entries.length()).any { i ->
            val d = entries.optJSONObject(i)?.rawDuration() ?: return@any false
            d != Math.floor(d)
        }

    private fun JSONObject.listDurationSecs(dotted: Boolean): Long? {
        val raw = rawDuration() ?: return null
        if (!dotted) return raw.toLong()
        val mins = raw.toLong()
        val secs = Math.round((raw - mins) * 100)
        return if (secs < 60) mins * 60 + secs else raw.toLong()
    }

    /** Exact size when reported, else bitrate × duration. */
    private fun JSONObject.sizeBytes(durationSecs: Long?): Long? {
        optLong("filesize", -1).takeIf { it > 0 }?.let { return it }
        optLong("filesize_approx", -1).takeIf { it > 0 }?.let { return it }
        val kbps = optDouble("abr", Double.NaN).takeIf { !it.isNaN() && it > 0 }
            ?: optDouble("tbr", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        return if (kbps != null && durationSecs != null) (kbps * 125 * durationSecs).toLong() else null
    }
}
