package com.example.yt2mp3.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.data.util.RssFeed
import com.example.yt2mp3.domain.model.DownloadRequest
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Daily scheduler (e.g. 02:00) via inexact AlarmManager. No exact-alarm permission needed. */
object ScheduleManager {
    private const val REQ = 4001

    fun rescheduleIfEnabled(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val app = context.applicationContext as Yt2Mp3App
            val (enabled, time) = runCatching { app.settings.getSchedule() }.getOrDefault(Pair(false, Pair(2, 0)))
            val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(context)
            mgr.cancel(pi)
            if (!enabled) return@launch
            val (hour, min) = time
            val first = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, min.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            runCatching {
                mgr.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    first,
                    AlarmManager.INTERVAL_DAY,
                    pi,
                )
            }.onFailure { AppLog.w("SCHED", "schedule failed", it) }
        }
    }

    fun cancel(context: Context) {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mgr.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val i = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_RUN
        }
        return PendingIntent.getBroadcast(
            context, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Fires the queue (and optional public RSS auto-check) on schedule. */
class ScheduleReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as Yt2Mp3App
                // Public RSS auto-download (no login): enqueue new channel videos.
                if (runCatching { app.settings.getRssAuto() }.getOrDefault(false)) {
                    runCatching { rssCheck(app) }.onFailure {
                        AppLog.w("SCHED", "rss check failed", it)
                    }
                }
                if (app.downloads.hasActive() || app.downloads.nextQueued() != null) {
                    app.queueRunner.kick()
                    DownloadForegroundService.start(context)
                }
            } catch (t: Throwable) {
                AppLog.w("SCHED", "scheduled run failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN = "com.example.yt2mp3.action.SCHEDULED_RUN"

        /** Shared RSS check: public feed only, max 5 new videos. Returns enqueued count. */
        suspend fun rssCheck(app: Yt2Mp3App): Int {
            val raw = app.settings.getRssChannel()
            val id = RssFeed.channelIdFrom(raw) ?: throw IllegalArgumentException("Channel RSS tidak valid.")
            val items = RssFeed.fetch(id).take(20)
            if (items.isEmpty()) return 0
            val urls = items.map { "https://www.youtube.com/watch?v=${it.videoId}" }
            val known = app.downloads.existingUrls(urls)
            val fresh = items.filterNot { "https://www.youtube.com/watch?v=${it.videoId}" in known }.take(5)
            if (fresh.isEmpty()) return 0
            val kind = app.settings.observeMediaKind()
            // Defaults snapshot for automation enqueues.
            val reqs = fresh.map {
                DownloadRequest(
                    sourceUrl = "https://www.youtube.com/watch?v=${it.videoId}",
                    title = it.title,
                    artist = null,
                    thumbnailUrl = "https://i.ytimg.com/vi/${it.videoId}/hqdefault.jpg",
                    quality = app.settings.getDefaultQuality(),
                    format = runCatching { app.settings.observeDefaultFormat().first() }
                        .getOrDefault(com.example.yt2mp3.domain.model.AudioFormat.DEFAULT),
                    mediaKind = runCatching { kind.first() }
                        .getOrDefault(com.example.yt2mp3.domain.model.MediaKind.AUDIO),
                    post = app.settings.getPostFlags(),
                )
            }
            var n = 0
            app.startDownload.executeAll(reqs).onSuccess { n = it.size }
            return n
        }
    }
}

/**
 * Automation entry point for Tasker/MacroDroid/IFTTT-webhook apps:
 * send broadcast `com.example.yt2mp3.action.ENQUEUE` with
 * StringArray extra `urls` (or String extra `text` containing links).
 * Titles are resolved via the engine; max 10 per call. No login/cookies.
 */
class EnqueueReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ENQUEUE) return
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as Yt2Mp3App
                val rawUrls = intent.getStringArrayExtra(EXTRA_URLS)?.toList()
                    ?: intent.getStringExtra(EXTRA_TEXT)?.let {
                        com.example.yt2mp3.data.util.UrlValidator.extractAllSupportedUrls(it)
                    } ?: emptyList()
                val urls = rawUrls.take(10)
                if (urls.isEmpty()) return@launch
                val reqs = urls.mapNotNull { url ->
                    runCatching {
                        val meta = app.metadata.fetchMetadata(url)
                        DownloadRequest(
                            sourceUrl = meta.sourceUrl,
                            title = meta.title,
                            artist = meta.artist,
                            thumbnailUrl = meta.thumbnailUrl,
                            quality = app.settings.getDefaultQuality(),
                            format = runCatching {
                                app.settings.observeDefaultFormat().first()
                            }.getOrDefault(com.example.yt2mp3.domain.model.AudioFormat.DEFAULT),
                            post = app.settings.getPostFlags(),
                        )
                    }.getOrNull()
                }
                if (reqs.isNotEmpty()) app.startDownload.executeAll(reqs)
            } catch (t: Throwable) {
                AppLog.w("AUTOMATION", "enqueue failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ENQUEUE = "com.example.yt2mp3.action.ENQUEUE"
        const val EXTRA_URLS = "urls"
        const val EXTRA_TEXT = "text"
    }
}
