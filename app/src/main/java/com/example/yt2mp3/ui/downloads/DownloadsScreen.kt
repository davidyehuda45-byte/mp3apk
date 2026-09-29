package com.example.yt2mp3.ui.downloads

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.ui.components.SmallThumb
import com.example.yt2mp3.ui.components.listThumb
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.ui.theme.AppMotion
import com.example.yt2mp3.ui.theme.AppSpacing
import com.example.yt2mp3.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(app: Application) : ViewModel() {
    private val yt = app as Yt2Mp3App
    val queue = yt.downloads.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(id: Long) = viewModelScope.launch { yt.cancelDownload(id) }

    /** Removes a waiting item from the queue entirely (PRD §22). */
    fun remove(id: Long) = viewModelScope.launch { yt.downloads.deleteEntry(id) }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { DownloadsViewModel(app) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onStartDownloading: () -> Unit = {},
    vm: DownloadsViewModel = viewModel(
        factory = DownloadsViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val queue by vm.queue.collectAsState()
    val reduced = rememberReducedMotion()
    var pendingCancel by remember { mutableStateOf<DownloadItem?>(null) }
    // Queue enter: fade + 8dp rise (§24).
    val density = LocalDensity.current
    val risePx = remember(density) { with(density) { AppSpacing.sm.toPx().toInt() } }

    val current = queue.firstOrNull { it.status.isActive || it.status == DownloadStatus.DOWNLOADING }
        ?: queue.firstOrNull()
    val waiting = queue.filter { it != current }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Downloads") })
        if (queue.isEmpty()) {
            // Minimal empty state (§40): no illustration, no emoji.
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Queued and active downloads will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onStartDownloading) { Text("Start downloading") }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
            ) {
                if (current != null) {
                    Text("Current", style = MaterialTheme.typography.titleMedium)
                    CurrentCard(
                        item = current,
                        reduced = reduced,
                        onCancel = { pendingCancel = current },
                    )
                }
                if (waiting.isNotEmpty()) {
                    Text("Queue", style = MaterialTheme.typography.titleMedium)
                    waiting.forEach { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = if (reduced) fadeIn(tween(1))
                            else fadeIn(tween(AppMotion.NORMAL_MS)) +
                                slideInVertically(tween(AppMotion.NORMAL_MS)) { risePx },
                            exit = fadeOut(tween(AppMotion.FAST_MS)),
                        ) {
                            QueueRow(
                                item = item,
                                onCancel = { pendingCancel = item },
                                onRemove = { vm.remove(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirm dialog for destructive action only (§32).
    pendingCancel?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancel download?") },
            text = { Text("The current download will be stopped.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancel(item.id)
                    pendingCancel = null
                }) { Text("Cancel") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) { Text("Keep downloading") }
            },
        )
    }
}

@Composable
private fun CurrentCard(item: DownloadItem, reduced: Boolean, onCancel: () -> Unit) {
    // Tonal surface, no shadow (§8). Current job gets the most hierarchy (§38).
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSpacing.none),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.group),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.thumbnailUrl != null) {
                    SmallThumb(
                        url = listThumb(item.thumbnailUrl),
                        contentDescription = "Thumbnail: ${item.title}",
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.artist != null) {
                        Text(
                            item.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Smooth but honest progress: short interpolation of real data (§22).
            val target = (item.progress ?: 0) / 100f
            val animated by animateFloatAsState(
                target,
                animationSpec = tween(if (reduced) 1 else AppMotion.FAST_MS),
                label = "progress",
            )
            if (item.progress != null) {
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Download progress ${item.progress} percent"
                        },
                )
                Text(
                    "${item.progress}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Indeterminate only when truly unknown — the one allowed
                // infinite animation (§46).
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    statusLabel(item),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onCancel) {
                    Icon(Icons.Filled.Cancel, contentDescription = null)
                    Text("Cancel")
                }
            }
            if (item.status == DownloadStatus.COMPLETED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Completed", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(item: DownloadItem, onCancel: () -> Unit, onRemove: () -> Unit) {
    // Compact rows for waiting items (§38).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.group),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                statusLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.status == DownloadStatus.QUEUED) {
            // Waiting items can be removed outright; active ones go
            // through the cancel confirmation dialog (§32).
            TextButton(onClick = onRemove) { Text("Remove") }
        } else {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun statusLabel(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "Waiting"
    DownloadStatus.DOWNLOADING -> item.stage ?: "Downloading…"
    DownloadStatus.CONVERTING -> "Converting"
    DownloadStatus.SAVING -> "Saving"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}
