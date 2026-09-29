package com.example.yt2mp3

import com.example.yt2mp3.data.engine.YtJson
import com.example.yt2mp3.data.util.InputParser
import com.example.yt2mp3.data.util.ParsedInput
import com.example.yt2mp3.data.util.UrlValidator
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.SizeEstimator
import com.example.yt2mp3.domain.model.TimeFormat
import com.example.yt2mp3.domain.model.VideoMetadata
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String): String =
    requireNotNull(FeatureTests::class.java.getResource("/ytdlp/$name")) { "missing fixture $name" }.readText()

class FeatureTests {
    // ------------------------------------------------------------ InputParser

    @Test fun parsesVideoLinks() {
        assertEquals(
            ParsedInput.Video("https://www.youtube.com/watch?v=k08A4cjVI8k"),
            InputParser.parse("https://youtu.be/k08A4cjVI8k?si=qhq-OMuw9WyRM7uQ"),
        )
        // Typed without scheme.
        assertEquals(
            ParsedInput.Video("https://www.youtube.com/watch?v=k08A4cjVI8k"),
            InputParser.parse("youtu.be/k08A4cjVI8k"),
        )
        assertEquals(
            ParsedInput.Video("https://www.youtube.com/watch?v=k08A4cjVI8k"),
            InputParser.parse("m.youtube.com/watch?v=k08A4cjVI8k"),
        )
    }

    @Test fun videoInsidePlaylistStaysVideo() {
        val p = InputParser.parse("https://www.youtube.com/watch?v=k08A4cjVI8k&list=RDk08A4cjVI8k&index=2")
        assertTrue(p is ParsedInput.Video)
        assertEquals(
            "https://www.youtube.com/watch?v=k08A4cjVI8k&list=RDk08A4cjVI8k",
            UrlValidator.playlistOfVideo((p as ParsedInput.Video).url),
        )
    }

    @Test fun parsesPlaylistLinks() {
        assertEquals(
            ParsedInput.Playlist("https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"),
            InputParser.parse("https://youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG&si=abc"),
        )
    }

    @Test fun textIsSearch() {
        assertEquals(ParsedInput.Search("tuhan memanggil namamu"), InputParser.parse("  tuhan memanggil namamu "))
        assertEquals(ParsedInput.Search("Mr.Bean theme"), InputParser.parse("Mr.Bean theme"))
        assertEquals(ParsedInput.Empty, InputParser.parse("   "))
    }

    @Test fun otherLinksAreUnsupported() {
        assertEquals(ParsedInput.Unsupported, InputParser.parse("https://example.com/video"))
        assertEquals(ParsedInput.Unsupported, InputParser.parse("https://www.youtube.com/@somechannel"))
        assertEquals(ParsedInput.Unsupported, InputParser.parse("www.tiktok.com/@x/video/1"))
    }

    // ------------------------------------------------------------- TimeFormat

    @Test fun parsesTimes() {
        assertEquals(90, TimeFormat.parse("90"))
        assertEquals(90, TimeFormat.parse("1:30"))
        assertEquals(90, TimeFormat.parse("1.30"))
        assertEquals(3723, TimeFormat.parse("1:02:03"))
        assertNull(TimeFormat.parse("1:75"))
        assertNull(TimeFormat.parse("abc"))
        assertNull(TimeFormat.parse("1::2"))
        assertEquals("2:46", TimeFormat.format(166))
        assertEquals("1:02:03", TimeFormat.format(3723))
    }

    @Test fun validatesTrim() {
        assertEquals(TimeFormat.TrimResult.Ok(60, 150), TimeFormat.validateTrim("1:00", "2:30", 166))
        assertEquals(TimeFormat.TrimResult.Ok(null, 30), TimeFormat.validateTrim("", "0:30", 166))
        assertEquals(TimeFormat.TrimResult.Ok(60, null), TimeFormat.validateTrim("1:00", "", 166))
        // End == duration means "until the end".
        assertEquals(TimeFormat.TrimResult.Ok(60, null), TimeFormat.validateTrim("1:00", "2:46", 166))
        assertEquals(TimeFormat.TrimResult.Ok(null, null), TimeFormat.validateTrim("", "", 166))
        assertTrue(TimeFormat.validateTrim("2:00", "1:00", 166) is TimeFormat.TrimResult.Invalid)
        assertTrue(TimeFormat.validateTrim("0:10", "5:00", 166) is TimeFormat.TrimResult.Invalid)
        assertTrue(TimeFormat.validateTrim("3:00", "", 166) is TimeFormat.TrimResult.Invalid)
        assertTrue(TimeFormat.validateTrim("x", "", 166) is TimeFormat.TrimResult.Invalid)
    }

    // ---------------------------------------------------- yt-dlp JSON (real)

    @Test fun parsesRealPreviewJson() {
        val url = "https://www.youtube.com/watch?v=k08A4cjVI8k"
        val m = YtJson.metadata(url, JSONObject(fixture("info_k08A4cjVI8k.json")))
        assertTrue(m.title.startsWith("Pujian Sekolah Minggu"))
        assertEquals("Griya Kasih Nusantara Ministry", m.artist)
        assertEquals(166L, m.durationSecs)
        assertEquals("https://i.ytimg.com/vi/k08A4cjVI8k/hqdefault.jpg", m.thumbnailUrl)
        assertEquals(2749355L, m.bestAudioBytes)
        // Format 140 (AAC ~130 kbps), not the 48 kbps format 139.
        assertEquals(2686539L, m.m4aBytes)
        assertNull(m.playlistUrl)
    }

    @Test fun editedTagsGoIntoInfoJson() {
        val patched = JSONObject(YtJson.withEditedTags(fixture("info_k08A4cjVI8k.json"), "Judul Baru", "Artis Baru"))
        assertEquals("Judul Baru", patched.getString("meta_title"))
        assertEquals("Artis Baru", patched.getString("meta_artist"))
        val noArtist = JSONObject(YtJson.withEditedTags(fixture("info_k08A4cjVI8k.json"), "T", " "))
        assertFalse(noArtist.has("meta_artist"))
    }

    @Test fun parsesRealSearchJson() {
        val entries = YtJson.entries(JSONObject(fixture("search_single.json")))
        assertEquals(5, entries.size)
        entries.forEach {
            assertTrue(it.url, it.url.matches(Regex("""https://www\.youtube\.com/watch\?v=[\w-]{11}""")))
            assertTrue(it.title.isNotBlank())
            assertTrue(it.thumbnailUrl!!.endsWith("/hqdefault.jpg"))
        }
    }

    @Test fun parsesRealPlaylistJsonAndSkipsPlaceholders() {
        val json = JSONObject(fixture("playlist_mix.json"))
        val real = YtJson.entries(json)
        assertEquals(6, real.size)
        assertTrue(YtJson.title(json)!!.startsWith("Mix"))

        json.getJSONArray("entries").put(
            JSONObject().put("id", "aaaaaaaaaaa").put("title", "[Private video]").put("duration", JSONObject.NULL),
        )
        assertEquals(6, YtJson.entries(json).size)
    }

    @Test fun fixesDottedLocaleDurationsInSearchResults() {
        // lang=id → YouTube shows "3.45"/"3.00"; yt-dlp turns those into 3.45 and 3.0 seconds.
        fun entry(id: String, d: Double) = JSONObject().put("id", id).put("title", "t").put("duration", d)
        val dotted = JSONObject().put("entries", org.json.JSONArray()
            .put(entry("aaaaaaaaaaa", 3.45)).put(entry("bbbbbbbbbbb", 3.0)).put(entry("ccccccccccc", 0.05)))
        assertEquals(listOf(225L, 180L, 5L), YtJson.entries(dotted).map { it.durationSecs })

        val normal = JSONObject().put("entries", org.json.JSONArray().put(entry("aaaaaaaaaaa", 225.0)))
        assertEquals(listOf(225L), YtJson.entries(normal).map { it.durationSecs })
    }

    // ---------------------------------------------------------- SizeEstimator

    private val meta = VideoMetadata(
        sourceUrl = "https://www.youtube.com/watch?v=k08A4cjVI8k",
        title = "t", artist = null, durationSecs = 166, thumbnailUrl = null,
        bestAudioBytes = 2_749_355, m4aBytes = 2_686_539,
    )

    @Test fun estimatesMp3AndM4a() {
        val mp3 = SizeEstimator.forVideo(meta, AudioFormat.MP3, AudioQuality.Q192)!!
        assertEquals(2_749_355L, mp3.downloadBytes)
        assertEquals(166L * 192 * 125, mp3.fileBytes)
        assertTrue(mp3.exact)

        val m4a = SizeEstimator.forVideo(meta, AudioFormat.M4A, AudioQuality.Q320)!!
        assertEquals(2_686_539L, m4a.downloadBytes)
        assertEquals(m4a.downloadBytes, m4a.fileBytes)
    }

    @Test fun estimateScalesWithTrim() {
        val half = SizeEstimator.forVideo(meta, AudioFormat.M4A, AudioQuality.Q192, 0, 83)!!
        assertEquals(2_686_539L / 2, half.downloadBytes, 2)
        // Trimmed MP3 downloads the M4A stream too.
        val mp3 = SizeEstimator.forVideo(meta, AudioFormat.MP3, AudioQuality.Q192, 65, 100)!!
        assertEquals(2_686_539L * 35 / 166, mp3.downloadBytes, 2)
        assertEquals(35L * 192 * 125, mp3.fileBytes)
    }

    @Test fun estimateFallsBackToDuration() {
        val noSize = meta.copy(bestAudioBytes = null, m4aBytes = null)
        val e = SizeEstimator.forVideo(noSize, AudioFormat.MP3, AudioQuality.Q128)!!
        assertFalse(e.exact)
        assertEquals(166L * 130 * 125, e.downloadBytes)
        assertNull(SizeEstimator.forVideo(noSize.copy(durationSecs = null), AudioFormat.MP3, AudioQuality.Q128))
    }

    private fun assertEquals(expected: Long, actual: Long, delta: Long) =
        assertTrue("expected $expected ± $delta but was $actual", kotlin.math.abs(expected - actual) <= delta)
}
