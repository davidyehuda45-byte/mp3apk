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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.yt2mp3.domain.model.SortMode
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
    private val all = yt.downloads.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var query by mutableStateOf("")
        private set
    var sort by mutableStateOf(SortMode.NEWEST)
        private set
    var favOnly by mutableStateOf(false)
        private set

    val history = all

    /** Filtered + sorted snapshot for the UI. */
    fun visible(): List<DownloadItem> {
        var list = all.value
        if (favOnly) list = list.filter { it.favorite }
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.title.lowercase().contains(q) || (it.artist?.lowercase()?.contains(q) == true)
            }
        }
        return when (sort) {
            SortMode.NEWEST -> list.sortedByDescending { it.createdAt }
            SortMode.OLDEST -> list.sortedBy { it.createdAt }
            SortMode.TITLE -> list.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> list.sortedBy { (it.artist ?: it.title).lowercase() }
            SortMode.STATUS -> list.sortedBy { it.status.ordinal }
        }
    }

    /** Groups of likely duplicates (same source or same normalized title+artist). */
    fun duplicates(): List<List<DownloadItem>> {
        val groups = linkedMapOf<String, MutableList<DownloadItem>>()
        for (item in all.value) {
            val key = if (item.sourceUrl.isNotBlank()) "url:${item.sourceUrl}"
            else "t:${item.title.trim().lowercase()}|${item.artist?.trim()?.lowercase().orEmpty()}"
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
        return groups.values.filter { it.size > 1 }
    }

    fun onQuery(v: String) { query = v.take(100) }
    fun onSort(m: SortMode) { sort = m }
    fun onFavOnly(v: Boolean) { favOnly = v }

    fun retry(id: Long) = viewModelScope.launch { yt.retryDownload.execute(id) }

    /** Deletes the history entry only — never the file (§PRD-19). */
    fun delete(id: Long) = viewModelScope.launch { yt.downloads.deleteEntry(id) }

    fun toggleFavorite(item: DownloadItem) = viewModelScope.launch {
        yt.downloads.setFavorite(item.id, !item.favorite)
    }

    /** Deletes the entry AND its file in the SAF destination. */
    fun deleteWithFile(item: DownloadItem, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        var ok = true
        item.outputUri?.let { uri ->
            ok = (yt.container.storage.deleteOutput(uri))
        }
        yt.downloads.deleteEntry(item.id)
        onDone(ok)
    }

    fun rename(item: DownloadItem, newBase: String, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val clean = newBase.trim().take(100)
        if (clean.isEmpty()) {
            onDone(false)
            return@launch
        }
        val uri = item.outputUri
        if (uri == null || item.status != DownloadStatus.COMPLETED) {
            onDone(false)
            return@launch
        }
        val ext = item.outputFileName.substringAfterLast('.', "mp3")
        val newName = com.example.yt2mp3.data.util.FileNameSanitizer.sanitizeWithExtension(clean, ".$ext")
        val newUri = yt.container.storage.renameOutput(uri, newName)
        if (newUri != null) {
            yt.downloads.updateOutput(item.id, newName, newUri.toString())
            onDone(true)
        } else {
            onDone(false)
        }
    }

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
    onPlay: (Long) -> Unit = {},
    vm: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val all by vm.history.collectAsState()
    // Recompute on every recomposition so search/sort/fav apply instantly.
    val items = remember(all, vm.query, vm.sort, vm.favOnly) { vm.visible() }
    var selected by remember { mutableStateOf<DownloadItem?>(null) }
    var showDupes by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("History") },
            actions = {
                TextButton(onClick = { showDupes = true }) { Text("Duplikat") }
            },
        )
        Column(
            Modifier.padding(horizontal = AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQuery,
                placeholder = { Text("Cari judul/artis") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FilterChip(
                    selected = vm.favOnly,
                    onClick = { vm.onFavOnly(!vm.favOnly) },
                    label = { Text("Favorit") },
                )
                SortMode.entries.forEach { m ->
                    FilterChip(
                        selected = vm.sort == m,
                        onClick = { vm.onSort(m) },
                        label = { Text(m.label) },
                    )
                }
            }
        }
        if (items.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    if (all.isEmpty()) "No downloads yet" else "Tidak ada yang cocok",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (all.isEmpty()) "Downloaded files will appear here."
                    else "Ubah pencarian atau filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (all.isEmpty()) OutlinedButton(onClick = onStartDownloading) { Text("Start downloading") }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                groupByDay(items).forEach { group ->
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
                            onFav = { vm.toggleFavorite(item) },
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
                onDeleteFile = {
                    vm.deleteWithFile(item)
                    selected = null
                },
                onFav = { vm.toggleFavorite(item) },
                onRename = { name ->
                    vm.rename(item, name) { selected = null }
                },
                onPlay = {
                    selected = null
                    onPlay(item.id)
                },
            )
        }
    }

    if (showDupes) {
        val dupes = remember(all) { vm.duplicates() }
        AlertDialog(
            onDismissRequest = { showDupes = false },
            title = { Text("Duplikat (${dupes.size} grup)") },
            text = {
                if (dupes.isEmpty()) {
                    Text("Tidak ada duplikat.")
                } else {
                    LazyColumn {
                        dupes.take(20).forEach { g ->
                            item {
                                Text(
                                    "${g.first().title} × ${g.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDupes = false }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun HistoryRow(item: DownloadItem, onOpen: () -> Unit, onRetry: () -> Unit, onFav: () -> Unit) {
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
        IconButton(onClick = onFav) {
            Icon(
                if (item.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (item.favorite) "Hapus favorit" else "Favorit",
                tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun DetailSheet(
    item: DownloadItem,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDeleteFile: () -> Unit,
    onFav: () -> Unit,
    onRename: (String) -> Unit,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showRename by remember { mutableStateOf(false) }
    var newName by remember(item.id) { mutableStateOf(item.outputFileName.substringBeforeLast('.')) }
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
        item.subfolder?.let { DetailLine("Folder", it) }
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
                OutlinedButton(onClick = onPlay) { Text("Play") }
                OutlinedButton(onClick = { openFile(context, item.outputUri, mimeOf(item)) }) {
                    Text("Open file")
                }
            }
            if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("Retry")
                }
            }
            OutlinedButton(onClick = onFav) {
                Text(if (item.favorite) "Unfavorit" else "Favorit")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            if (item.outputUri != null) {
                OutlinedButton(onClick = {
                    shareFile(context, item.outputUri, mimeOf(item))
                }) { Text("Bagikan file") }
            }
            TextButton(onClick = {
                shareText(context, item.sourceUrl)
            }) { Text("Bagikan link") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            if (item.outputUri != null && item.status == DownloadStatus.COMPLETED) {
                TextButton(onClick = { showRename = true }) { Text("Rename") }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                Text("Delete entry")
            }
            if (item.outputUri != null) {
                TextButton(onClick = onDeleteFile) { Text("Delete + file") }
            }
        }
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(100) },
                    label = { Text("Nama baru (tanpa ekstensi)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(newName)
                    showRename = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Batal") } },
        )
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
    if (item.mediaKind == com.example.yt2mp3.domain.model.MediaKind.VIDEO) {
        "${item.videoFormat.label} ${item.videoQuality.label}"
    } else if (item.format == AudioFormat.MP3) {
        "MP3 ${item.quality.label}"
    } else {
        item.format.label
    }

private fun mimeOf(item: DownloadItem): String =
    if (item.mediaKind == com.example.yt2mp3.domain.model.MediaKind.VIDEO) item.videoFormat.mime
    else item.format.mime

/** Opens the finished file in the user's player. No-op if none can handle it. */
private fun openFile(context: Context, outputUri: String, mime: String) {
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

private fun shareFile(context: Context, outputUri: String, mime: String) {
    runCatching {
        val uri = android.net.Uri.parse(outputUri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}

private fun shareText(context: Context, text: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
