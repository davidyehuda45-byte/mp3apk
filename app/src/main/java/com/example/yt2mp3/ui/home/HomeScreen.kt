package com.example.yt2mp3.ui.home

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.data.util.ParsedInput
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.SizeEstimator
import com.example.yt2mp3.domain.model.TimeFormat
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.ui.components.SmallThumb
import com.example.yt2mp3.ui.components.listThumb
import com.example.yt2mp3.ui.theme.AppMotion
import com.example.yt2mp3.ui.theme.AppSpacing
import com.example.yt2mp3.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val state by vm.state.collectAsState()
    val tracked by vm.tracked.collectAsState()
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()

    var pastedTick by remember { mutableStateOf(false) }
    LaunchedEffect(pastedTick) {
        if (pastedTick) {
            delay(1000)
            pastedTick = false
        }
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            vm.onSharedText(sharedUrl)
            onSharedUrlConsumed()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            scope.launch {
                (context.applicationContext as Yt2Mp3App).settings.setDestinationUri(uri.toString())
            }
        }
    }

    // One-time subtle highlight when the destination changes (§30).
    var destFlash by remember { mutableStateOf(false) }
    LaunchedEffect(state.destinationLabel) {
        if (state.destinationLabel != null) {
            destFlash = true
            delay(700)
            destFlash = false
        }
    }
    val destBg by animateColorAsState(
        if (destFlash && !reduced) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(AppMotion.SLOW_MS),
        label = "destFlash",
    )

    val submit = {
        focus.clearFocus()
        vm.submit()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("YT2MP3") })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text("Download audio", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tempel link YouTube, link playlist, atau ketik judul lagu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Link atau judul lagu") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.input.isNotEmpty()) {
                        IconButton(onClick = vm::clear) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                isError = state.content is HomeContent.Error,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = {
                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                        vm.onInputChange(it.trim())
                        pastedTick = true
                        submit()
                    }
                }) {
                    Icon(
                        if (pastedTick) Icons.Filled.Check else Icons.Filled.ContentPaste,
                        contentDescription = null,
                    )
                    Spacer(Modifier.padding(end = AppSpacing.xs))
                    Text(if (pastedTick) "Pasted" else "Paste")
                }
                Button(onClick = submit) {
                    Text(
                        when (state.parsed) {
                            is ParsedInput.Search -> "Cari"
                            is ParsedInput.Playlist -> "Buka playlist"
                            else -> "Preview"
                        },
                    )
                }
            }

            HorizontalDivider()

            ContentSection(state = state, vm = vm, reduced = reduced)

            val downloadable = state.content is HomeContent.Video || state.content is HomeContent.Playlist
            if (downloadable) {
                OptionsSection(
                    format = state.format,
                    quality = state.quality,
                    estimate = state.estimate,
                    onFormat = vm::onFormatChange,
                    onQuality = vm::onQualityChange,
                )

                Text("Save location", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(destBg)
                        .padding(vertical = AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.destinationLabel ?: "No folder selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { folderPicker.launch(null) }) { Text("Change") }
                }

                val trimOk = state.trimCheck !is TimeFormat.TrimResult.Invalid
                when (val c = state.content) {
                    is HomeContent.Video -> DownloadButton(
                        tracked = tracked,
                        enabled = state.canDownload && state.destinationLabel != null && trimOk,
                        reduced = reduced,
                        onClick = vm::download,
                    )
                    is HomeContent.Playlist -> Button(
                        onClick = vm::download,
                        enabled = state.canDownload && state.destinationLabel != null && c.selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.padding(end = AppSpacing.xs))
                        Text("Download ${c.selected.size} lagu", style = MaterialTheme.typography.labelLarge)
                    }
                    else -> Unit
                }
                if (state.destinationLabel == null) {
                    Text(
                        "Choose a destination folder before downloading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.notice?.let {
                SelectionContainer {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.content is HomeContent.Idle) {
                Text(
                    "Tip: di aplikasi YouTube, tekan Share → YT2MP3 untuk langsung membuka link di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ----------------------------------------------------------------- content

@Composable
private fun ContentSection(state: HomeScreenState, vm: HomeViewModel, reduced: Boolean) {
    when (val c = state.content) {
        HomeContent.Idle -> Unit
        is HomeContent.Loading -> LoadingIndicator(c.label)
        is HomeContent.Error -> InlineError(
            title = c.title,
            message = c.message,
            actionLabel = if (c.canRetry) "Try again" else null,
            onAction = vm::retry,
        )
        is HomeContent.SearchResults -> SearchResultsSection(c, onOpen = vm::openResult)
        is HomeContent.Video -> Reveal(reduced) {
            VideoSection(
                meta = c.meta,
                edits = state.edits,
                trimError = (state.trimCheck as? TimeFormat.TrimResult.Invalid)?.message,
                showBack = state.backToResults != null,
                onBack = vm::backToResults,
                onEdits = vm::onEdits,
                onOpenPlaylist = { url -> vm.loadPlaylist(url) },
            )
        }
        is HomeContent.Playlist -> PlaylistSection(
            c,
            onToggle = vm::toggleEntry,
            onSelectAll = vm::selectAll,
        )
    }
}

@Composable
private fun Reveal(reduced: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = if (reduced) fadeIn(tween(1))
        else fadeIn(tween(AppMotion.NORMAL_MS, easing = AppMotion.Standard)) +
            slideInVertically(tween(AppMotion.NORMAL_MS, easing = AppMotion.Emphasized)) { it / 8 },
        exit = fadeOut(tween(AppMotion.FAST_MS)),
    ) { content() }
}

@Composable
private fun LoadingIndicator(label: String) {
    // Loading indicator only after a grace period — no flicker (§33).
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(AppMotion.LOADING_GRACE_MS)
        show = true
    }
    if (show) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SearchResultsSection(c: HomeContent.SearchResults, onOpen: (VideoEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text("Hasil untuk “${c.query}”", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ketuk lagu untuk melihat preview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        c.results.forEach { e ->
            EntryRow(entry = e, modifier = Modifier.clickable { onOpen(e) })
        }
    }
}

@Composable
private fun EntryRow(entry: VideoEntry, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.group),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        SmallThumb(url = listThumb(entry.thumbnailUrl), contentDescription = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(entry.channel, entry.durationSecs?.let(TimeFormat::format)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VideoSection(
    meta: VideoMetadata,
    edits: VideoEdits,
    trimError: String?,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdits: ((VideoEdits) -> VideoEdits) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.group)) {
        if (showBack) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.padding(end = AppSpacing.xs))
                Text("Kembali ke hasil pencarian")
            }
        }
        PreviewCard(meta)

        // Edits go into the file name and the song tags (§17).
        OutlinedTextField(
            value = edits.title,
            onValueChange = { v -> onEdits { it.copy(title = v) } },
            label = { Text("Judul") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = edits.artist,
            onValueChange = { v -> onEdits { it.copy(artist = v) } },
            label = { Text("Artis") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Potong bagian lagu", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Hanya bagian yang dipilih yang diunduh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = edits.trimEnabled,
                onCheckedChange = { on -> onEdits { it.copy(trimEnabled = on) } },
            )
        }
        if (edits.trimEnabled) {
            val durationText = meta.durationSecs?.let(TimeFormat::format)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = edits.trimStart,
                    onValueChange = { v -> onEdits { it.copy(trimStart = v) } },
                    label = { Text("Mulai") },
                    placeholder = { Text("0:00") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = trimError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = edits.trimEnd,
                    onValueChange = { v -> onEdits { it.copy(trimEnd = v) } },
                    label = { Text("Selesai") },
                    placeholder = { Text(durationText ?: "akhir") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = trimError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Text(
                trimError ?: "Format m:ss, misal 1:30 (titik juga boleh: 1.30). Kosongkan = dari awal / sampai akhir.",
                style = MaterialTheme.typography.bodySmall,
                color = if (trimError != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        meta.playlistUrl?.let { url ->
            OutlinedButton(onClick = { onOpenPlaylist(url) }) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                Spacer(Modifier.padding(end = AppSpacing.xs))
                Text("Link ini bagian dari playlist — buka playlist")
            }
        }
    }
}

@Composable
private fun PreviewCard(meta: VideoMetadata) {
    // Tonal surface + subtle border instead of shadow (§8).
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSpacing.none),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(AppSpacing.group),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            if (meta.thumbnailUrl != null) {
                AsyncImage(
                    model = meta.thumbnailUrl,
                    contentDescription = "Thumbnail: ${meta.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppSpacing.previewImage)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(meta.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(meta.artist, meta.durationSecs?.let(TimeFormat::format)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistSection(
    c: HomeContent.Playlist,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(c.info.title, style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                append("${c.info.entries.size} video")
                if (c.info.truncated) append(" (dibatasi 100 video pertama)")
                append(" · ${c.selected.size} dipilih")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val allSelected = c.selected.size == c.info.entries.size
        TextButton(onClick = { onSelectAll(!allSelected) }) {
            Text(if (allSelected) "Hapus semua pilihan" else "Pilih semua")
        }
        c.info.entries.forEachIndexed { i, e ->
            EntryRow(
                entry = e,
                modifier = Modifier.clickable { onToggle(i) },
                leading = { Checkbox(checked = i in c.selected, onCheckedChange = { onToggle(i) }) },
            )
        }
    }
}

// ----------------------------------------------------------------- options

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSection(
    format: AudioFormat,
    quality: AudioQuality,
    estimate: SizeEstimator.Estimate?,
    onFormat: (AudioFormat) -> Unit,
    onQuality: (AudioQuality) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("Format", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AudioFormat.entries.forEachIndexed { i, f ->
                SegmentedButton(
                    selected = format == f,
                    onClick = { onFormat(f) },
                    shape = SegmentedButtonDefaults.itemShape(i, AudioFormat.entries.size),
                ) { Text(f.label) }
            }
        }
        if (format == AudioFormat.MP3) {
            Text("Quality", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AudioQuality.entries.forEachIndexed { i, q ->
                    SegmentedButton(
                        selected = quality == q,
                        onClick = { onQuality(q) },
                        shape = SegmentedButtonDefaults.itemShape(i, AudioQuality.entries.size),
                    ) { Text(q.label) }
                }
            }
        } else {
            Text(
                "Audio asli dari YouTube tanpa konversi: lebih cepat, file lebih kecil, kualitas tidak turun. " +
                    "Bisa diputar di hampir semua HP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (estimate != null) {
            Text(
                "Pemakaian kuota ± ${SizeEstimator.formatBytes(estimate.downloadBytes)} · " +
                    "ukuran file ± ${SizeEstimator.formatBytes(estimate.fileBytes)}" +
                    if (estimate.exact) "" else " (perkiraan)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ------------------------------------------------------------------ shared

@Composable
private fun InlineError(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // Calm error: color + icon + reveal, no shake/flash (§26).
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun DownloadButton(
    tracked: DownloadItem?,
    enabled: Boolean,
    reduced: Boolean,
    onClick: () -> Unit,
) {
    val t = tracked
    val busy = t != null && !t.status.isTerminal
    val label = when {
        t == null -> "Download"
        else -> when (t.status) {
            DownloadStatus.QUEUED -> "Preparing"
            DownloadStatus.DOWNLOADING ->
                if (t.progress != null) "Downloading ${t.progress}%" else (t.stage ?: "Downloading…")
            DownloadStatus.CONVERTING -> "Converting"
            DownloadStatus.SAVING -> "Saving"
            DownloadStatus.COMPLETED -> "Completed"
            else -> "Download"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Button(
            onClick = onClick,
            // Stable shape while busy — no drastic reshaping (§20).
            enabled = if (busy) false else enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!busy && t?.status == DownloadStatus.COMPLETED) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.padding(end = AppSpacing.xs))
            } else if (busy) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.padding(end = AppSpacing.xs))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        // Calm determinate progress under the button (§21). Interpolated, never fabricated (§22).
        if (busy && t?.progress != null) {
            val animated by animateFloatAsState(
                t.progress / 100f,
                animationSpec = tween(if (reduced) 1 else AppMotion.FAST_MS),
                label = "homeProgress",
            )
            LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
        } else if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (t?.status == DownloadStatus.FAILED) {
            SelectionContainer {
                Text(
                    t.errorMessage ?: "Download failed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
