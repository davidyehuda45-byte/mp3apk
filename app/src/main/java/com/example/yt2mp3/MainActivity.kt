package com.example.yt2mp3

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.yt2mp3.data.util.InputParser
import com.example.yt2mp3.data.util.ParsedInput
import com.example.yt2mp3.ui.navigation.AppNavigation
import com.example.yt2mp3.ui.theme.YT2MP3Theme
import com.example.yt2mp3.ui.update.UpdatePrompt

/**
 * singleTop + onNewIntent so Share Sheet works on cold start,
 * when open, and from background (PRD §7).
 */
class MainActivity : ComponentActivity() {

    /** Shared-URL inbox consumed by Home screen. */
    var sharedUrl by mutableStateOf<String?>(null)
        private set

    // Android 13+: completion notifications need a runtime grant. The
    // foreground-service progress notification is exempt, but asking once
    // keeps success/failure notifications visible.
    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* sticky denial is fine; downloads still work */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Native splash only — no artificial delay, no UI-thread init (§11–§13).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotifPermissionIfNeeded()
        consumeShareIntent(intent)
        setContent {
            YT2MP3Theme {
                AppNavigation(
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = { sharedUrl = null },
                )
                UpdatePrompt()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        // EXTRA_TEXT may contain surrounding prose (PRD §45) — extract URL.
        sharedUrl = when (val p = InputParser.parse(text)) {
            is ParsedInput.Video -> p.url
            is ParsedInput.Playlist -> p.url
            else -> return
        }
    }
}
