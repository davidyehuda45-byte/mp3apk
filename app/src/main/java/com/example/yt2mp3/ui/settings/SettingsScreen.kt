package com.example.yt2mp3.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.ui.theme.AppSpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : ViewModel() {
    private val yt = app as Yt2Mp3App
    val destination = yt.settings.observeDestinationUri()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val quality = yt.settings.observeDefaultQuality()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioQuality.DEFAULT)
    val format = yt.settings.observeDefaultFormat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioFormat.DEFAULT)

    fun setFormat(f: AudioFormat) = viewModelScope.launch { yt.settings.setDefaultFormat(f) }

    var engineVersion by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            engineVersion = yt.engineManager.currentVersion()
        }
    }

    var updateMessage by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    fun setQuality(q: AudioQuality) = viewModelScope.launch {
        yt.settings.setDefaultQuality(q)
    }

    fun checkUpdate() = viewModelScope.launch {
        checking = true
        updateMessage = "Checking…"
        yt.updateEngine.checkLatest().fold(
            onSuccess = { updateMessage = "Latest: $it" },
            onFailure = { updateMessage = "Check failed." },
        )
        checking = false
    }

    fun doUpdate() = viewModelScope.launch {
        checking = true
        updateMessage = "Updating…"
        yt.updateEngine.update().fold(
            onSuccess = {
                engineVersion = yt.engineManager.currentVersion()
                updateMessage = "Engine up to date."
            },
            onFailure = { updateMessage = "Update failed. Previous engine kept." },
        )
        checking = false
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(app) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dest by vm.destination.collectAsState()
    val quality by vm.quality.collectAsState()
    val format by vm.format.collectAsState()
    val engineVersion = vm.engineVersion

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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

    // Almost static screen (§29): sections, plain selection, no custom animation.
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
        ) {
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dest?.takeLast(48) ?: "No folder selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = { picker.launch(null) }) { Text("Change folder") }

            HorizontalDivider()

            Text("Default format", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AudioFormat.entries.forEachIndexed { i, f ->
                    SegmentedButton(
                        selected = format == f,
                        onClick = { vm.setFormat(f) },
                        shape = SegmentedButtonDefaults.itemShape(i, AudioFormat.entries.size),
                    ) { Text(f.label) }
                }
            }

            Text("Default MP3 quality", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AudioQuality.entries.forEachIndexed { i, q ->
                    SegmentedButton(
                        selected = quality == q,
                        onClick = { vm.setQuality(q) },
                        shape = SegmentedButtonDefaults.itemShape(i, AudioQuality.entries.size),
                    ) { Text(q.label) }
                }
            }

            HorizontalDivider()

            Text("Engine", style = MaterialTheme.typography.titleMedium)
            Text(
                "yt-dlp ${engineVersion ?: "bundled"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.updateMessage != null) {
                Text(
                    vm.updateMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = vm::checkUpdate, enabled = !vm.checking) {
                    Text("Check for update")
                }
                Button(onClick = vm::doUpdate, enabled = !vm.checking) { Text("Update engine") }
            }

            HorizontalDivider()

            Text("Background downloads", style = MaterialTheme.typography.titleMedium)
            Text(
                "If downloads stop with the screen off, exempt YT2MP3 from battery optimization.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                        ),
                    )
                }
            }) { Text("Open battery settings") }

            HorizontalDivider()

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "YT2MP3 1.1.0 — personal use. Only save content you own or are permitted to keep.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
