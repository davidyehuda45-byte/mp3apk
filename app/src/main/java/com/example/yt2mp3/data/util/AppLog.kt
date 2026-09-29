package com.example.yt2mp3.data.util

import android.util.Log

/** Structured logging (PRD §49). */
object AppLog {
    private const val PREFIX = "YT2MP3"

    fun d(tag: String, msg: String) { Log.d("$PREFIX/$tag", msg) }
    fun i(tag: String, msg: String) { Log.i("$PREFIX/$tag", msg) }
    fun w(tag: String, msg: String, t: Throwable? = null) { Log.w("$PREFIX/$tag", msg, t) }
    fun e(tag: String, msg: String, t: Throwable? = null) { Log.e("$PREFIX/$tag", msg, t) }
}
