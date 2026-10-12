package com.example.yt2mp3.data.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Structured logging with an in-memory ring buffer for the in-app log viewer. */
object AppLog {
    private const val PREFIX = "YT2MP3"
    private const val CAP = 300
    private val lock = Any()
    private val ring = ArrayDeque<String>()

    data class Entry(val time: Long, val level: String, val tag: String, val msg: String)

    private val entries = ArrayDeque<Entry>()

    fun d(tag: String, msg: String) {
        Log.d("$PREFIX/$tag", msg)
        push("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i("$PREFIX/$tag", msg)
        push("I", tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        Log.w("$PREFIX/$tag", msg, t)
        push("W", tag, if (t != null) "$msg (${t.message ?: t::class.java.simpleName})" else msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e("$PREFIX/$tag", msg, t)
        push("E", tag, if (t != null) "$msg (${t.message ?: t::class.java.simpleName})" else msg)
    }

    private fun push(level: String, tag: String, msg: String) {
        synchronized(lock) {
            entries.addLast(Entry(System.currentTimeMillis(), level, tag, msg.take(500)))
            ring.addLast(msg)
            while (entries.size > CAP) entries.removeFirst()
            while (ring.size > CAP) ring.removeFirst()
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) {
        entries.clear()
        ring.clear()
    }

    fun exportText(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        return snapshot().joinToString("\n") {
            "${fmt.format(Date(it.time))} ${it.level}/${it.tag}: ${it.msg}"
        }
    }
}
