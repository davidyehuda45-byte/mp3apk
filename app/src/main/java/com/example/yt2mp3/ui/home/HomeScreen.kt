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
import androidx.compose.material3.AlertDialog
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
import com.example.yt2mp3.domain.model.DownloadProfile
import com.example.yt2mp3.domain.model.DownloadStatus
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.SizeEstimator
import com.example.yt2mp3.domain.model.SubtitleMode
import com.example.yt2mp3.domain.model.TimeFormat
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoFormat
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.domain.model.VideoQuality
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
    var showSites by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showDisclaimer = runCatching {
            !(context.applicationContext as Yt2Mp3App).settings.getSeenDisclaimer()
        }.getOrDefault(false)
    }
    // Clipboard monitor: saat kembali ke app dan input kosong, tawarkan link dari clipboard.
    val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val app = context.applicationContext as Yt2Mp3App
                    if (!app.settings.getAutoPaste()) return@launch
                    if (vm.state.value.input.isNotBlank()) return@launch
                    val text = clipboard.getText()?.text?.trim().orEmpty()
                    if (text.isBlank()) return@launch
                    val urls = com.example.yt2mp3.data.util.UrlValidator.extractAllSupportedUrls(text)
                    if (urls.isNotEmpty() && vm.state.value.input != text.take(2000)) {
                        vm.onInputChange(text.take(2000))
                        pastedTick = true
                        focus.clearFocus()
                        vm.submit()
                    }
                }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(obs)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(obs) }
    }
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

    // Import batch links from TXT/CSV/JSON (Fase 1: batch paste via file).
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
                if (!text.isNullOrBlank()) vm.importBatchText(text)
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
                    "Tempel link video/audio (YouTube, TikTok, IG, FB, X, SoundCloud, dsb), beberapa link sekaligus, link playlist, atau ketik judul lagu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Link, beberapa link, atau judul lagu") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.input.isNotEmpty()) {
                        IconButton(onClick = vm::clear) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = false,
                maxLines = 5,
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
                OutlinedButton(onClick = { importPicker.launch(arrayOf("text/plain", "text/csv", "application/json")) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.padding(end = AppSpacing.xs))
                    Text("Import file")
                }
                Button(onClick = submit) {
                    Text(
                        when (state.parsed) {
                            is ParsedInput.Search -> "Cari"
                            is ParsedInput.Playlist -> "Buka playlist"
                            is ParsedInput.Batch -> "Preview ${(state.parsed as ParsedInput.Batch).urls.size} link"
                            else -> "Preview"
                        },
                    )
                }
            }
            TextButton(onClick = { showSites = true }) {
                Text("Situs didukung (1000+ via yt-dlp) — lihat daftar")
            }

            HorizontalDivider()

            ContentSection(state = state, vm = vm, reduced = reduced)

            val downloadable = state.content is HomeContent.Video ||
                state.content is HomeContent.Playlist || state.content is HomeContent.Batch
            if (downloadable) {
                ProfilesRow(
                    profiles = state.profiles,
                    active = state.activeProfile,
                    onApply = vm::applyProfile,
                    onSave = vm::saveProfile,
                    onDelete = vm::deleteProfile,
                )
                OptionsSection(
                    state = state,
                    vm = vm,
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
                        enabled = state.canDownload && state.destinationLabel != null && c.selected.isNotEmpty() &&
                            state.rangeError == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.padding(end = AppSpacing.xs))
                        val n = state.rangedIndices(c.info.entries.size, c.selected).size
                        Text("Download $n item", style = MaterialTheme.typography.labelLarge)
                    }
                    is HomeContent.Batch -> Button(
                        onClick = vm::download,
                        enabled = state.canDownload && state.destinationLabel != null && c.selected.isNotEmpty() &&
                            state.rangeError == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.padding(end = AppSpacing.xs))
                        val n = state.rangedIndices(c.items.size, c.selected).size
                        Text("Download $n link", style = MaterialTheme.typography.labelLarge)
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
                    "Tip: di aplikasi lain, tekan Share → YT2MP3 untuk langsung membuka link di sini. " +
                        "Bisa tempel banyak link sekaligus atau import file TXT/CSV/JSON. " +
                        "Hanya untuk konten publik yang boleh disimpan; konten privat/berbayar/DRM tidak didukung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Penting: hak cipta & penggunaan") },
            text = {
                Text(
                    "YT2MP3 hanya untuk konten publik yang kamu miliki atau diizinkan menyimpan " +
                        "untuk dipakai offline pribadi.\n\nDilarang: konten privat/login-only, konten " +
                        "berbayar, dan mem-bypass DRM atau proteksi akses. Patuhi syarat layanan tiap situs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclaimer = false
                    scope.launch {
                        (context.applicationContext as Yt2Mp3App).settings.setSeenDisclaimer()
                    }
                }) { Text("Mengerti") }
            },
        )
    }

    if (showSites) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSites = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.padding(start = AppSpacing.screen, end = AppSpacing.screen, bottom = AppSpacing.section),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                Text("Situs didukung", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Engine yt-dlp di HP mendukung 1000+ situs publik. Yang populer:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.example.yt2mp3.data.util.SupportedSites.popular.forEach { site ->
                    Column(Modifier.padding(vertical = AppSpacing.xs)) {
                        Text(site.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            site.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
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
                onOpenAsPlaylist = vm::openAsPlaylist,
            )
        }
        is HomeContent.Playlist -> PlaylistSection(
            c,
            onToggle = vm::toggleEntry,
            onSelectAll = vm::selectAll,
        )
        is HomeContent.Batch -> BatchSection(
            c,
            onToggle = vm::toggleBatch,
            onSelectAll = vm::selectBatchAll,
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
    onOpenAsPlaylist: () -> Unit,
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
        OutlinedButton(onClick = onOpenAsPlaylist) {
            Icon(Icons.Filled.LibraryMusic, contentDescription = null)
            Spacer(Modifier.padding(end = AppSpacing.xs))
            Text("Buka sebagai playlist/channel/album")
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
            meta.siteName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
                append("${c.info.entries.size} item")
                if (c.info.truncated) append(" (dibatasi 100 pertama)")
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

@Composable
private fun BatchSection(
    c: HomeContent.Batch,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
) {
    val ready = c.items.count { it.meta != null }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text("Batch: ${c.items.size} link", style = MaterialTheme.typography.titleMedium)
        Text(
            "$ready siap · ${c.selected.size} dipilih (maks ${com.example.yt2mp3.data.util.UrlValidator.MAX_BATCH}/paste)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val allSelected = c.selected.size == ready && ready > 0
        TextButton(onClick = { onSelectAll(!allSelected) }) {
            Text(if (allSelected) "Hapus semua pilihan" else "Pilih semua yang siap")
        }
        c.items.forEachIndexed { i, item ->
            val meta = item.meta
            if (meta != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(i) }
                        .padding(vertical = AppSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.group),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = i in c.selected, onCheckedChange = { onToggle(i) })
                    SmallThumb(url = listThumb(meta.thumbnailUrl), contentDescription = null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text(
                            meta.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                meta.siteName,
                                meta.artist,
                                meta.durationSecs?.let(TimeFormat::format),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.url.take(80),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.error ?: "Gagal memuat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- options

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OptionsSection(
    state: HomeScreenState,
    vm: HomeViewModel,
) {
    val format = state.format
    val quality = state.quality
    val estimate = state.estimate
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("Jenis output", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MediaKind.entries.forEachIndexed { i, k ->
                SegmentedButton(
                    selected = state.mediaKind == k,
                    onClick = { vm.onMediaKindChange(k) },
                    shape = SegmentedButtonDefaults.itemShape(i, MediaKind.entries.size),
                ) { Text(if (k == MediaKind.AUDIO) "Audio" else "Video") }
            }
        }
        if (state.mediaKind == MediaKind.AUDIO) {
            Text("Format", style = MaterialTheme.typography.titleMedium)
            // 6 formats don't fit a segmented row: wrap as filter chips.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                AudioFormat.entries.forEach { f ->
                    androidx.compose.material3.FilterChip(
                        selected = format == f,
                        onClick = { vm.onFormatChange(f) },
                        label = { Text(f.label) },
                    )
                }
            }
            if (format.usesQuality) {
                Text("Quality", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AudioQuality.entries.forEachIndexed { i, q ->
                        SegmentedButton(
                            selected = quality == q,
                            onClick = { vm.onQualityChange(q) },
                            shape = SegmentedButtonDefaults.itemShape(i, AudioQuality.entries.size),
                        ) { Text(q.label) }
                    }
                }
            } else if (format == AudioFormat.M4A) {
                Text(
                    "Audio asli tanpa konversi: lebih cepat, file lebih kecil, kualitas tidak turun. " +
                        "Bisa diputar di hampir semua HP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Format lossless (FLAC/WAV): kualitas maksimal, file lebih besar. Quality bitrate tidak berlaku.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text("Format video", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                VideoFormat.entries.forEach { f ->
                    androidx.compose.material3.FilterChip(
                        selected = state.videoFormat == f,
                        onClick = { vm.onVideoFormatChange(f) },
                        label = { Text(f.label) },
                    )
                }
            }
            Text("Resolusi maks", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                VideoQuality.entries.forEach { q ->
                    androidx.compose.material3.FilterChip(
                        selected = state.videoQuality == q,
                        onClick = { vm.onVideoQualityChange(q) },
                        label = { Text(q.label) },
                    )
                }
            }
        }
        if (state.content is HomeContent.Playlist || state.content is HomeContent.Batch) {
            OutlinedTextField(
                value = state.rangeText,
                onValueChange = vm::onRangeChange,
                label = { Text("Range (opsional)") },
                placeholder = { Text("mis. 1-10,15,20-30 — kosong = semua") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.rangeError != null,
            )
            state.rangeError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lewati yang sudah ada", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Jangan antre ulang link yang sudah ada di riwayat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.skipExisting, onCheckedChange = vm::onSkipChange)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Subfolder per playlist", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Simpan ke subfolder sesuai nama playlist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.subfolderAuto, onCheckedChange = vm::onSubfolderChange)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Opsi lanjutan", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Subtitle, chapters, SponsorBlock, normalisasi, thumbnail terpisah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.showAdvanced, onCheckedChange = { vm.toggleAdvanced() })
        }
        if (state.showAdvanced) {
            AdvancedOptions(state = state, vm = vm)
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AdvancedOptions(state: HomeScreenState, vm: HomeViewModel) {
    val post = state.post
    Column(
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.sm),
    ) {
        Text("Subtitle (video)", style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            SubtitleMode.entries.forEach { m ->
                androidx.compose.material3.FilterChip(
                    selected = post.subtitleMode == m,
                    onClick = { vm.onPostChange { it.copy(subtitleMode = m) } },
                    label = { Text(m.label) },
                )
            }
        }
        if (post.subtitleMode != SubtitleMode.NONE && state.mediaKind == MediaKind.VIDEO) {
            OutlinedTextField(
                value = post.subLangs,
                onValueChange = { v -> vm.onPostChange { it.copy(subLangs = v.take(60)) } },
                label = { Text("Bahasa subtitle") },
                placeholder = { Text("en,id") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        CheckRow("Embed chapters", "Simpan penanda bab ke file.", post.embedChapters) { v ->
            vm.onPostChange { it.copy(embedChapters = v) }
        }
        CheckRow("SponsorBlock", "Hapus segmen sponsor (konten publik).", post.sponsorBlock) { v ->
            vm.onPostChange { it.copy(sponsorBlock = v) }
        }
        CheckRow("Normalisasi loudness", "Samakan volume (EBU R128).", post.normalizeAudio) { v ->
            vm.onPostChange { it.copy(normalizeAudio = v) }
        }
        CheckRow("Simpan thumbnail terpisah", "Simpan cover sebagai JPG di samping file.", post.saveThumbSeparate) { v ->
            vm.onPostChange { it.copy(saveThumbSeparate = v) }
        }
        CheckRow("Hapus metadata (privasi)", "Jangan embed tag/thumbnail apa pun.", post.stripMetadata) { v ->
            vm.onPostChange { it.copy(stripMetadata = v) }
        }
    }
}

@Composable
private fun CheckRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfilesRow(
    profiles: List<DownloadProfile>,
    active: String?,
    onApply: (DownloadProfile) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showSave by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profil", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showSave = true }) { Text("Simpan saat ini") }
        }
        if (profiles.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                profiles.forEach { p ->
                    androidx.compose.material3.FilterChip(
                        selected = active == p.name,
                        onClick = { onApply(p) },
                        label = { Text(p.name) },
                        trailingIcon = {
                            IconButton(onClick = { onDelete(p.name) }, modifier = Modifier.height(AppSpacing.lg)) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus profil ${p.name}")
                            }
                        },
                    )
                }
            }
        }
    }
    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Simpan profil") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nama profil") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(name)
                    showSave = false
                    name = ""
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Batal") } },
        )
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
