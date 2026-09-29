package com.example.yt2mp3.ui.history

import android.app.Application
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.TimeFormat
import com.example.yt2mp3.ui.components.SmallThumb
import com.example.yt2mp3.ui.components.listThumb
import com.example.yt2mp3.ui.theme.AppSpacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : ViewModel() {
    private val yt = app as Yt2Mp3App
    val history = yt.downloads.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(id: Long) = viewModelScope.launch { yt.retryDownload.execute(id) }

    /** Deletes the history entry only — never the file (§PRD-19). */
    fun delete(id: Long) = viewModelScope.launch { yt.downloads.deleteEntry(id) }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(app) }
        }
    }
}

private data class HistoryGroup(val header: String, val items: List<DownloadItem>)

private fun groupByDay(items: List<DownloadItem>): List<HistoryGroup> {
    if (items.isEmpty()) return emptyList()
    val groups = linkedMapOf<String, MutableList<DownloadItem>>()
    for (item in items) {
        val header = when {
            DateUtils.isToday(item.createdAt) -> "Today"
            DateUtils.isToday(item.createdAt + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
            else -> DateFormat.getDateInstance().format(Date(item.createdAt))
        }
        groups.getOrPut(header) { mutableListOf() }.add(item)
    }
    return groups.map { HistoryGroup(it.key, it.value) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onStartDownloading: () -> Unit = {},
    vm: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val history by vm.history.collectAsState()
    var selected by remember { mutableStateOf<DownloadItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("History") })
        if (history.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Downloaded files will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onStartDownloading) { Text("Start downloading") }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                groupByDay(history).forEach { group ->
                    item(key = "header_${group.header}") {
                        Text(
                            group.header,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                top = AppSpacing.lg,
                                bottom = AppSpacing.xs,
                            ),
                        )
                    }
                    items(group.items, key = { it.id }) { item ->
                        HistoryRow(
                            item = item,
                            onOpen = { selected = item },
                            onRetry = { vm.retry(item.id) },
                        )
                    }
                }
            }
        }
    }

    // Detail + actions in a compact bottom sheet (§31). Native sheet motion,
    // no custom animation. Dismiss on action.
    selected?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DetailSheet(
                item = item,
                onRetry = {
                    vm.retry(item.id)
                    selected = null
                },
                onDelete = {
                    vm.delete(item.id)
                    selected = null
                },
            )
        }
    }
}

@Composable
private fun HistoryRow(item: DownloadItem, onOpen: () -> Unit, onRetry: () -> Unit) {
    // Clean list row, not a developer log (§39). Small thumbnail (§45).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.group),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallThumb(
            url = listThumb(item.thumbnailUrl),
            contentDescription = "Thumbnail: ${item.title}",
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    item.artist,
                    "${formatLabel(item)} · ${item.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("Retry")
            }
        }
    }
}

@Composable
private fun DetailSheet(item: DownloadItem, onRetry: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.padding(
            start = AppSpacing.screen,
            end = AppSpacing.screen,
            bottom = AppSpacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleLarge)
        if (item.artist != null) {
            Text(
                item.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        DetailLine("Format", formatLabel(item))
        if (item.sectionStart != null || item.sectionEnd != null) {
            DetailLine(
                "Bagian",
                "${TimeFormat.format((item.sectionStart ?: 0).toLong())} – " +
                    (item.sectionEnd?.let { TimeFormat.format(it.toLong()) } ?: "akhir"),
            )
        }
        DetailLine("Status", item.status.name.lowercase().replaceFirstChar { it.uppercase() })
        DetailLine("File", item.outputFileName)
        DetailLine(
            "Date",
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(item.createdAt)),
        )
        DetailLine("Source", item.sourceUrl)
        if (item.status == DownloadStatus.FAILED && item.errorMessage != null) {
            DetailLine("Error", item.errorMessage)
            // User-facing handle on technical error info (§31.8).
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.errorMessage))
            }) { Text("Copy error") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            if (item.outputUri != null && item.status == DownloadStatus.COMPLETED) {
                OutlinedButton(onClick = { openAudio(context, item.outputUri, item.format.mime) }) {
                    Text("Open file")
                }
            }
            if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("Retry")
                }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                Text("Delete entry")
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatLabel(item: DownloadItem): String =
    if (item.format == AudioFormat.MP3) "MP3 ${item.quality.label}" else item.format.label

/** Opens the finished file in the user's player. No-op if none can handle it. */
private fun openAudio(context: Context, outputUri: String, mime: String) {
    runCatching {
        val uri = android.net.Uri.parse(outputUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
