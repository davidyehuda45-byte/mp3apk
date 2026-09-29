package com.example.yt2mp3.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.yt2mp3.BuildConfig
import com.example.yt2mp3.data.update.AppUpdate
import com.example.yt2mp3.data.update.AppUpdater
import kotlinx.coroutines.launch

/** Checks GitHub once per app start and offers the new version. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (!dismissed) update = AppUpdater.check() }

    val u = update ?: return
    if (dismissed) return

    AlertDialog(
        onDismissRequest = { if (!downloading) dismissed = true },
        title = { Text("Update tersedia") },
        text = {
            Column {
                Text("Versi ${u.version} tersedia (sekarang ${BuildConfig.VERSION_NAME}).")
                u.notes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(16.dp))
                    val p = progress
                    if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    downloading = true
                    error = null
                    scope.launch {
                        runCatching { AppUpdater.download(context, u) { progress = it } }
                            .onSuccess { AppUpdater.install(context, it) }
                            .onFailure { error = "Gagal mengunduh update. Cek koneksi lalu coba lagi." }
                        downloading = false
                        progress = null
                    }
                },
            ) { Text(if (error != null) "Coba lagi" else "Update") }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = { dismissed = true }) { Text("Nanti") }
        },
    )
}
