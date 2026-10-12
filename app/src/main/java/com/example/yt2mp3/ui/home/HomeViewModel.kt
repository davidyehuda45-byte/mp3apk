package com.example.yt2mp3.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.data.util.FileNamePattern
import com.example.yt2mp3.data.util.InputParser
import com.example.yt2mp3.data.util.ParsedInput
import com.example.yt2mp3.data.util.PlaylistRange
import com.example.yt2mp3.data.util.Profiles
import com.example.yt2mp3.data.util.SupportedSites
import com.example.yt2mp3.data.util.UrlValidator
import com.example.yt2mp3.domain.model.AppError
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadProfile
import com.example.yt2mp3.domain.model.DownloadRequest
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.PlaylistInfo
import com.example.yt2mp3.domain.model.PostOptions
import com.example.yt2mp3.domain.model.SizeEstimator
import com.example.yt2mp3.domain.model.SubtitleMode
import com.example.yt2mp3.domain.model.TimeFormat
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoFormat
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.domain.model.VideoQuality
import com.example.yt2mp3.domain.model.errorDetail
import com.example.yt2mp3.domain.model.toDownloadErrorKind
import com.example.yt2mp3.domain.model.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** PRD §27: sealed UI state, StateFlow, survives rotation (ViewModel, not Compose memory). */
sealed interface HomeContent {
    data object Idle : HomeContent
    data class Loading(val label: String) : HomeContent
    data class Video(val meta: VideoMetadata) : HomeContent
    data class SearchResults(val query: String, val results: List<VideoEntry>) : HomeContent
    data class Playlist(val info: PlaylistInfo, val selected: Set<Int>) : HomeContent
    /** Batch paste: per-URL preview rows resolved via fetchMetadata. */
    data class Batch(val items: List<BatchItem>, val selected: Set<Int>) : HomeContent
    data class Error(val title: String, val message: String, val canRetry: Boolean = true) : HomeContent
}

data class BatchItem(
    val url: String,
    val meta: VideoMetadata?,
    val error: String?,
)

data class VideoEdits(
    val title: String = "",
    val artist: String = "",
    val trimEnabled: Boolean = false,
    val trimStart: String = "",
    val trimEnd: String = "",
)

data class HomeScreenState(
    val input: String = "",
    val content: HomeContent = HomeContent.Idle,
    val mediaKind: MediaKind = MediaKind.AUDIO,
    val format: AudioFormat = AudioFormat.DEFAULT,
    val quality: AudioQuality = AudioQuality.DEFAULT,
    val videoFormat: VideoFormat = VideoFormat.DEFAULT,
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    val post: PostOptions = PostOptions(),
    val destinationLabel: String? = null,
    val edits: VideoEdits = VideoEdits(),
    /** Results to return to after opening one of them. */
    val backToResults: HomeContent.SearchResults? = null,
    /** Short confirmation, e.g. after queueing a playlist. */
    val notice: String? = null,
    /** False after the current preview was queued, until something changes. */
    val canDownload: Boolean = false,
    /** Playlist/batch range text, e.g. "1-10,15". Blank = all selected. */
    val rangeText: String = "",
    val rangeError: String? = null,
    val skipExisting: Boolean = false,
    val subfolderAuto: Boolean = true,
    val showAdvanced: Boolean = false,
    val profiles: List<DownloadProfile> = emptyList(),
    val activeProfile: String? = null,
) {
    val parsed: ParsedInput get() = InputParser.parse(input)

    val trimCheck: TimeFormat.TrimResult?
        get() {
            val v = content as? HomeContent.Video ?: return null
            if (!edits.trimEnabled) return null
            return TimeFormat.validateTrim(edits.trimStart, edits.trimEnd, v.meta.durationSecs)
        }

    val estimate: SizeEstimator.Estimate?
        get() = when (val c = content) {
            is HomeContent.Video -> {
                if (mediaKind == MediaKind.VIDEO) {
                    SizeEstimator.forVideoDownload(c.meta.durationSecs, videoQuality)
                } else {
                    val trim = trimCheck as? TimeFormat.TrimResult.Ok
                    SizeEstimator.forVideo(c.meta, format, quality, trim?.start, trim?.end)
                }
            }
            is HomeContent.Playlist ->
                c.selected.takeIf { it.isNotEmpty() }?.let { sel ->
                    val durs = sel.map { c.info.entries[it].durationSecs }
                    if (mediaKind == MediaKind.VIDEO) SizeEstimator.forVideoDurations(durs, videoQuality)
                    else SizeEstimator.forDurations(durs, format, quality)
                }
            is HomeContent.Batch ->
                c.selected.takeIf { it.isNotEmpty() }?.let { sel ->
                    val durs = sel.mapNotNull { c.items.getOrNull(it)?.meta?.durationSecs }
                    if (mediaKind == MediaKind.VIDEO) SizeEstimator.forVideoDurations(durs, videoQuality)
                    else SizeEstimator.forDurations(durs, format, quality)
                }
            else -> null
        }

    /** Effective selection after applying the range filter (playlist/batch). */
    fun rangedIndices(size: Int, selected: Set<Int>): Set<Int> {
        if (rangeText.isBlank()) return selected
        return PlaylistRange.parse(rangeText, size)?.intersect(selected) ?: selected
    }
}

class HomeViewModel(app: Application) : ViewModel() {
    private val yt = app as Yt2Mp3App
    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    private var lastDownloadId: Long? = null
    private var loadJob: Job? = null
    private val _tracked = MutableStateFlow<DownloadItem?>(null)

    /** Live item backing the primary-button states (§20). Null when idle. */
    val tracked: StateFlow<DownloadItem?> = _tracked.asStateFlow()

    init {
        viewModelScope.launch {
            yt.settings.observeDestinationUri().collect { uri ->
                _state.update { it.copy(destinationLabel = uri?.let(::folderLabel)) }
            }
        }
        viewModelScope.launch {
            yt.settings.observeDefaultQuality().collect { q -> _state.update { it.copy(quality = q) } }
        }
        viewModelScope.launch {
            yt.settings.observeDefaultFormat().collect { f -> _state.update { it.copy(format = f) } }
        }
        viewModelScope.launch {
            yt.settings.observeMediaKind().collect { k -> _state.update { it.copy(mediaKind = k) } }
        }
        viewModelScope.launch {
            yt.settings.observeVideoFormat().collect { f -> _state.update { it.copy(videoFormat = f) } }
        }
        viewModelScope.launch {
            yt.settings.observeVideoQuality().collect { q -> _state.update { it.copy(videoQuality = q) } }
        }
        viewModelScope.launch {
            yt.settings.observeSubtitleMode().collect { m ->
                _state.update { it.copy(post = it.post.copy(subtitleMode = m)) }
            }
        }
        viewModelScope.launch {
            val post = runCatching { yt.settings.getPostFlags() }.getOrDefault(PostOptions())
            val skip = runCatching { yt.settings.getSkipExisting() }.getOrDefault(false)
            val sub = runCatching { yt.settings.getSubfolderPerPlaylist() }.getOrDefault(true)
            val profiles = Profiles.decode(runCatching { yt.settings.getProfilesJson() }.getOrDefault(""))
            _state.update { it.copy(post = post, skipExisting = skip, subfolderAuto = sub, profiles = profiles) }
        }
        // Track the enqueued item through the queue to terminal state.
        viewModelScope.launch {
            yt.downloads.observeQueue().collect { queue ->
                val id = lastDownloadId ?: return@collect
                val active = queue.firstOrNull { it.id == id }
                if (active != null) {
                    _tracked.value = active
                } else if (_tracked.value?.status?.isTerminal == false || _tracked.value == null) {
                    // Left the active queue: fetch final state once from history.
                    _tracked.value = yt.downloads.get(id)
                }
            }
        }
    }

    // ------------------------------------------------------------------ input

    fun onInputChange(v: String) {
        _state.update { it.copy(input = v, notice = null) }
    }

    fun onSharedText(text: String?) {
        if (text.isNullOrBlank()) return
        _state.update { it.copy(input = text.trim()) }
        submit()
    }

    fun clear() {
        loadJob?.cancel()
        resetTracking()
        _state.update {
            it.copy(input = "", content = HomeContent.Idle, backToResults = null, notice = null, canDownload = false)
        }
    }

    /** Preview a video link, open a playlist link, or search for anything else. */
    fun submit() {
        when (val p = _state.value.parsed) {
            ParsedInput.Empty -> showError(
                "Belum ada input",
                "Tempel link video/audio atau ketik judul lagu.",
                canRetry = false,
            )
            ParsedInput.Unsupported -> showError(
                "Link tidak didukung",
                AppError(DownloadErrorKind.UNSUPPORTED_URL, "").userMessage(),
                canRetry = false,
            )
            is ParsedInput.Video -> loadVideo(p.url, keepResults = false)
            is ParsedInput.Playlist -> loadPlaylist(p.url)
            is ParsedInput.Batch -> loadBatch(p.urls)
            is ParsedInput.Search -> search(p.query)
        }
    }

    fun retry() {
        lastLoad?.invoke() ?: submit()
    }

    // ---------------------------------------------------------------- loaders

    private var lastLoad: (() -> Unit)? = null

    private fun launchLoad(label: String, block: suspend () -> Unit) {
        loadJob?.cancel()
        resetTracking()
        _state.update { it.copy(content = HomeContent.Loading(label), notice = null, canDownload = false) }
        loadJob = viewModelScope.launch {
            try {
                block()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppLog.e("HOME", "load failed: $label", t)
                showError("Gagal memuat", messageWithDetail(t))
            }
        }
    }

    private fun loadVideo(url: String, keepResults: Boolean) {
        lastLoad = { loadVideo(url, keepResults = true) }
        if (!keepResults) _state.update { it.copy(backToResults = null) }
        launchLoad("Memuat preview…") {
            val meta = yt.metadata.fetchMetadata(url)
            _state.update {
                it.copy(
                    content = HomeContent.Video(meta),
                    edits = VideoEdits(title = meta.title, artist = meta.artist.orEmpty()),
                    canDownload = true,
                )
            }
        }
    }

    private fun search(query: String) {
        lastLoad = { search(query) }
        _state.update { it.copy(backToResults = null) }
        launchLoad("Mencari “$query”…") {
            val results = yt.metadata.search(query)
            if (results.isEmpty()) {
                showError("Tidak ada hasil", "Coba kata kunci lain.", canRetry = false)
            } else {
                _state.update { it.copy(content = HomeContent.SearchResults(query, results)) }
            }
        }
    }

    fun loadPlaylist(url: String) {
        lastLoad = { loadPlaylist(url) }
        _state.update { it.copy(backToResults = null) }
        launchLoad("Memuat playlist…") {
            val info = yt.metadata.fetchPlaylist(url)
            if (info.entries.isEmpty()) {
                showError("Playlist kosong", "Tidak ada video yang bisa di-download di playlist ini.", canRetry = false)
            } else {
                _state.update {
                    it.copy(
                        content = HomeContent.Playlist(info, info.entries.indices.toSet()),
                        canDownload = true,
                    )
                }
            }
        }
    }

    /** Batch: resolve each URL to a preview (title/thumbnail) so the queue has real names. */
    fun loadBatch(urls: List<String>) {
        val capped = urls.take(com.example.yt2mp3.data.util.UrlValidator.MAX_BATCH)
        lastLoad = { loadBatch(capped) }
        _state.update { it.copy(backToResults = null) }
        launchLoad("Memuat ${capped.size} link…") {
            val items = capped.map { url ->
                try {
                    BatchItem(url, yt.metadata.fetchMetadata(url), null)
                } catch (t: Throwable) {
                    AppLog.w("HOME", "batch item failed: $url", t)
                    BatchItem(url, null, messageWithDetail(t).lineSequence().firstOrNull() ?: "Gagal memuat.")
                }
            }
            val ok = items.count { it.meta != null }
            if (ok == 0) {
                showError("Semua link gagal", "Tidak ada link yang bisa di-preview. Cek koneksi atau engine.")
            } else {
                _state.update {
                    it.copy(
                        content = HomeContent.Batch(
                            items = items,
                            selected = items.indices.filter { items[it].meta != null }.toSet(),
                        ),
                        canDownload = true,
                        notice = if (ok < items.size) "${items.size - ok} link gagal, ${ok} siap diunduh." else null,
                    )
                }
            }
        }
    }

    /** Batch content pasted from a TXT/CSV/JSON import (file content, not a single input). */
    fun importBatchText(fileText: String) {
        val urls = com.example.yt2mp3.data.util.UrlValidator.extractAllSupportedUrls(fileText)
        if (urls.isEmpty()) {
            showError("Tidak ada link", "File tidak berisi link video/audio yang dikenali.", canRetry = false)
            return
        }
        _state.update { it.copy(input = urls.joinToString("\n")) }
        loadBatch(urls)
    }

    fun openResult(entry: VideoEntry) {
        val results = _state.value.content as? HomeContent.SearchResults
        _state.update { it.copy(input = entry.url, backToResults = results) }
        loadVideo(entry.url, keepResults = true)
    }

    fun backToResults() {
        val results = _state.value.backToResults ?: return
        loadJob?.cancel()
        resetTracking()
        _state.update {
            it.copy(input = results.query, content = results, backToResults = null, canDownload = false)
        }
    }

    // ------------------------------------------------------------ edits/opts

    fun onFormatChange(f: AudioFormat) {
        _state.update { it.copy(format = f, activeProfile = null, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setDefaultFormat(f) }
    }

    fun onQualityChange(q: AudioQuality) {
        _state.update { it.copy(quality = q, activeProfile = null, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setDefaultQuality(q) }
    }

    fun onMediaKindChange(k: MediaKind) {
        _state.update { it.copy(mediaKind = k, activeProfile = null, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setMediaKind(k) }
    }

    fun onVideoFormatChange(f: VideoFormat) {
        _state.update { it.copy(videoFormat = f, activeProfile = null, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setVideoFormat(f) }
    }

    fun onVideoQualityChange(q: VideoQuality) {
        _state.update { it.copy(videoQuality = q, activeProfile = null, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setVideoQuality(q) }
    }

    fun onPostChange(transform: (PostOptions) -> PostOptions) {
        _state.update { it.copy(post = transform(it.post), activeProfile = null, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setPostFlags(_state.value.post) }
    }

    fun onRangeChange(v: String) {
        _state.update {
            val size = when (val c = it.content) {
                is HomeContent.Playlist -> c.info.entries.size
                is HomeContent.Batch -> c.items.size
                else -> 0
            }
            val err = if (v.isBlank() || size == 0) null
            else if (PlaylistRange.parse(v, size) == null) "Format range salah. Contoh: 1-10,15,20-30."
            else null
            it.copy(rangeText = v.take(60), rangeError = err)
        }
    }

    fun onSkipChange(v: Boolean) {
        _state.update { it.copy(skipExisting = v) }
        viewModelScope.launch { yt.settings.setSkipExisting(v) }
    }

    fun onSubfolderChange(v: Boolean) {
        _state.update { it.copy(subfolderAuto = v) }
        viewModelScope.launch { yt.settings.setSubfolderPerPlaylist(v) }
    }

    fun toggleAdvanced() {
        _state.update { it.copy(showAdvanced = !it.showAdvanced) }
    }

    /** Opens the current input/preview as a playlist/channel (fallback for generic links). */
    fun openAsPlaylist() {
        val url = (_state.value.content as? HomeContent.Video)?.meta?.sourceUrl
            ?: _state.value.input
        if (url.isBlank()) return
        _state.update { it.copy(input = url) }
        loadPlaylist(UrlValidator.extractFirstSupportedUrl(url) ?: url.trim())
    }

    fun applyProfile(p: DownloadProfile) {
        _state.update {
            it.copy(
                mediaKind = p.mediaKind,
                format = p.audioFormat,
                quality = p.audioQuality,
                videoFormat = p.videoFormat,
                videoQuality = p.videoQuality,
                post = p.post,
                activeProfile = p.name,
                canDownload = it.hasDownloadable(),
            )
        }
        viewModelScope.launch {
            yt.settings.setMediaKind(p.mediaKind)
            yt.settings.setDefaultFormat(p.audioFormat)
            yt.settings.setDefaultQuality(p.audioQuality)
            yt.settings.setVideoFormat(p.videoFormat)
            yt.settings.setVideoQuality(p.videoQuality)
            yt.settings.setPostFlags(p.post)
        }
    }

    fun saveProfile(name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        val s = _state.value
        val p = DownloadProfile(
            name = clean,
            mediaKind = s.mediaKind,
            audioFormat = s.format,
            audioQuality = s.quality,
            videoFormat = s.videoFormat,
            videoQuality = s.videoQuality,
            post = s.post,
        )
        viewModelScope.launch {
            val list = (Profiles.decode(runCatching { yt.settings.getProfilesJson() }.getOrDefault("")) +
                p).distinctBy { it.name }.takeLast(20)
            yt.settings.setProfilesJson(Profiles.encode(list))
            _state.update { it.copy(profiles = list, activeProfile = clean) }
        }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch {
            val list = Profiles.decode(runCatching { yt.settings.getProfilesJson() }.getOrDefault(""))
                .filterNot { it.name == name }
            yt.settings.setProfilesJson(Profiles.encode(list))
            _state.update { it.copy(profiles = list, activeProfile = null) }
        }
    }

    fun onEdits(transform: (VideoEdits) -> VideoEdits) {
        _state.update { it.copy(edits = transform(it.edits), canDownload = it.hasDownloadable()) }
    }

    fun toggleEntry(index: Int) = updatePlaylist { c ->
        if (index in c.selected) c.selected - index else c.selected + index
    }

    fun selectAll(all: Boolean) = updatePlaylist { c ->
        if (all) c.info.entries.indices.toSet() else emptySet()
    }

    fun toggleBatch(index: Int) {
        _state.update {
            val c = it.content as? HomeContent.Batch ?: return@update it
            if (c.items.getOrNull(index)?.meta == null) return@update it
            val sel = if (index in c.selected) c.selected - index else c.selected + index
            it.copy(content = c.copy(selected = sel), canDownload = sel.isNotEmpty(), notice = null)
        }
    }

    fun selectBatchAll(all: Boolean) {
        _state.update {
            val c = it.content as? HomeContent.Batch ?: return@update it
            val sel = if (all) c.items.indices.filter { c.items[it].meta != null }.toSet() else emptySet()
            it.copy(content = c.copy(selected = sel), canDownload = sel.isNotEmpty(), notice = null)
        }
    }

    private fun updatePlaylist(transform: (HomeContent.Playlist) -> Set<Int>) {
        _state.update {
            val c = it.content as? HomeContent.Playlist ?: return@update it
            it.copy(content = c.copy(selected = transform(c)), canDownload = true, notice = null)
        }
    }

    // ---------------------------------------------------------------- download

    fun download() {
        val s = _state.value
        when (val c = s.content) {
            is HomeContent.Video -> downloadVideo(s, c.meta)
            is HomeContent.Playlist -> downloadPlaylist(s, c)
            is HomeContent.Batch -> downloadBatch(s, c)
            else -> Unit
        }
    }

    private fun downloadVideo(s: HomeScreenState, meta: VideoMetadata) {
        var start: Int? = null
        var end: Int? = null
        when (val trim = s.trimCheck) {
            is TimeFormat.TrimResult.Invalid -> return
            is TimeFormat.TrimResult.Ok -> { start = trim.start; end = trim.end }
            null -> Unit
        }
        val request = DownloadRequest(
            sourceUrl = meta.sourceUrl,
            title = s.edits.title.trim().ifBlank { meta.title },
            artist = s.edits.artist.trim().ifBlank { null },
            thumbnailUrl = meta.thumbnailUrl,
            quality = s.quality,
            format = s.format,
            sectionStart = start,
            sectionEnd = end,
            mediaKind = s.mediaKind,
            videoFormat = s.videoFormat,
            videoQuality = s.videoQuality,
            post = s.post,
        )
        viewModelScope.launch {
            yt.startDownload.execute(request).fold(
                onSuccess = { id ->
                    lastDownloadId = id
                    _state.update { it.copy(canDownload = false) }
                },
                onFailure = { t ->
                    AppLog.e("HOME", "start download failed", t)
                    val msg = if (t.message == "SKIP_EXISTING") "Link ini sudah ada di antrean/riwayat (lewati aktif)."
                    else messageWithDetail(t)
                    _state.update { it.copy(notice = msg) }
                },
            )
        }
    }

    private fun subfolderFor(name: String?, enabled: Boolean): String? {
        if (!enabled) return null
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty() || clean == "Playlist") return null
        return clean.take(80)
    }

    private fun downloadPlaylist(s: HomeScreenState, c: HomeContent.Playlist) {
        val sel = s.rangedIndices(c.info.entries.size, c.selected).sorted()
        val sub = subfolderFor(c.info.title, s.subfolderAuto)
        val requests = sel.map { i ->
            val e = c.info.entries[i]
            DownloadRequest(
                sourceUrl = e.url,
                title = e.title,
                artist = e.channel,
                thumbnailUrl = e.thumbnailUrl,
                quality = s.quality,
                format = s.format,
                mediaKind = s.mediaKind,
                videoFormat = s.videoFormat,
                videoQuality = s.videoQuality,
                post = s.post,
                subfolder = sub,
            )
        }
        if (requests.isEmpty()) return
        viewModelScope.launch {
            yt.startDownload.executeAll(requests).fold(
                onSuccess = { ids ->
                    _state.update {
                        it.copy(
                            canDownload = false,
                            notice = "${ids.size} item masuk antrean" +
                                (if (sub != null) " (folder $sub)." else ".") +
                                " Lihat progresnya di tab Downloads.",
                        )
                    }
                },
                onFailure = { t ->
                    AppLog.e("HOME", "queue playlist failed", t)
                    val msg = if (t.message == "SKIP_EXISTING") "Semua item sudah ada (lewati aktif)."
                    else messageWithDetail(t)
                    _state.update { it.copy(notice = msg) }
                },
            )
        }
    }

    private fun downloadBatch(s: HomeScreenState, c: HomeContent.Batch) {
        val sel = s.rangedIndices(c.items.size, c.selected).sorted()
        val requests = sel.mapNotNull { i ->
            val item = c.items.getOrNull(i) ?: return@mapNotNull null
            val meta = item.meta ?: return@mapNotNull null
            DownloadRequest(
                sourceUrl = meta.sourceUrl,
                title = meta.title,
                artist = meta.artist,
                thumbnailUrl = meta.thumbnailUrl,
                quality = s.quality,
                format = s.format,
                mediaKind = s.mediaKind,
                videoFormat = s.videoFormat,
                videoQuality = s.videoQuality,
                post = s.post,
                subfolder = subfolderFor(meta.siteName?.let { "Batch $it" }, s.subfolderAuto),
            )
        }
        if (requests.isEmpty()) return
        viewModelScope.launch {
            yt.startDownload.executeAll(requests).fold(
                onSuccess = { ids ->
                    _state.update {
                        it.copy(
                            canDownload = false,
                            notice = "${ids.size} link masuk antrean. Lihat progresnya di tab Downloads.",
                        )
                    }
                },
                onFailure = { t ->
                    AppLog.e("HOME", "queue batch failed", t)
                    val msg = if (t.message == "SKIP_EXISTING") "Semua link sudah ada (lewati aktif)."
                    else messageWithDetail(t)
                    _state.update { it.copy(notice = msg) }
                },
            )
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun HomeScreenState.hasDownloadable() =
        content is HomeContent.Video || content is HomeContent.Playlist || content is HomeContent.Batch

    private fun resetTracking() {
        _tracked.value = null
        lastDownloadId = null
    }

    private fun showError(title: String, message: String, canRetry: Boolean = true) {
        _state.update { it.copy(content = HomeContent.Error(title, message, canRetry), canDownload = false) }
    }

    private fun messageWithDetail(t: Throwable): String {
        val kind = t.toDownloadErrorKind()
        val base = AppError(kind, "").userMessage()
        // Input-validation kinds carry no extra info beyond the kind name.
        if (kind == DownloadErrorKind.INVALID_URL || kind == DownloadErrorKind.UNSUPPORTED_URL) return base
        return "$base\n\nDetail: ${t.errorDetail()}"
    }

    /** "content://…/tree/primary%3AMusic%2FYT2MP3" → "Music/YT2MP3". */
    private fun folderLabel(uri: String): String {
        val decoded = runCatching { java.net.URLDecoder.decode(uri.substringAfterLast("/tree/"), "UTF-8") }
            .getOrDefault(uri)
        return decoded.substringAfter(':').ifBlank { "Penyimpanan internal" }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(app) }
        }
    }
}
