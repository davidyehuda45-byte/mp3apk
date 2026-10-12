package com.example.yt2mp3.ui.player

import android.app.Application
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.TimeFormat
import com.example.yt2mp3.ui.theme.AppSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : ViewModel() {
    private val yt = app as Yt2Mp3App
    private val _item = MutableStateFlow<DownloadItem?>(null)
    val item: StateFlow<DownloadItem?> = _item

    fun load(id: Long) = viewModelScope.launch {
        _item.value = yt.downloads.get(id)
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlayerViewModel(app) }
        }
    }
}

/**
 * Built-in player for downloaded files (no new dependencies):
 * audio via MediaPlayer, video via VideoView. Play/pause/seek + sleep timer.
 * Background play, PiP, Cast, Auto/Wear are not included — use "Open file".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    itemId: Long,
    onBack: () -> Unit,
    vm: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val item by vm.item.collectAsState()
    LaunchedEffect(itemId) { vm.load(itemId) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Player") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            },
        )
        val it = item
        if (it == null) {
            Text(
                "Memuat…",
                modifier = Modifier.padding(AppSpacing.screen),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (it.outputUri == null) {
            Text(
                "File belum tersedia (download belum selesai).",
                modifier = Modifier.padding(AppSpacing.screen),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
            ) {
                Text(it.title, style = MaterialTheme.typography.titleLarge)
                it.artist?.let { a ->
                    Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (it.mediaKind == MediaKind.VIDEO) {
                    VideoPlayer(uri = it.outputUri)
                } else {
                    AudioPlayer(uri = it.outputUri)
                }
            }
        }
    }
}

@Composable
private fun AudioPlayer(uri: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var sleepMin by remember { mutableStateOf<Int?>(null) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(uri) {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(context, android.net.Uri.parse(uri))
            mp.prepare()
            duration = mp.duration.toLong()
            mp.setOnCompletionListener { playing = false }
            player = mp
        } catch (t: Throwable) {
            error = "Tidak bisa diputar di pemutar bawaan. Pakai Open file. (${t.message?.take(80)})"
        }
        onDispose {
            sleepJob?.cancel()
            runCatching { mp.release() }
            player = null
        }
    }

    // Position ticker.
    LaunchedEffect(playing) {
        while (playing) {
            delay(500)
            player?.let {
                if (it.duration > 0) position = it.currentPosition.toFloat() / it.duration
            }
        }
    }

    if (error != null) {
        Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Button(onClick = {
            val mp = player
            if (mp == null) return@Button
            if (playing) {
                mp.pause()
                playing = false
            } else {
                mp.start()
                playing = true
            }
        }) { Text(if (playing) "Pause" else "Play") }
        Text(
            "${TimeFormat.format(((position * duration) / 1000).toLong())} / ${TimeFormat.format(duration / 1000)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Slider(
        value = position,
        onValueChange = { position = it },
        onValueChangeFinished = {
            player?.seekTo((position * duration).toInt())
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Sleep timer", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        listOf(null, 5, 10, 30, 60).forEach { m ->
            FilterChip(
                selected = sleepMin == m,
                onClick = {
                    sleepMin = m
                    sleepJob?.cancel()
                    sleepJob = if (m != null) {
                        scope.launch {
                            delay(m * 60_000L)
                            player?.pause()
                            playing = false
                        }
                    } else null
                },
                label = { Text(if (m == null) "Off" else "$m mnt") },
            )
        }
    }
}

@Composable
private fun VideoPlayer(uri: String) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(android.net.Uri.parse(uri))
                setOnPreparedListener { it.isLooping = false }
                view = this
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(AppSpacing.previewImage * 2),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Button(onClick = {
            val v = view
            if (v == null) return@Button
            if (v.isPlaying) {
                v.pause()
                playing = false
            } else {
                v.start()
                playing = true
            }
        }) { Text(if (playing) "Pause" else "Play") }
        OutlinedButton(onClick = {
            view?.let {
                it.pause()
                playing = false
                it.seekTo(0)
            }
        }) { Text("Ulangi") }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { view?.stopPlayback() } }
    }
}
