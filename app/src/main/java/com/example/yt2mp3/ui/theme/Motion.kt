package com.example.yt2mp3.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Centralized motion tokens (Design §36). No random durations per screen.
 *
 * Hierarchy (§37): navigation / progress / content appearance / major state
 * changes are high priority; decorative motion is removed first on
 * low-end devices and always skipped under reduced motion (§43).
 */
object AppMotion {
    const val FAST_MS = 150
    const val NORMAL_MS = 220
    const val SLOW_MS = 350

    /** Delay before showing a loading indicator — avoids flicker (§33). */
    const val LOADING_GRACE_MS = 300L

    val Standard: Easing = FastOutSlowInEasing
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val Decelerate: Easing = LinearOutSlowInEasing
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    fun <T> tweenFast(reducedMotion: Boolean) =
        tween<T>(if (reducedMotion) 1 else FAST_MS, easing = Standard)

    fun <T> tweenNormal(reducedMotion: Boolean) =
        tween<T>(if (reducedMotion) 1 else NORMAL_MS, easing = Emphasized)
}

/**
 * True when the user enabled reduced motion (animator duration scale = 0)
 * or the device is low-RAM (§44). Decorative transitions collapse to
 * ~instant; informative progress updates are kept (Design §43).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            val animatorOff = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
            val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.isLowRamDevice == true
            animatorOff || lowRam
        }.getOrDefault(false)
    }
}
