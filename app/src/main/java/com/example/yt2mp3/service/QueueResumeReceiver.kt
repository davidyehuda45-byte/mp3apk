package com.example.yt2mp3.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.data.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Resumes the persistent queue after reboot. Best-effort: on Android 12+
 * a foreground-service start from the background may be rejected, in which
 * case the pump still runs until the process is reclaimed and resumes
 * again on next app launch.
 */
class QueueResumeReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as Yt2Mp3App
                if (app.downloads.hasActive() || app.downloads.nextQueued() != null) {
                    app.queueRunner.kick()
                    DownloadForegroundService.start(context)
                }
            } catch (t: Throwable) {
                AppLog.w("SERVICE", "resume after boot failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
