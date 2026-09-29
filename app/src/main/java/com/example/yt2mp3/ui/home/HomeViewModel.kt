package com.example.yt2mp3.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.data.util.InputParser
import com.example.yt2mp3.data.util.ParsedInput
import com.example.yt2mp3.domain.model.AppError
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadErrorKind
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadRequest
import com.example.yt2mp3.domain.model.PlaylistInfo
import com.example.yt2mp3.domain.model.SizeEstimator
import com.example.yt2mp3.domain.model.TimeFormat
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoMetadata
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
    data class Error(val title: String, val message: String, val canRetry: Boolean = true) : HomeContent
}

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
    val format: AudioFormat = AudioFormat.DEFAULT,
    val quality: AudioQuality = AudioQuality.DEFAULT,
    val destinationLabel: String? = null,
    val edits: VideoEdits = VideoEdits(),
    /** Results to return to after opening one of them. */
    val backToResults: HomeContent.SearchResults? = null,
    /** Short confirmation, e.g. after queueing a playlist. */
    val notice: String? = null,
    /** False after the current preview was queued, until something changes. */
    val canDownload: Boolean = false,
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
                val trim = trimCheck as? TimeFormat.TrimResult.Ok
                SizeEstimator.forVideo(c.meta, format, quality, trim?.start, trim?.end)
            }
            is HomeContent.Playlist ->
                c.selected.takeIf { it.isNotEmpty() }?.let { sel ->
                    SizeEstimator.forDurations(sel.map { c.info.entries[it].durationSecs }, format, quality)
                }
            else -> null
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
                "Tempel link YouTube atau ketik judul lagu.",
                canRetry = false,
            )
            ParsedInput.Unsupported -> showError(
                "Link tidak didukung",
                AppError(DownloadErrorKind.UNSUPPORTED_URL, "").userMessage(),
                canRetry = false,
            )
            is ParsedInput.Video -> loadVideo(p.url, keepResults = false)
            is ParsedInput.Playlist -> loadPlaylist(p.url)
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
        _state.update { it.copy(format = f, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setDefaultFormat(f) }
    }

    fun onQualityChange(q: AudioQuality) {
        _state.update { it.copy(quality = q, canDownload = it.hasDownloadable()) }
        viewModelScope.launch { yt.settings.setDefaultQuality(q) }
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
        )
        viewModelScope.launch {
            yt.startDownload.execute(request).fold(
                onSuccess = { id ->
                    lastDownloadId = id
                    _state.update { it.copy(canDownload = false) }
                },
                onFailure = { t ->
                    AppLog.e("HOME", "start download failed", t)
                    _state.update { it.copy(notice = messageWithDetail(t)) }
                },
            )
        }
    }

    private fun downloadPlaylist(s: HomeScreenState, c: HomeContent.Playlist) {
        val requests = c.selected.sorted().map { i ->
            val e = c.info.entries[i]
            DownloadRequest(
                sourceUrl = e.url,
                title = e.title,
                artist = e.channel,
                thumbnailUrl = e.thumbnailUrl,
                quality = s.quality,
                format = s.format,
            )
        }
        if (requests.isEmpty()) return
        viewModelScope.launch {
            yt.startDownload.executeAll(requests).fold(
                onSuccess = { ids ->
                    _state.update {
                        it.copy(
                            canDownload = false,
                            notice = "${ids.size} lagu masuk antrean. Lihat progresnya di tab Downloads.",
                        )
                    }
                },
                onFailure = { t ->
                    AppLog.e("HOME", "queue playlist failed", t)
                    _state.update { it.copy(notice = messageWithDetail(t)) }
                },
            )
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun HomeScreenState.hasDownloadable() =
        content is HomeContent.Video || content is HomeContent.Playlist

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
