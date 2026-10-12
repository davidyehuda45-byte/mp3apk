package com.example.yt2mp3.service

import android.content.Intent
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to open YT2MP3. */
class DownloadTileService : TileService() {
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(Intent(this, com.example.yt2mp3.MainActivity::class.java))
    }
}
