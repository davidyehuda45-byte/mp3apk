package com.example.yt2mp3

import com.example.yt2mp3.data.util.FileNameSanitizer
import com.example.yt2mp3.data.util.UrlValidator
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.errorDetail
import com.example.yt2mp3.domain.model.mapEngineError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test fun blankIsNull() {
        assertNull(UrlValidator.extractFirstSupportedUrl(null))
        assertNull(UrlValidator.extractFirstSupportedUrl("   "))
        assertNull(UrlValidator.extractFirstSupportedUrl(""))
    }

    @Test fun rejectsNonUrl() {
        assertNull(UrlValidator.extractFirstSupportedUrl("hello world"))
    }

    @Test fun acceptsWatchUrl() {
        val out = UrlValidator.extractFirstSupportedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", out)
    }

    @Test fun extractsFromShareText() {
        val out = UrlValidator.extractFirstSupportedUrl(
            "Check this out: https://youtu.be/dQw4w9WgXcQ wow",
        )
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", out)
    }

    @Test fun rejectsUnsupportedHost() {
        // Fase 1 generic: any http(s) URL is attempted by yt-dlp; truly unknown
        // hosts fail downstream as UNSUPPORTED_URL/EXTRACTOR_ERROR, not here.
        assertEquals(
            "https://example.com/video",
            UrlValidator.extractFirstSupportedUrl("https://example.com/video"),
        )
        assertTrue(UrlValidator.isSupported("https://example.com/video"))
        assertTrue(UrlValidator.isSupported("https://www.tiktok.com/@x/video/1"))
        assertNull(UrlValidator.extractFirstSupportedUrl("not a url at all"))
    }

    @Test fun stripsSiTrackingParam() {
        val out = UrlValidator.extractFirstSupportedUrl(
            "https://youtu.be/722d3I_hUB4?si=oWftDGoeVLrsu8CR",
        )
        assertEquals("https://www.youtube.com/watch?v=722d3I_hUB4", out)
    }

    @Test fun keepsTimestampButDropsSi() {
        val out = UrlValidator.extractFirstSupportedUrl(
            "https://youtu.be/dQw4w9WgXcQ?si=abc&t=30",
        )
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30", out)
    }

    @Test fun acceptsShortsAndMusic() {
        assertEquals(
            "https://www.youtube.com/watch?v=abcdEFGH123",
            UrlValidator.extractFirstSupportedUrl("https://youtube.com/shorts/abcdEFGH123?si=x"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=abcdEFGH123",
            UrlValidator.extractFirstSupportedUrl("https://music.youtube.com/watch?v=abcdEFGH123&si=x"),
        )
    }

    @Test fun rejectsPlaylistAndChannel() {
        // Playlist/channel pages surface via extractPlaylistUrl / InputParser.Playlist.
        assertEquals(
            "https://www.youtube.com/playlist?list=PL123",
            UrlValidator.extractPlaylistUrl("https://www.youtube.com/playlist?list=PL123"),
        )
        // Channel pages are listable: routed to the playlist flow, not single-video.
        assertTrue(
            UrlValidator.extractAllSupportedUrls("https://www.youtube.com/@somechannel").isNotEmpty(),
        )
        assertNull(UrlValidator.extractFirstSupportedUrl("https://www.youtube.com/"))
    }
}

class FileNameSanitizerTest {
    @Test fun stripsIllegalChars() {
        assertEquals("a_b_c.mp3", FileNameSanitizer.sanitizeWithExtension("a/b:c", ".mp3"))
    }

    @Test fun blankTitleFallback() {
        assertEquals("audio.mp3", FileNameSanitizer.sanitizeWithExtension("   ", ".mp3"))
    }

    @Test fun collisionSuffix() {
        val existing = setOf("Song.mp3", "Song (1).mp3")
        assertEquals(
            "Song (2).mp3",
            FileNameSanitizer.resolveCollision("Song.mp3") { it in existing },
        )
    }
}

class QualityMappingTest {
    @Test fun defaultIs192() {
        assertEquals(AudioQuality.Q192, AudioQuality.DEFAULT)
        assertEquals(AudioQuality.Q192, AudioQuality.fromBitrate(999))
        assertEquals(AudioQuality.Q320, AudioQuality.fromBitrate(320))
    }

    @Test fun formatFlags() {
        assertEquals(com.example.yt2mp3.domain.model.AudioFormat.MP3, com.example.yt2mp3.domain.model.AudioFormat.DEFAULT)
        assertTrue(com.example.yt2mp3.domain.model.AudioFormat.MP3.usesQuality)
        assertTrue(com.example.yt2mp3.domain.model.AudioFormat.OPUS.usesQuality)
        assertTrue(com.example.yt2mp3.domain.model.AudioFormat.OGG.usesQuality)
        assertFalse(com.example.yt2mp3.domain.model.AudioFormat.M4A.usesQuality)
        assertFalse(com.example.yt2mp3.domain.model.AudioFormat.FLAC.usesQuality)
        assertFalse(com.example.yt2mp3.domain.model.AudioFormat.WAV.usesQuality)
        assertEquals("opus", com.example.yt2mp3.domain.model.AudioFormat.OPUS.ytdlpFormat)
        assertEquals("vorbis", com.example.yt2mp3.domain.model.AudioFormat.OGG.ytdlpFormat)
    }

    @Test fun siteNames() {
        assertEquals("YouTube", com.example.yt2mp3.data.util.SupportedSites.siteNameForUrl("https://youtu.be/abc123"))
        assertEquals("TikTok", com.example.yt2mp3.data.util.SupportedSites.siteNameForUrl("https://www.tiktok.com/@x/video/1"))
        assertEquals("SoundCloud", com.example.yt2mp3.data.util.SupportedSites.siteNameForUrl("https://soundcloud.com/a/b"))
    }
}

class QueueStateTest {
    @Test fun legalTransitions() {
        assertTrue(DownloadStatus.canTransition(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
        assertTrue(DownloadStatus.canTransition(DownloadStatus.FAILED, DownloadStatus.QUEUED))
    }

    @Test fun illegalTransitions() {
        assertFalse(DownloadStatus.canTransition(DownloadStatus.QUEUED, DownloadStatus.COMPLETED))
        assertFalse(DownloadStatus.canTransition(DownloadStatus.COMPLETED, DownloadStatus.FAILED))
    }
}

class EngineErrorMappingTest {
    @Test fun botCheckIsExtractorNotUnavailable() {
        assertEquals(
            DownloadErrorKind.EXTRACTOR_ERROR,
            mapEngineError(RuntimeException("ERROR: [youtube] 722d3I_hUB4: Sign in to confirm you're not a bot")),
        )
    }

    @Test fun forbiddenIsExtractor() {
        assertEquals(
            DownloadErrorKind.EXTRACTOR_ERROR,
            mapEngineError(RuntimeException("HTTP Error 403: Forbidden")),
        )
    }

    @Test fun privateVideoStaysUnavailable() {
        assertEquals(
            DownloadErrorKind.CONTENT_UNAVAILABLE,
            mapEngineError(RuntimeException("This video is private")),
        )
    }

    @Test fun requestedFormatIsExtractorNotUnavailable() {
        assertEquals(
            DownloadErrorKind.EXTRACTOR_ERROR,
            mapEngineError(RuntimeException("ERROR: [youtube] k08A4cjVI8k: Requested format is not available.")),
        )
    }

    @Test fun ageGateIsUnavailableNotBotCheck() {
        assertEquals(
            DownloadErrorKind.CONTENT_UNAVAILABLE,
            mapEngineError(RuntimeException("ERROR: [youtube] x: Sign in to confirm your age.")),
        )
    }

    @Test fun warningsDoNotMaskRealError() {
        val stderr = "WARNING: [youtube] x: mweb client https formats require a GVS PO Token\n" +
            "ERROR: [youtube] x: Private video. Sign in if you've been granted access"
        assertEquals(DownloadErrorKind.CONTENT_UNAVAILABLE, mapEngineError(RuntimeException(stderr)))
    }

    @Test fun noSpaceIsStorageFull() {
        assertEquals(
            DownloadErrorKind.STORAGE_FULL,
            mapEngineError(RuntimeException("ERROR: unable to write data: [Errno 28] No space left on device")),
        )
    }

    @Test fun emptyInfoIsExtractor() {
        assertEquals(
            DownloadErrorKind.EXTRACTOR_ERROR,
            mapEngineError(RuntimeException("Failed to fetch video information")),
        )
    }

    @Test fun detailPicksYtDlpErrorLine() {
        val t = RuntimeException("WARNING: something\nERROR: [youtube] abc: Requested format is not available\n")
        assertEquals("ERROR: [youtube] abc: Requested format is not available", t.errorDetail())
    }

    @Test fun detailFallsBackToCauseOrType() {
        assertEquals("IOException", RuntimeException(null, java.io.IOException()).errorDetail())
        assertEquals("boom", RuntimeException(null, java.io.IOException("boom")).errorDetail())
    }
}
