package com.example.yt2mp3.data.engine

import android.content.Context
import com.example.yt2mp3.data.util.AppLog
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Single configuration point for engine updates (PRD §34). No scattered hard-coded URLs.
 * Update flow: check → download → validate → atomic replace → rollback (PRD §33).
 *
 * The actual binary swap delegates to the bundled library updater
 * (`YoutubeDL.updateYoutubeDL`), which downloads the new binary to temp,
 * replaces atomically, and restores the bundled default version if install
 * fails — so a failed update never leaves the app without a working engine.
 */
data class EngineUpdateConfig(
    /** Official yt-dlp releases API (same feed the STABLE channel uses). HTTPS only (PRD §34). */
    val releasesApiUrl: String = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest",
)

class EngineVersionManager(
    private val appContext: Context,
    private val config: EngineUpdateConfig = EngineUpdateConfig(),
) {
    suspend fun currentVersion(): String? = withContext(Dispatchers.IO) {
        runCatching { com.yausername.youtubedl_android.YoutubeDL.getInstance().version(appContext) }
            .getOrNull()
    }

    suspend fun checkLatest(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(config.releasesApiUrl).openConnection() as HttpsURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val body = conn.inputStream.bufferedReader().readText()
                val tag = JSONObject(body).optString("tag_name", "")
                if (tag.isBlank()) error("no tag_name in response")
                tag
            } finally {
                conn.disconnect()
            }
        }.onFailure { AppLog.e("UPDATE", "check failed", it) }
    }

    /**
     * Returns true when an update is available. Null current version counts
     * as "unknown — check only", never auto-install.
     */
    suspend fun isUpdateAvailable(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val latest = checkLatest().getOrThrow()
            val current = currentVersion()
            current != null && current != latest
        }
    }

    /**
     * Installs the latest stable yt-dlp binary. Safe-fail: the bundled updater
     * restores the default engine if replacement fails (PRD §33 rollback).
     */
    suspend fun updateToLatest(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Write lock: waits for running previews/downloads, blocks new ones until swapped.
            val status = EngineLock.rw.write {
                com.yausername.youtubedl_android.YoutubeDL.getInstance()
                    .updateYoutubeDL(
                        appContext,
                        com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.STABLE,
                    )
            }
            AppLog.i("UPDATE", "engine update status=$status")
            currentVersion() ?: "unknown"
        }.onFailure {
            // Bundled updater already rolled back to the default engine on failure.
            AppLog.e("UPDATE", "update failed, engine keeps previous working version", it)
        }
    }
}
