package com.example.yt2mp3.ui.settings

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.data.util.FileNamePattern
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.SubtitleMode
import com.example.yt2mp3.domain.model.ThemeMode
import com.example.yt2mp3.domain.model.VideoFormat
import com.example.yt2mp3.domain.model.VideoQuality
import com.example.yt2mp3.service.ScheduleManager
import com.example.yt2mp3.ui.theme.AppSpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

class SettingsViewModel(app: Application) : ViewModel() {
    private val yt = app as Yt2Mp3App
    val destination = yt.settings.observeDestinationUri()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val quality = yt.settings.observeDefaultQuality()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioQuality.DEFAULT)
    val format = yt.settings.observeDefaultFormat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioFormat.DEFAULT)
    val mediaKind = yt.settings.observeMediaKind()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaKind.AUDIO)
    val videoFormat = yt.settings.observeVideoFormat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoFormat.DEFAULT)
    val videoQuality = yt.settings.observeVideoQuality()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoQuality.DEFAULT)
    val subtitleMode = yt.settings.observeSubtitleMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleMode.NONE)
    val themeMode = yt.settings.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    // One-shot editable values (loaded once, saved on change).
    var subLangs by mutableStateOf("en,id")
        private set
    var postChapters by mutableStateOf(false)
        private set
    var postSponsor by mutableStateOf(false)
        private set
    var postNormalize by mutableStateOf(false)
        private set
    var postThumb by mutableStateOf(false)
        private set
    var postStrip by mutableStateOf(false)
        private set
    var pattern by mutableStateOf(FileNamePattern.DEFAULT)
        private set
    var subfolderAuto by mutableStateOf(true)
        private set
    var skipExisting by mutableStateOf(false)
        private set
    var parallel by mutableStateOf(1)
        private set
    var retries by mutableStateOf(2)
        private set
    var fragRetries by mutableStateOf(3)
        private set
    var rateLimit by mutableStateOf("0")
        private set
    var wifiOnly by mutableStateOf(false)
        private set
    var proxy by mutableStateOf("")
        private set
    var userAgent by mutableStateOf("")
        private set
    var ytdlpArgs by mutableStateOf("")
        private set
    var ffmpegArgs by mutableStateOf("")
        private set
    var customFormat by mutableStateOf("")
        private set
    var debugMode by mutableStateOf(false)
        private set
    var saveHistory by mutableStateOf(true)
        private set
    var simpleMode by mutableStateOf(true)
        private set
    var autoPaste by mutableStateOf(false)
        private set
    var dynamicColor by mutableStateOf(true)
        private set
    var lockEnabled by mutableStateOf(false)
        private set
    var hasPin by mutableStateOf(false)
        private set
    var schedEnabled by mutableStateOf(false)
        private set
    var schedHour by mutableStateOf(2)
        private set
    var schedMin by mutableStateOf(0)
        private set
    var rssChannel by mutableStateOf("")
        private set
    var rssAuto by mutableStateOf(false)
        private set
    var storageUsage by mutableStateOf<String?>(null)
        private set

    var engineVersion by mutableStateOf<String?>(null)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    fun postNotice(v: String?) {
        notice = v
    }

    init {
        viewModelScope.launch {
            engineVersion = yt.engineManager.currentVersion()
        }
        viewModelScope.launch {
            val s = yt.settings
            subLangs = s.getSubLangs()
            val post = s.getPostFlags()
            postChapters = post.embedChapters
            postSponsor = post.sponsorBlock
            postNormalize = post.normalizeAudio
            postThumb = post.saveThumbSeparate
            postStrip = post.stripMetadata
            pattern = s.getRenamePattern()
            subfolderAuto = s.getSubfolderPerPlaylist()
            skipExisting = s.getSkipExisting()
            parallel = s.getMaxParallel()
            retries = s.getMaxRetries()
            fragRetries = s.getFragmentRetries()
            rateLimit = s.getRateLimitKbps().toString()
            wifiOnly = s.getWifiOnly()
            proxy = s.getProxyUrl()
            userAgent = s.getUserAgent()
            ytdlpArgs = s.getCustomYtdlpArgs()
            ffmpegArgs = s.getCustomFfmpegArgs()
            customFormat = s.getCustomFormat()
            debugMode = s.getDebugMode()
            saveHistory = s.getSaveHistory()
            simpleMode = s.getSimpleMode()
            autoPaste = s.getAutoPaste()
            dynamicColor = s.getDynamicColor()
            lockEnabled = s.getLockEnabled()
            hasPin = s.getPinHash().isNotEmpty()
            val (se, t) = s.getSchedule()
            schedEnabled = se
            schedHour = t.first
            schedMin = t.second
            rssChannel = s.getRssChannel()
            rssAuto = s.getRssAuto()
        }
        viewModelScope.launch {
            val bytes = yt.container.storage.storageUsageBytes()
            storageUsage = com.example.yt2mp3.domain.model.SizeEstimator.formatBytes(bytes.coerceAtLeast(1))
        }
    }

    private fun savePost() = viewModelScope.launch {
        yt.settings.setPostFlags(
            com.example.yt2mp3.domain.model.PostOptions(
                subtitleMode = subtitleMode.value,
                subLangs = subLangs,
                embedChapters = postChapters,
                sponsorBlock = postSponsor,
                normalizeAudio = postNormalize,
                saveThumbSeparate = postThumb,
                stripMetadata = postStrip,
            ),
        )
    }

    fun setFormat(f: AudioFormat) = viewModelScope.launch { yt.settings.setDefaultFormat(f) }
    fun setMediaKind(k: MediaKind) = viewModelScope.launch { yt.settings.setMediaKind(k) }
    fun setVideoFormat(f: VideoFormat) = viewModelScope.launch { yt.settings.setVideoFormat(f) }
    fun setVideoQuality(q: VideoQuality) = viewModelScope.launch { yt.settings.setVideoQuality(q) }
    fun setSubtitle(m: SubtitleMode) = viewModelScope.launch {
        yt.settings.setSubtitleMode(m)
        savePost()
    }

    fun setQuality(q: AudioQuality) = viewModelScope.launch {
        yt.settings.setDefaultQuality(q)
    }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { yt.settings.setThemeMode(m) }
    fun setDynamic(v: Boolean) = viewModelScope.launch {
        dynamicColor = v
        yt.settings.setDynamicColor(v)
        notice = "Warna dinamis berlaku setelah restart aplikasi."
    }

    fun updateSubLangs(v: String) {
        subLangs = v.take(60)
        viewModelScope.launch {
            yt.settings.setSubLangs(v.take(60))
            savePost()
        }
    }

    fun togglePost(kind: String, v: Boolean) {
        when (kind) {
            "chapters" -> postChapters = v
            "sponsor" -> postSponsor = v
            "norm" -> postNormalize = v
            "thumb" -> postThumb = v
            "strip" -> postStrip = v
        }
        savePost()
    }

    fun updatePattern(v: String) {
        pattern = v.take(120)
        viewModelScope.launch { yt.settings.setRenamePattern(v.take(120)) }
    }

    fun setSubfolder(v: Boolean) {
        subfolderAuto = v
        viewModelScope.launch { yt.settings.setSubfolderPerPlaylist(v) }
    }

    fun setSkip(v: Boolean) {
        skipExisting = v
        viewModelScope.launch { yt.settings.setSkipExisting(v) }
    }

    fun updateParallel(v: Int) {
        parallel = v.coerceIn(1, 3)
        viewModelScope.launch { yt.settings.setMaxParallel(v.coerceIn(1, 3)) }
    }

    fun updateRetries(v: Int) {
        retries = v.coerceIn(0, 5)
        viewModelScope.launch {
            yt.settings.setMaxRetries(v.coerceIn(0, 5))
            yt.container.refreshTuningAsync()
        }
    }

    fun updateFragRetries(v: Int) {
        fragRetries = v.coerceIn(0, 10)
        viewModelScope.launch {
            yt.settings.setFragmentRetries(v.coerceIn(0, 10))
            yt.container.refreshTuningAsync()
        }
    }

    fun setRate(v: String) {
        rateLimit = v.filter(Char::isDigit).take(7)
        viewModelScope.launch {
            yt.settings.setRateLimitKbps(rateLimit.toIntOrNull() ?: 0)
            yt.container.refreshTuningAsync()
        }
    }

    fun setWifi(v: Boolean) {
        wifiOnly = v
        viewModelScope.launch { yt.settings.setWifiOnly(v) }
    }

    fun updateProxy(v: String) {
        proxy = v.trim().take(200)
        viewModelScope.launch {
            yt.settings.setProxyUrl(v.trim().take(200))
            yt.container.refreshTuningAsync()
        }
    }

    fun setUA(v: String) {
        userAgent = v.take(300)
        viewModelScope.launch {
            yt.settings.setUserAgent(v.take(300))
            yt.container.refreshTuningAsync()
        }
    }

    fun updateYtdlpArgs(v: String) {
        ytdlpArgs = v.take(1000)
        viewModelScope.launch {
            yt.settings.setCustomYtdlpArgs(v.take(1000))
            yt.container.refreshTuningAsync()
        }
    }

    fun updateFfmpegArgs(v: String) {
        ffmpegArgs = v.take(500)
        viewModelScope.launch {
            yt.settings.setCustomFfmpegArgs(v.take(500))
            yt.container.refreshTuningAsync()
        }
    }

    fun updateCustomFormat(v: String) {
        customFormat = v.trim().take(200)
        viewModelScope.launch {
            yt.settings.setCustomFormat(v.trim().take(200))
            yt.container.refreshTuningAsync()
        }
    }

    fun setDebug(v: Boolean) {
        debugMode = v
        viewModelScope.launch {
            yt.settings.setDebugMode(v)
            yt.container.refreshTuningAsync()
        }
    }

    fun setHistory(v: Boolean) {
        saveHistory = v
        viewModelScope.launch { yt.settings.setSaveHistory(v) }
    }

    fun setSimple(v: Boolean) {
        simpleMode = v
        viewModelScope.launch { yt.settings.setSimpleMode(v) }
    }

    fun updateAutoPaste(v: Boolean) {
        autoPaste = v
        viewModelScope.launch { yt.settings.setAutoPaste(v) }
    }

    fun setLock(v: Boolean) {
        lockEnabled = v
        viewModelScope.launch { yt.settings.setLockEnabled(v && hasPin) }
        if (v && !hasPin) notice = "Buat PIN dulu di tab yang terkunci saat restart, atau matikan kunci."
    }

    fun setPin(pin: String) {
        if (pin.length < 4) {
            notice = "PIN minimal 4 digit."
            return
        }
        viewModelScope.launch {
            yt.settings.setPinHash(com.example.yt2mp3.ui.lock.sha256Hex(pin))
            yt.settings.setLockEnabled(true)
            hasPin = true
            lockEnabled = true
            notice = "Kunci aplikasi aktif."
        }
    }

    fun removePin() = viewModelScope.launch {
        yt.settings.setPinHash("")
        yt.settings.setLockEnabled(false)
        hasPin = false
        lockEnabled = false
    }

    fun setSched(enabled: Boolean, hour: Int = schedHour, min: Int = schedMin, context: android.content.Context? = null) {
        schedEnabled = enabled
        schedHour = hour.coerceIn(0, 23)
        schedMin = min.coerceIn(0, 59)
        viewModelScope.launch {
            yt.settings.setSchedule(enabled, schedHour, schedMin)
            context?.let {
                if (enabled) ScheduleManager.rescheduleIfEnabled(it) else ScheduleManager.cancel(it)
            }
        }
    }

    fun setRss(channel: String, auto: Boolean) {
        rssChannel = channel.trim().take(200)
        rssAuto = auto
        viewModelScope.launch {
            yt.settings.setRssChannel(channel.trim().take(200))
            yt.settings.setRssAuto(auto)
        }
    }

    fun rssCheckNow() = viewModelScope.launch {
        checking = true
        notice = "Memeriksa RSS…"
        val n = runCatching { com.example.yt2mp3.service.ScheduleReceiver.rssCheck(yt) }
        notice = n.fold(
            onSuccess = { if (it > 0) "$it video baru masuk antrean." else "Tidak ada video baru." },
            onFailure = { "RSS gagal: ${it.message?.take(120)}" },
        )
        checking = false
    }

    fun setHideGallery(hide: Boolean) = viewModelScope.launch {
        val ok = yt.container.storage.setHideFromGallery(hide)
        notice = if (ok) {
            if (hide) "File disembunyikan dari galeri (.nomedia)." else ".nomedia dihapus."
        } else {
            "Gagal. Pilih folder dulu."
        }
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

    /** Exports settings (not media/history) as JSON for backup. */
    suspend fun buildBackupJson(): String {
        val s = yt.settings
        return JSONObject()
            .put("quality", s.getDefaultQuality().bitrateKbps)
            .put("format", s.observeDefaultFormat().first().name)
            .put("pattern", s.getRenamePattern())
            .put("subLangs", s.getSubLangs())
            .put("proxy", s.getProxyUrl())
            .put("ua", s.getUserAgent())
            .put("ytdlpArgs", s.getCustomYtdlpArgs())
            .put("ffmpegArgs", s.getCustomFfmpegArgs())
            .put("customFormat", s.getCustomFormat())
            .put("rate", s.getRateLimitKbps())
            .put("retries", s.getMaxRetries())
            .put("frag", s.getFragmentRetries())
            .put("parallel", s.getMaxParallel())
            .put("wifi", s.getWifiOnly())
            .put("history", s.getSaveHistory())
            .put("theme", s.observeThemeMode().first().name)
            .put("profiles", s.getProfilesJson())
            .toString()
    }

    fun restoreBackupJson(json: String) = viewModelScope.launch {
        runCatching {
            val o = JSONObject(json)
            o.optInt("quality", -1).takeIf { it > 0 }?.let { yt.settings.setDefaultQuality(AudioQuality.fromBitrate(it)) }
            o.optString("format").takeIf { it.isNotBlank() }?.let { yt.settings.setDefaultFormat(AudioFormat.fromName(it)) }
            if (o.has("pattern")) updatePattern(o.optString("pattern"))
            if (o.has("subLangs")) updateSubLangs(o.optString("subLangs"))
            if (o.has("proxy")) updateProxy(o.optString("proxy"))
            if (o.has("ua")) setUA(o.optString("ua"))
            if (o.has("ytdlpArgs")) updateYtdlpArgs(o.optString("ytdlpArgs"))
            if (o.has("ffmpegArgs")) updateFfmpegArgs(o.optString("ffmpegArgs"))
            if (o.has("customFormat")) updateCustomFormat(o.optString("customFormat"))
            if (o.has("rate")) setRate(o.optInt("rate").toString())
            if (o.has("retries")) updateRetries(o.optInt("retries"))
            if (o.has("frag")) updateFragRetries(o.optInt("frag"))
            if (o.has("parallel")) updateParallel(o.optInt("parallel"))
            if (o.has("wifi")) setWifi(o.optBoolean("wifi"))
            if (o.has("history")) setHistory(o.optBoolean("history", true))
            o.optString("theme").takeIf { it.isNotBlank() }?.let {
                setTheme(com.example.yt2mp3.domain.model.ThemeMode.fromName(it))
            }
            if (o.has("profiles")) yt.settings.setProfilesJson(o.optString("profiles"))
            notice = "Pengaturan dipulihkan."
        }.onFailure { notice = "Backup tidak valid." }
    }

    fun diagnostics(): String {
        return buildString {
            appendLine("YT2MP3 1.4.0")
            appendLine("Engine: ${engineVersion ?: "?"}")
            appendLine("Tujuan: ${destination.value?.takeLast(40) ?: "-"}")
            appendLine("Output: ${mediaKind.value}/${format.value.label} ${quality.value.label} / " +
                "${videoFormat.value.label} ${videoQuality.value.label}")
            appendLine("Paralel: $parallel Retry: $retries Frag: $fragRetries Rate: ${rateLimit}kbps")
            appendLine("WiFi-only: $wifiOnly Proxy: ${if (proxy.isBlank()) "-" else "ya"}")
            appendLine("Custom yt-dlp: ${ytdlpArgs.ifBlank { "-" }}")
            appendLine("Custom ffmpeg: ${ffmpegArgs.ifBlank { "-" }}")
            appendLine("Custom format: ${customFormat.ifBlank { "-" }}")
            appendLine("Debug: $debugMode")
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(app) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val dest by vm.destination.collectAsState()
    val quality by vm.quality.collectAsState()
    val format by vm.format.collectAsState()
    val mediaKind by vm.mediaKind.collectAsState()
    val videoFormat by vm.videoFormat.collectAsState()
    val videoQuality by vm.videoQuality.collectAsState()
    val subtitleMode by vm.subtitleMode.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val engineVersion = vm.engineVersion

    var showLog by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }

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
    val backupCreate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = vm.buildBackupJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    vm.postNotice("Backup tersimpan.")
                }
            }
            // notice state lives in vm; force recompose via dummy read below
        }
    }
    val backupOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
                if (!text.isNullOrBlank()) vm.restoreBackupJson(text)
            }
        }
    }

    // Almost static screen (§29): sections, plain selection, no custom animation.
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(section?.title ?: "Settings") },
            navigationIcon = {
                if (section != null) {
                    IconButton(onClick = { section = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
        ) {
            vm.notice?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            if (section == null) {
                SettingsMenu(onOpen = { section = it })
            }
            if (section == SettingsSection.STORAGE) {
            Section("Penyimpanan")
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
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = { picker.launch(null) }) { Text("Change folder") }
                OutlinedButton(onClick = { vm.setHideGallery(true) }) { Text("Sembunyikan dari galeri") }
            }
            vm.storageUsage?.let {
                Text("Terpakai di folder tujuan: ± $it", style = MaterialTheme.typography.bodySmall)
            }
            ToggleRow("Subfolder per playlist", "Simpan playlist/batch ke subfolder.", vm.subfolderAuto, vm::setSubfolder)
            ToggleRow("Lewati yang sudah ada", "Jangan antre ulang link di riwayat.", vm.skipExisting, vm::setSkip)
            OutlinedTextField(
                value = vm.pattern,
                onValueChange = vm::updatePattern,
                label = { Text("Pola nama file") },
                placeholder = { Text("{title} - {artist} [{id}]") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "Placeholder: {title} {artist} {id} {site} {date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = { backupCreate.launch("yt2mp3-backup.json") }) { Text("Backup pengaturan") }
                OutlinedButton(onClick = { backupOpen.launch(arrayOf("application/json", "text/plain")) }) { Text("Restore") }
            }

            }
            if (section == SettingsSection.OUTPUT) {
            Section("Output default")
            Text("Jenis", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MediaKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = mediaKind == k,
                        onClick = { vm.setMediaKind(k) },
                        shape = SegmentedButtonDefaults.itemShape(i, MediaKind.entries.size),
                    ) { Text(if (k == MediaKind.AUDIO) "Audio" else "Video") }
                }
            }
            Text("Format audio", style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                AudioFormat.entries.forEach { f ->
                    FilterChip(selected = format == f, onClick = { vm.setFormat(f) }, label = { Text(f.label) })
                }
            }
            Text("Quality audio (lossy)", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AudioQuality.entries.forEachIndexed { i, q ->
                    SegmentedButton(
                        selected = quality == q,
                        onClick = { vm.setQuality(q) },
                        shape = SegmentedButtonDefaults.itemShape(i, AudioQuality.entries.size),
                    ) { Text(q.label) }
                }
            }
            Text("Format video", style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                VideoFormat.entries.forEach { f ->
                    FilterChip(selected = videoFormat == f, onClick = { vm.setVideoFormat(f) }, label = { Text(f.label) })
                }
                VideoQuality.entries.forEach { q ->
                    FilterChip(selected = videoQuality == q, onClick = { vm.setVideoQuality(q) }, label = { Text(q.label) })
                }
            }
            Text("Subtitle", style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                SubtitleMode.entries.forEach { m ->
                    FilterChip(selected = subtitleMode == m, onClick = { vm.setSubtitle(m) }, label = { Text(m.label) })
                }
            }
            OutlinedTextField(
                value = vm.subLangs,
                onValueChange = vm::updateSubLangs,
                label = { Text("Bahasa subtitle") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ToggleRow("Embed chapters", "Simpan penanda bab.", vm.postChapters) { vm.togglePost("chapters", it) }
            ToggleRow("SponsorBlock", "Hapus segmen sponsor.", vm.postSponsor) { vm.togglePost("sponsor", it) }
            ToggleRow("Normalisasi loudness", "Samakan volume.", vm.postNormalize) { vm.togglePost("norm", it) }
            ToggleRow("Thumbnail terpisah", "Simpan cover JPG.", vm.postThumb) { vm.togglePost("thumb", it) }
            ToggleRow("Hapus metadata (privasi)", "Tanpa embed tag/thumbnail.", vm.postStrip) { vm.togglePost("strip", it) }

            }
            if (section == SettingsSection.QUEUE) {
            Section("Antrean & jaringan")
            CounterRow("Unduhan paralel", vm.parallel, 1, 3, vm::updateParallel)
            CounterRow("Auto-retry gagal", vm.retries, 0, 5, vm::updateRetries)
            CounterRow("Fragment retry", vm.fragRetries, 0, 10, vm::updateFragRetries)
            OutlinedTextField(
                value = vm.rateLimit,
                onValueChange = vm::setRate,
                label = { Text("Batas kecepatan (KB/s, 0 = tanpa batas)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ToggleRow("Hanya saat Wi-Fi", "Jeda antrean di data seluler.", vm.wifiOnly, vm::setWifi)

            }
            if (section == SettingsSection.SCHEDULE) {
            Section("Jadwal & RSS publik")
            ToggleRow(
                "Unduh terjadwal ${"%02d:%02d".format(vm.schedHour, vm.schedMin)}",
                "Jalankan antrean tiap hari jam tersebut.",
                vm.schedEnabled,
            ) { vm.setSched(it, vm.schedHour, vm.schedMin, context) }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = "%02d".format(vm.schedHour),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { vm.setSched(vm.schedEnabled, it, vm.schedMin, context) }
                    },
                    label = { Text("Jam") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = "%02d".format(vm.schedMin),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { vm.setSched(vm.schedEnabled, vm.schedHour, it, context) }
                    },
                    label = { Text("Menit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = vm.rssChannel,
                onValueChange = { vm.setRss(it, vm.rssAuto) },
                label = { Text("Channel RSS (UC id / link /channel/UC…)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ToggleRow("Auto-download RSS", "Saat jadwal jalan, ambil video baru (maks 5).", vm.rssAuto) {
                vm.setRss(vm.rssChannel, it)
            }
            OutlinedButton(onClick = vm::rssCheckNow, enabled = !vm.checking) { Text("Periksa RSS sekarang") }

            }
            if (section == SettingsSection.DISPLAY) {
            Section("Tampilan")
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                com.example.yt2mp3.domain.model.ThemeMode.entries.forEach { m ->
                    FilterChip(selected = themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(m.label) })
                }
            }
            ToggleRow("Warna dinamis (Material You)", "Berlaku setelah restart.", vm.dynamicColor, vm::setDynamic)
            ToggleRow("Tempel otomatis (clipboard)", "Saat buka app dan input kosong.", vm.autoPaste, vm::updateAutoPaste)
            ToggleRow("Mode sederhana", "Sembunyikan profil & opsi lanjutan di Home.", vm.simpleMode, vm::setSimple)
            }
            if (section == SettingsSection.PRIVACY) {
            Section("Privasi & kunci")
            ToggleRow("Simpan riwayat", "Matikan = mode incognito (file tetap tersimpan).", vm.saveHistory, vm::setHistory)
            ToggleRow("Kunci aplikasi (PIN)", if (vm.hasPin) "PIN terpasang." else "Belum ada PIN.", vm.lockEnabled && vm.hasPin) {
                if (it && !vm.hasPin) showPin = true else vm.setLock(it)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = { showPin = true }) { Text(if (vm.hasPin) "Ganti PIN" else "Buat PIN") }
                if (vm.hasPin) {
                    OutlinedButton(onClick = vm::removePin) { Text("Hapus PIN") }
                }
            }

            }
            if (section == SettingsSection.ADVANCED) {
            Section("Jaringan lanjutan (tanpa login)")
            OutlinedTextField(
                value = vm.proxy,
                onValueChange = vm::updateProxy,
                label = { Text("Proxy (http/https/socks5, mis. Tor/Orbot)") },
                placeholder = { Text("socks5://127.0.0.1:9050") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.userAgent,
                onValueChange = vm::setUA,
                label = { Text("User-Agent kustom (kosong = default)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.ytdlpArgs,
                onValueChange = vm::updateYtdlpArgs,
                label = { Text("Argumen yt-dlp tambahan") },
                placeholder = { Text("--embed-chapters --sponsorblock-remove sponsor") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Token login/cookies/DRM otomatis dibuang.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = vm.ffmpegArgs,
                onValueChange = vm::updateFfmpegArgs,
                label = { Text("Argumen FFmpeg tambahan") },
                placeholder = { Text("-af loudnorm=I=-16:TP=-1.5:LRA=11") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.customFormat,
                onValueChange = vm::updateCustomFormat,
                label = { Text("Format selection string (advanced, kosong = otomatis)") },
                placeholder = { Text("bestaudio/best") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ToggleRow("Mode debug", "Log verbose + info diagnostik.", vm.debugMode, vm::setDebug)

            }
            if (section == SettingsSection.ENGINE) {
            Section("Engine")
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

            }
            if (section == SettingsSection.HELP) {
            Section("Bantuan & maintenance")
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = { showLog = true }) { Text("Lihat log") }
                OutlinedButton(onClick = { showChangelog = true }) { Text("Changelog") }
                OutlinedButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.diagnostics()))
                    vm.postNotice("Info diagnostik disalin.")
                }) { Text("Salin diagnostik") }
            }
            Text(
                "Otomasi: kirim broadcast com.example.yt2mp3.action.ENQUEUE dengan extra String[] urls " +
                    "(Tasker/MacroDroid). Quick Settings tile + App Shortcuts tersedia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            }
            if (section == SettingsSection.BATTERY) {
            Section("Background downloads")
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

            }
            if (section == SettingsSection.LEGAL) {
            Section("Legal")
            Text(
                "YT2MP3 1.4.0 — hanya konten publik yang boleh disimpan untuk offline pribadi. " +
                    "Tanpa login/cookies: konten privat, berbayar, dan DRM tidak didukung dan tidak di-bypass. " +
                    "Tanpa watermark stripping. Patuhi syarat layanan tiap situs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Lisensi MIT (lihat LICENSE di repo). Komponen pihak ketiga " +
                    "(yt-dlp, FFmpeg, AndroidX) ikut lisensinya masing-masing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }
    }

    if (showLog) {
        val entries = remember(showLog) { AppLog.snapshot().reversed() }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Log (${entries.size})") },
            text = {
                LazyColumn {
                    items(entries.take(200)) { e ->
                        Text(
                            "${e.level} ${e.tag}: ${e.msg}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    TextButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(AppLog.exportText()))
                    }) { Text("Salin") }
                    TextButton(onClick = { AppLog.clear() }) { Text("Hapus") }
                    TextButton(onClick = { showLog = false }) { Text("Tutup") }
                }
            },
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("Changelog 1.4.0") },
            text = {
                Text(
                    "• Video MP4/MKV/WEBM 144p–4K\n" +
                        "• Audio OPUS/OGG/FLAC/WAV + MP3 64–320k\n" +
                        "• Batch + import TXT/CSV/JSON + range + lewati-ada + subfolder\n" +
                        "• Channel/playlist generik, profil, pola nama file\n" +
                        "• Subtitle/chapters/SponsorBlock/normalisasi/thumbnail\n" +
                        "• Paralel 1–3, auto-retry, Wi-Fi only, batas kecepatan\n" +
                        "• Jadwal harian, RSS publik, otomasi broadcast, tile, shortcuts\n" +
                        "• Tema AMOLED/dinamis, clipboard monitor, disclaimer\n" +
                        "• Favorit, cari/sortir, duplikat, rename/share, player bawaan\n" +
                        "• PIN/biometrik, incognito, proxy, UA kustom, log viewer, backup",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("Tutup") } },
        )
    }

    if (showPin) {
        AlertDialog(
            onDismissRequest = { showPin = false; pin1 = ""; pin2 = "" },
            title = { Text(if (vm.hasPin) "Ganti PIN" else "Buat PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    OutlinedTextField(
                        value = pin1,
                        onValueChange = { pin1 = it.filter(Char::isDigit).take(12) },
                        label = { Text("PIN baru (4+ digit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { pin2 = it.filter(Char::isDigit).take(12) },
                        label = { Text("Ulangi PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pin1 != pin2 || pin1.length < 4) {
                        vm.postNotice("PIN tidak sama / kurang dari 4 digit.")
                    } else {
                        vm.setPin(pin1)
                        showPin = false
                    }
                    pin1 = ""
                    pin2 = ""
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showPin = false; pin1 = ""; pin2 = "" }) { Text("Batal") }
            },
        )
    }
}

enum class SettingsSection(val title: String, val desc: String) {
    STORAGE("Penyimpanan", "Folder, pola nama, backup"),
    OUTPUT("Output", "Audio/video, subtitle, chapters"),
    QUEUE("Antrean & jaringan", "Paralel, retry, Wi-Fi"),
    SCHEDULE("Jadwal & RSS", "Otomatis harian, channel"),
    DISPLAY("Tampilan", "Tema, mode sederhana"),
    PRIVACY("Privasi & kunci", "PIN, incognito"),
    ADVANCED("Lanjutan", "Proxy, argumen kustom"),
    ENGINE("Engine", "Update yt-dlp"),
    HELP("Bantuan", "Log, changelog, diagnostik"),
    BATTERY("Background", "Agar jalan saat layar mati"),
    LEGAL("Legal", "Hak cipta & batasan"),
}

@Composable
private fun SettingsMenu(onOpen: (SettingsSection) -> Unit) {
    val icons = mapOf(
        SettingsSection.STORAGE to Icons.Filled.FolderOpen,
        SettingsSection.OUTPUT to Icons.Filled.MusicNote,
        SettingsSection.QUEUE to Icons.AutoMirrored.Filled.List,
        SettingsSection.SCHEDULE to Icons.Filled.Schedule,
        SettingsSection.DISPLAY to Icons.Filled.Palette,
        SettingsSection.PRIVACY to Icons.Filled.Lock,
        SettingsSection.ADVANCED to Icons.Filled.Tune,
        SettingsSection.ENGINE to Icons.Filled.SystemUpdate,
        SettingsSection.HELP to Icons.AutoMirrored.Filled.Help,
        SettingsSection.BATTERY to Icons.Filled.Info,
        SettingsSection.LEGAL to Icons.Filled.Info,
    )
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        SettingsSection.entries.forEach { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(s) }
                    .padding(vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.group),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icons[s] ?: Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        s.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun HDiv() {
    HorizontalDivider()
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CounterRow(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("-") }
        Text("$value", modifier = Modifier.padding(horizontal = AppSpacing.sm))
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}
