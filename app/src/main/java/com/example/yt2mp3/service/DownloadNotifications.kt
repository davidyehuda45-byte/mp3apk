package com.example.yt2mp3.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.yt2mp3.MainActivity
import com.example.yt2mp3.R
import com.example.yt2mp3.domain.model.DownloadStatus

object DownloadNotifications {
    const val CHANNEL_ID = "yt2mp3_downloads"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.notif_channel_desc) },
            )
        }
    }

    fun contentIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancelIntent(context: Context, itemId: Long): PendingIntent {
        val i = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_CANCEL
            putExtra(DownloadForegroundService.EXTRA_ID, itemId)
        }
        return PendingIntent.getService(
            context, itemId.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun progress(
        context: Context,
        title: String,
        percent: Int?,
        itemId: Long,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notif_downloading))
            .setContentText(title)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.cancel),
                cancelIntent(context, itemId),
            )
        if (percent != null) builder.setProgress(100, percent, false)
        else builder.setProgress(0, 0, true)
        return builder.build()
    }

    fun finished(context: Context, title: String, ok: Boolean): Notification {
        val text = if (ok) context.getString(R.string.notif_complete, title)
        else context.getString(R.string.notif_failed, title)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (ok) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
    }

    fun statusText(status: DownloadStatus, stage: String?): String = when (status) {
        DownloadStatus.QUEUED -> "Waiting"
        DownloadStatus.DOWNLOADING -> stage ?: "Downloading…"
        DownloadStatus.CONVERTING -> "Converting"
        DownloadStatus.SAVING -> "Saving"
        DownloadStatus.COMPLETED -> "Completed"
        DownloadStatus.FAILED -> "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }
}
