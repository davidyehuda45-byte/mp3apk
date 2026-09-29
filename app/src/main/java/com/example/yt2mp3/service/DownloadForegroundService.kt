package com.example.yt2mp3.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.domain.model.DownloadStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground Service so downloads survive screen-off (PRD §13).
 * Shows persistent notification, progress, cancel; cleans temp files via QueueRunner.
 */
class DownloadForegroundService : LifecycleService() {

    companion object {
        const val ACTION_START = "yt2mp3.action.START"
        const val ACTION_CANCEL = "yt2mp3.action.CANCEL"
        const val EXTRA_ID = "extra_id"
        const val TAG = "SERVICE"

        fun start(context: android.content.Context) {
            // Best-effort: starting from the background is rejected on
            // Android 12+ (ForegroundServiceStartNotAllowedException). The
            // queue pump still runs; it resumes under service on next launch.
            runCatching {
                val i = Intent(context, DownloadForegroundService::class.java).apply { action = ACTION_START }
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                else context.startService(i)
            }.onFailure {
                com.example.yt2mp3.data.util.AppLog.w(TAG, "service start rejected", it)
            }
        }
    }

    private var collector: Job? = null
    private var lastTitle = ""
    private var lastItemId = -1L

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val app = application as Yt2Mp3App
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                lifecycleScope.launch {
                    if (id >= 0) {
                        app.queueRunner.cancel(id, "dl_$id")
                        app.cancelDownload(id)
                    }
                }
                return START_NOT_STICKY
            }
            else -> { /* ACTION_START or null (restart) → fall through to kick */ }
        }

        startForegroundWith(getString(com.example.yt2mp3.R.string.notif_preparing), null, -1L)
        app.queueRunner.kick()

        collector?.cancel()
        collector = lifecycleScope.launch {
            app.queueRunner.events.collect { ev ->
                val item = app.downloads.get(ev.itemId)
                lastItemId = ev.itemId
                lastTitle = item?.title ?: lastTitle
                when {
                    ev.status.isActive || ev.status == DownloadStatus.DOWNLOADING || ev.status == DownloadStatus.QUEUED -> {
                        startForegroundWith(lastTitle.ifBlank { "YT2MP3" }, ev.percent, ev.itemId)
                    }
                    ev.status == DownloadStatus.COMPLETED -> {
                        notifyFinished(lastTitle, true)
                        if (!app.downloads.hasActive() && app.queueRunner.nextPending() == null) stopSelf()
                    }
                    ev.status == DownloadStatus.FAILED || ev.status == DownloadStatus.CANCELLED -> {
                        if (ev.status == DownloadStatus.FAILED) notifyFinished(lastTitle, false)
                        if (!app.downloads.hasActive() && app.queueRunner.nextPending() == null) stopSelf()
                    }
                }
            }
        }
        // Nothing to run (e.g. queue already drained): don't leave a stuck "Preparing" notification.
        lifecycleScope.launch {
            if (!app.downloads.hasActive() && app.queueRunner.nextPending() == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWith(title: String, percent: Int?, itemId: Long) {
        val n = DownloadNotifications.progress(this, title, percent, itemId)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    DownloadNotifications.NOTIF_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(DownloadNotifications.NOTIF_ID, n)
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "startForeground failed", t)
        }
    }

    private fun notifyFinished(title: String, ok: Boolean) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        mgr.notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            DownloadNotifications.finished(this, title.ifBlank { "YT2MP3" }, ok),
        )
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        collector?.cancel()
        super.onDestroy()
    }
}
