package com.example.yt2mp3.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.yt2mp3.ui.theme.AppSpacing

/**
 * Size-aware small thumbnail for lists (§45): requests a ~112px image so
 * list scrolling never holds full-resolution bitmaps in memory.
 * Coil handles caching + memory-efficient decoding.
 */
/** ~4 KB 120×90 YouTube thumbnail instead of the ~25 KB hqdefault, for long lists on mobile data. */
fun listThumb(url: String?): String? = url?.replace("/hqdefault.jpg", "/default.jpg")

@Composable
fun SmallThumb(url: String?, contentDescription: String?) {
    if (url == null) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .size(112, 112)
            .crossfade(false) // no decorative fade in long lists
            .build(),
        contentDescription = contentDescription,
        modifier = Modifier
            .size(AppSpacing.thumbSmall)
            .clip(MaterialTheme.shapes.small),
        contentScale = ContentScale.Crop,
    )
}
