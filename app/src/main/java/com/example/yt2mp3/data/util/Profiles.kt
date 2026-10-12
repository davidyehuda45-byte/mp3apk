package com.example.yt2mp3.data.util

import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadProfile
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.PostOptions
import com.example.yt2mp3.domain.model.SubtitleMode
import com.example.yt2mp3.domain.model.VideoFormat
import com.example.yt2mp3.domain.model.VideoQuality
import org.json.JSONArray
import org.json.JSONObject

/** JSON encode/decode for named download profiles. Pure logic. */
object Profiles {
    fun encode(list: List<DownloadProfile>): String {
        val arr = JSONArray()
        for (p in list.take(20)) {
            arr.put(
                JSONObject()
                    .put("name", p.name.take(40))
                    .put("kind", p.mediaKind.name)
                    .put("aformat", p.audioFormat.name)
                    .put("aquality", p.audioQuality.bitrateKbps)
                    .put("vformat", p.videoFormat.name)
                    .put("vquality", p.videoQuality.name)
                    .put("sub", p.post.subtitleMode.name)
                    .put("langs", p.post.subLangs)
                    .put("chapters", p.post.embedChapters)
                    .put("sponsor", p.post.sponsorBlock)
                    .put("norm", p.post.normalizeAudio)
                    .put("thumb", p.post.saveThumbSeparate)
                    .put("strip", p.post.stripMetadata),
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<DownloadProfile> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DownloadProfile(
                    name = name,
                    mediaKind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.AUDIO),
                    audioFormat = AudioFormat.fromName(o.optString("aformat")),
                    audioQuality = AudioQuality.fromBitrate(o.optInt("aquality", AudioQuality.DEFAULT.bitrateKbps)),
                    videoFormat = VideoFormat.fromName(o.optString("vformat")),
                    videoQuality = VideoQuality.fromName(o.optString("vquality")),
                    post = PostOptions(
                        subtitleMode = SubtitleMode.fromName(o.optString("sub")),
                        subLangs = o.optString("langs", "en,id"),
                        embedChapters = o.optBoolean("chapters"),
                        sponsorBlock = o.optBoolean("sponsor"),
                        normalizeAudio = o.optBoolean("norm"),
                        saveThumbSeparate = o.optBoolean("thumb"),
                        stripMetadata = o.optBoolean("strip"),
                    ),
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** Public YouTube channel RSS (no login): feeds/videos.xml?channel_id=UC... Pure parsing. */
object RssFeed {
    data class RssItem(val videoId: String, val title: String)

    /** Accepts a UC channel id or a /channel/UC... URL. Returns the id or null. */
    fun channelIdFrom(input: String): String? {
        val t = input.trim()
        if (Regex("""^UC[A-Za-z0-9_-]{10,}$""").matches(t)) return t
        val m = Regex("""UC[A-Za-z0-9_-]{10,}""").find(t)
        return m?.value
    }

    fun feedUrl(channelId: String): String =
        "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    /** Parses atom feed XML into (videoId, title) pairs. */
    fun parse(xml: String): List<RssItem> {
        val out = mutableListOf<RssItem>()
        return try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
            val entries = doc.getElementsByTagNameNS("*", "entry")
            for (i in 0 until entries.length) {
                val e = entries.item(i) as? org.w3c.dom.Element ?: continue
                val idNodes = e.getElementsByTagNameNS("*", "videoId")
                val titleNodes = e.getElementsByTagNameNS("*", "title")
                val id = (0 until idNodes.length).firstNotNullOfOrNull {
                    idNodes.item(it).textContent?.trim()?.takeIf { s -> s.isNotEmpty() }
                } ?: continue
                val title = (0 until titleNodes.length).firstNotNullOfOrNull {
                    titleNodes.item(it).textContent?.trim()?.takeIf { s -> s.isNotEmpty() }
                } ?: id
                out += RssItem(id, title)
            }
            out
        } catch (t: Throwable) {
            out
        }
    }

    /** Network fetch on caller's dispatcher. Returns items or throws. */
    fun fetch(channelId: String): List<RssItem> {
        val conn = java.net.URL(feedUrl(channelId)).openConnection() as java.net.HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "YT2MP3")
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("RSS HTTP $code")
            val xml = conn.inputStream.use { it.readBytes().decodeToString() }
            return parse(xml)
        } finally {
            conn.disconnect()
        }
    }
}
