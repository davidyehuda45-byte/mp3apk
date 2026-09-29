package com.example.yt2mp3.data.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.yt2mp3.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val version: String, val apkUrl: String, val notes: String?)

/**
 * Self-update for the sideloaded build: reads the latest GitHub release, downloads the APK
 * matching this phone's ABI and hands it to the system installer (user taps "Install" once).
 */
object AppUpdater {
    private const val LATEST = "https://api.github.com/repos/davidyehuda45-byte/mp3apk/releases/latest"

    /** Newer release than the installed one, or null (also on any network/parse error). */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(get(LATEST).bufferedReader().use { it.readText() })
            val version = json.optString("tag_name").removePrefix("v").trim()
            if (!isNewer(version, BuildConfig.VERSION_NAME)) return@runCatching null
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            val apks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk") }
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val apk = apks.firstOrNull { abi.isNotEmpty() && it.optString("name").contains(abi) }
                ?: apks.firstOrNull { "universal" in it.optString("name") }
                ?: apks.singleOrNull()
                ?: return@runCatching null
            AppUpdate(version, apk.getString("browser_download_url"), json.optString("body").takeIf { it.isNotBlank() })
        }.getOrNull()
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun download(context: Context, update: AppUpdate, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
            val out = File(dir, "update.apk")
            val conn = open(update.apkUrl)
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        onProgress(total?.let { done.toFloat() / it })
                    }
                }
            }
            out
        }

    /** Opens the system installer; Android asks for "install unknown apps" the first time. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun get(url: String) = open(url, "application/vnd.github+json").inputStream

    private fun open(url: String, accept: String = "*/*"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "YT2MP3/${BuildConfig.VERSION_NAME}")
            if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
        }
}
