package com.example.yt2mp3

import android.app.Application
import com.example.yt2mp3.data.engine.YtDlpEngineImpl
import com.example.yt2mp3.di.AppContainer
import com.example.yt2mp3.service.DownloadForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Yt2Mp3App : Application() {
    lateinit var container: AppContainer
        private set

    val downloads get() = container.downloads
    val settings get() = container.settings
    val queueRunner get() = container.queueRunner
    val metadata get() = container.metadataRepo
    val startDownload get() = container.startDownload
    val retryDownload get() = container.retryDownload
    val updateEngine get() = container.updateEngine
    val engineManager get() = container.engineManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch {
            runCatching { container.downloads.requeueInterrupted() }
            // Unpack Python/FFmpeg off the UI thread so the first Preview is fast.
            runCatching { (container.engine as? YtDlpEngineImpl)?.warmUp() }
            runCatching { autoUpdateEngineIfDue() }
            if (container.downloads.hasActive() || container.downloads.nextQueued() != null) {
                container.queueRunner.kick()
                DownloadForegroundService.start(this@Yt2Mp3App)
            }
        }
    }

    /**
     * Bundled yt-dlp goes stale within months and YouTube then rejects it.
     * Checks at most once per ~day (one small GitHub API call) and only
     * downloads (~3 MB) when a new release exists. Failure keeps the current engine.
     */
    private suspend fun autoUpdateEngineIfDue() {
        val now = System.currentTimeMillis()
        if (now - container.settings.getLastEngineCheck() < ENGINE_CHECK_INTERVAL_MS) return
        val mgr = container.engineManager
        val latest = mgr.checkLatest().getOrNull() ?: return // retry on next launch
        container.settings.setLastEngineCheck(now)
        if (mgr.currentVersion() != latest) mgr.updateToLatest()
    }

    suspend fun cancelDownload(id: Long) = container.cancelDownload.execute(id)

    companion object {
        private const val ENGINE_CHECK_INTERVAL_MS = 20 * 60 * 60 * 1000L
    }
}
