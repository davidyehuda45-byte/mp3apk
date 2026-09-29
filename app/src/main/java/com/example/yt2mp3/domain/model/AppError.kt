package com.example.yt2mp3.domain.model

/** User-facing error categories (PRD §31.9). */
enum class DownloadErrorKind {
    INVALID_URL,
    UNSUPPORTED_URL,
    CONTENT_UNAVAILABLE,
    NETWORK_ERROR,
    EXTRACTOR_ERROR,
    CONVERSION_ERROR,
    STORAGE_ERROR,
    STORAGE_FULL,
    PERMISSION_REVOKED,
    CANCELLED,
    UNKNOWN,
}

data class AppError(val kind: DownloadErrorKind, val message: String, val cause: Throwable? = null)

fun AppError.userMessage(): String = when (kind) {
    DownloadErrorKind.INVALID_URL -> "URL tidak valid. Periksa kembali link yang dimasukkan."
    DownloadErrorKind.UNSUPPORTED_URL ->
        "Link ini tidak didukung. Pakai link satu video YouTube (youtube.com / youtu.be), bukan playlist atau channel."
    DownloadErrorKind.CONTENT_UNAVAILABLE ->
        "Konten tidak dapat diakses oleh engine download. Konten privat/dibatasi atau telah dihapus."
    DownloadErrorKind.NETWORK_ERROR -> "Tidak ada koneksi internet. Periksa koneksi dan coba lagi."
    DownloadErrorKind.STORAGE_ERROR -> "Folder penyimpanan tidak tersedia. Pilih folder lain."
    DownloadErrorKind.PERMISSION_REVOKED ->
        "Akses ke folder penyimpanan telah dicabut. Pilih folder baru."
    DownloadErrorKind.STORAGE_FULL -> "Ruang penyimpanan tidak cukup."
    DownloadErrorKind.CONVERSION_ERROR -> "Konversi audio gagal. Coba lagi."
    DownloadErrorKind.EXTRACTOR_ERROR ->
        "YouTube menolak permintaan atau engine perlu diperbarui. Buka Settings → Update engine, lalu Try again."
    DownloadErrorKind.CANCELLED -> "Download dibatalkan."
    DownloadErrorKind.UNKNOWN -> "Terjadi kesalahan. Coba lagi."
}

/** Maps raw yt-dlp/FFmpeg/storage exceptions to categories. */
fun mapEngineError(t: Throwable): DownloadErrorKind {
    val raw = listOfNotNull(t.message, t.cause?.message).joinToString("\n")
    // yt-dlp stderr mixes WARNING lines (which mention "extractor", "PO token"
    // etc. even on success) with the real ERROR line; classify on ERROR only.
    val errorLines = raw.lines().filter { it.trim().startsWith("ERROR", ignoreCase = true) }
    val combined = (if (errorLines.isNotEmpty()) errorLines.joinToString(" ") else raw).lowercase() +
        " " + t::class.java.simpleName.lowercase() + " " + (t.cause?.let { it::class.java.simpleName.lowercase() } ?: "")
    // Bot-check / extractor failure FIRST — must not be mislabeled as private content.
    if ("not a bot" in combined || "po token" in combined || "player response" in combined ||
        "captcha" in combined || ("bot" in combined && "sign" in combined) ||
        "http error 403" in combined || "forbidden" in combined ||
        "http error 429" in combined || "too many requests" in combined ||
        "requested format" in combined || "page needs to be reloaded" in combined ||
        "no video formats" in combined || "no video format" in combined ||
        "failed to fetch video information" in combined ||
        "unable to parse video information" in combined ||
        "unable to extract" in combined || "nsig" in combined || "signature" in combined ||
        "extractor" in combined || ("json" in combined && "parse" in combined)
    ) return DownloadErrorKind.EXTRACTOR_ERROR
    return when {
        "unsupported url" in combined -> DownloadErrorKind.UNSUPPORTED_URL
        "enospc" in combined || "no space" in combined -> DownloadErrorKind.STORAGE_FULL
        "private" in combined || "login" in combined || "sign in" in combined ||
            "log in" in combined || ("age" in combined && "confirm" in combined) ||
            "not available" in combined || "unavailable" in combined ||
            "removed" in combined || "deleted" in combined || "members-only" in combined ||
            "drm" in combined -> DownloadErrorKind.CONTENT_UNAVAILABLE
        "unable to download" in combined || "network" in combined || "timeout" in combined ||
            "timed out" in combined || "connection" in combined || "unknownhost" in combined ||
            "unreachable" in combined || "resolve" in combined ||
            "connectexception" in combined || "sockettimeout" in combined ||
            ("ssl" in combined && "exception" in combined) ||
            "http error 5" in combined || "bad gateway" in combined ||
            "service unavailable" in combined -> DownloadErrorKind.NETWORK_ERROR
        "ffmpeg" in combined || "convert" in combined -> DownloadErrorKind.CONVERSION_ERROR
        "permission" in combined || "securityexception" in combined -> DownloadErrorKind.PERMISSION_REVOKED
        "bot" in combined -> DownloadErrorKind.EXTRACTOR_ERROR
        else -> DownloadErrorKind.UNKNOWN
    }
}

/**
 * Short raw reason for display: yt-dlp's "ERROR:" line if present, else the
 * last non-blank line of the message (or cause), else the exception type.
 */
fun Throwable.errorDetail(maxLen: Int = 300): String {
    val raw = listOfNotNull(message, cause?.message).joinToString("\n")
    val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val line = lines.lastOrNull { it.startsWith("ERROR", ignoreCase = true) }
        ?: lines.lastOrNull()
        ?: (cause ?: this)::class.java.simpleName
    return if (line.length > maxLen) line.take(maxLen) + "…" else line
}

/**
 * Converts any throwable into a [DownloadErrorKind].
 * Preserves kinds already encoded as `Exception(KIND.name)`; otherwise maps
 * the raw engine message via [mapEngineError] so the UI never mislabels
 * a network/extractor failure as "unsupported URL".
 */
fun Throwable.toDownloadErrorKind(): DownloadErrorKind {
    val name = message?.trim()
    if (!name.isNullOrEmpty()) {
        runCatching { return DownloadErrorKind.valueOf(name.substringBefore(':').trim()) }
    }
    return mapEngineError(this)
}
