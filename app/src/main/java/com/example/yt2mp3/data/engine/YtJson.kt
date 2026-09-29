package com.example.yt2mp3.data.engine

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
        return VideoMetadata(
            sourceUrl = cleanUrl,
            title = json.str("title") ?: json.str("fulltitle") ?: "Unknown title",
            artist = (json.str("artist") ?: json.str("uploader") ?: json.str("channel"))?.trim(),
            durationSecs = duration,
            // i.ytimg.com JPEG: webp thumbnails don't render as ID3 cover art in most players.
            thumbnailUrl = id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } ?: json.str("thumbnail"),
            bestAudioBytes = json.sizeBytes(duration),
            m4aBytes = bestM4a(json.optJSONArray("formats"))?.sizeBytes(duration),
            playlistUrl = UrlValidator.playlistOfVideo(cleanUrl),
        )
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
            val id = e.str("id") ?: continue
            val url = UrlValidator.extractFirstSupportedUrl("https://www.youtube.com/watch?v=$id") ?: continue
            val title = e.str("title") ?: continue
            val duration = e.listDurationSecs(dotted)
            // "[Private video]" / "[Deleted video]" placeholders can't be downloaded.
            if (duration == null && title.startsWith("[") && title.endsWith("]")) continue
            if (e.str("live_status") == "is_live") continue
            out += VideoEntry(
                url = url,
                title = title,
                channel = (e.str("channel") ?: e.str("uploader"))?.trim(),
                durationSecs = duration,
                // hqdefault doubles as cover art; lists show the tiny variant (see listThumb).
                thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            )
        }
        return out
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

    /** Exact size when YouTube reports it, else bitrate × duration. */
    private fun JSONObject.sizeBytes(durationSecs: Long?): Long? {
        optLong("filesize", -1).takeIf { it > 0 }?.let { return it }
        optLong("filesize_approx", -1).takeIf { it > 0 }?.let { return it }
        val kbps = optDouble("abr", Double.NaN).takeIf { !it.isNaN() && it > 0 }
            ?: optDouble("tbr", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        return if (kbps != null && durationSecs != null) (kbps * 125 * durationSecs).toLong() else null
    }
}
