package com.example.yt2mp3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Restrained visual language (Design §4–§8):
 * one accent hue, neutral surfaces, subtle borders over shadows,
 * few shape sizes, default platform type scale (no decorative fonts).
 */

// Single accent hue — deep emerald. Light variant is darker for contrast
// on near-white; dark variant is lighter for contrast on deep neutral.
private val AccentLight = Color(0xFF0C6B4A)
private val AccentDark = Color(0xFF62D99A)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F2E4),
    onPrimaryContainer = Color(0xFF083F2C),
    secondary = Color(0xFF4E5A54),
    onSecondary = Color.White,
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF161918),
    surface = Color.White,
    onSurface = Color(0xFF161918),
    surfaceVariant = Color(0xFFF0F2EF),
    onSurfaceVariant = Color(0xFF4E5A54),
    surfaceContainerHighest = Color(0xFFE8EBE7),
    outline = Color(0xFFDDE1DC),
    outlineVariant = Color(0xFFE9ECE8),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF06281C),
    primaryContainer = Color(0xFF0C4A32),
    onPrimaryContainer = Color(0xFFC9F2DC),
    secondary = Color(0xFF9AA3A6),
    background = Color(0xFF101314), // deep neutral, never pure black (§4)
    onBackground = Color(0xFFE8EAEB),
    surface = Color(0xFF171B1D),
    onSurface = Color(0xFFE8EAEB),
    surfaceVariant = Color(0xFF1E2426),
    onSurfaceVariant = Color(0xFF9AA3A6),
    surfaceContainerHighest = Color(0xFF262E31),
    outline = Color(0xFF2C3437),
    outlineVariant = Color(0xFF232A2D),
    error = Color(0xFFFFB4AB),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

private val AppTypography = Typography(
    // App wordmark: simple medium weight, no effects (§5).
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

/** Material 3 theme; dark mode is a first-class surface ramp (§42). */
@Composable
fun YT2MP3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
