package com.example.yt2mp3.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized spacing tokens (Design §6). No ad-hoc margins in screens.
 * Base scale: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48.
 */
object AppSpacing {
    val none: Dp = 0.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val huge: Dp = 40.dp
    val giant: Dp = 48.dp

    /** Default screen padding and section gaps. */
    val screen: Dp = 16.dp
    val section: Dp = 24.dp
    val group: Dp = 12.dp

    /** Fixed component sizes. */
    val previewImage: Dp = 180.dp
    val thumbSmall: Dp = 56.dp
}
