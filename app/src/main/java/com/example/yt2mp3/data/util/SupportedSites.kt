package com.example.yt2mp3.data.util

/**
 * Curated popular sites backed by the on-device yt-dlp engine.
 * The engine itself supports 1000+ sites generically — any public
 * http(s) link is accepted and passed to yt-dlp. This list is only
 * for UI hints (labels, “supported sites” sheet).
 *
 * Legal: public content you own or are permitted to keep only.
 * No DRM bypass, no private/login-only content, no watermark stripping.
 */
object SupportedSites {
    data class Site(val name: String, val hosts: List<String>, val hint: String)

    val popular = listOf(
        Site("YouTube", listOf("youtube.com", "youtu.be", "music.youtube.com"), "video, Shorts, live, playlist, podcast"),
        Site("YouTube Music", listOf("music.youtube.com"), "track, album, playlist"),
        Site("TikTok", listOf("tiktok.com", "vt.tiktok.com", "vm.tiktok.com"), "video publik"),
        Site("Instagram", listOf("instagram.com"), "Reels/post publik"),
        Site("Facebook", listOf("facebook.com", "fb.watch", "fb.com"), "video/Reels publik"),
        Site("X (Twitter)", listOf("x.com", "twitter.com"), "video/GIF publik"),
        Site("Reddit", listOf("reddit.com", "redd.it"), "video/audio publik"),
        Site("Pinterest", listOf("pinterest.com", "pin.it"), "video publik"),
        Site("Vimeo", listOf("vimeo.com"), "video publik"),
        Site("Dailymotion", listOf("dailymotion.com", "dai.ly"), "video publik"),
        Site("Twitch", listOf("twitch.tv"), "VOD & clip publik"),
        Site("SoundCloud", listOf("soundcloud.com"), "track, playlist, album"),
        Site("Bandcamp", listOf("bandcamp.com"), "track, album"),
        Site("Mixcloud", listOf("mixcloud.com"), "mix/show publik"),
        Site("Bilibili", listOf("bilibili.com", "b23.tv"), "video publik"),
        Site("Niconico", listOf("nicovideo.jp"), "video publik"),
        Site("VK", listOf("vk.com", "vkvideo.ru"), "video publik"),
        Site("LinkedIn", listOf("linkedin.com"), "video publik"),
        Site("Tumblr", listOf("tumblr.com"), "video/audio publik"),
        Site("Snapchat", listOf("snapchat.com"), "Spotlight publik"),
        Site("Likee", listOf("likee.video"), "video publik"),
        Site("Kwai", listOf("kwai.com", "kuaishou.com"), "video publik"),
        Site("Generic (yt-dlp)", listOf("lainnya"), "1000+ situs lain yang didukung yt-dlp"),
    )

    fun siteNameForUrl(url: String): String? {
        val host = runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull() ?: return null
        // YouTube subdomains collapse to "YouTube".
        if (host == "youtu.be" || host.endsWith(".youtu.be") || host.endsWith("youtube.com") || host == "youtube.com") {
            return "YouTube"
        }
        for (site in popular) {
            for (h in site.hosts) {
                if (h == "lainnya") continue
                if (host == h || host.endsWith(".$h")) return site.name
            }
        }
        // Unknown host: prettify domain (e.g. "example.com" -> "Example").
        val base = host.removePrefix("www.").substringBefore('.')
        return base.replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() }
    }

    /** Quick-pick chips on Home. Icon index maps to a Material icon in UI (no emoji, no brand assets). */
    data class QuickSite(val name: String, val hosts: List<String>, val icon: Int, val example: String)

    val quick = listOf(
        QuickSite("YouTube", listOf("youtube.com", "youtu.be", "music.youtube.com"), 0, "youtube.com/watch?v=… / youtu.be/…"),
        QuickSite("TikTok", listOf("tiktok.com"), 1, "tiktok.com/@akun/video/…"),
        QuickSite("Instagram", listOf("instagram.com"), 2, "instagram.com/reel/…"),
        QuickSite("Facebook", listOf("facebook.com", "fb.watch", "fb.com"), 3, "facebook.com/reel/… / fb.watch/…"),
        QuickSite("X", listOf("x.com", "twitter.com"), 4, "x.com/akun/status/…"),
        QuickSite("SoundCloud", listOf("soundcloud.com"), 5, "soundcloud.com/artis/judul"),
    )

    /** URLs in [text] belonging to this quick site (host match). */
    fun matchingUrls(site: QuickSite, text: String): List<String> {
        val out = LinkedHashSet<String>()
        val found = UrlValidator.extractAllSupportedUrls(text)
        for (u in found) {
            val host = runCatching { java.net.URI(u).host?.lowercase() }.getOrNull() ?: continue
            if (site.hosts.any { h -> host == h || host.endsWith(".$h") }) out += u
        }
        return out.toList()
    }
}
