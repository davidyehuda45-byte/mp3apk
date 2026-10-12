package com.example.yt2mp3.ui.lock

import android.app.Application
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yt2mp3.Yt2Mp3App
import com.example.yt2mp3.ui.theme.AppSpacing
import java.security.MessageDigest
import kotlinx.coroutines.launch

fun sha256Hex(s: String): String {
    val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    return d.joinToString("") { "%02x".format(it) }
}

class LockViewModel(app: Application) : ViewModel() {
    private val yt = app as Yt2Mp3App
    var hasPin by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            hasPin = runCatching { yt.settings.getPinHash() }.getOrDefault("").isNotEmpty()
        }
    }

    fun setPin(pin: String, onDone: () -> Unit) {
        if (pin.length < 4) {
            error = "PIN minimal 4 digit."
            return
        }
        viewModelScope.launch {
            yt.settings.setPinHash(sha256Hex(pin))
            yt.settings.setLockEnabled(true)
            hasPin = true
            error = null
            onDone()
        }
    }

    fun unlock(pin: String, onOk: () -> Unit) {
        viewModelScope.launch {
            val hash = runCatching { yt.settings.getPinHash() }.getOrDefault("")
            if (hash.isNotEmpty() && sha256Hex(pin) == hash) {
                error = null
                onOk()
            } else {
                error = "PIN salah."
            }
        }
    }

    fun mismatch() {
        error = "PIN tidak sama. Ulangi."
    }

    fun canBiometric(context: android.content.Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val mgr = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            mgr?.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        } catch (t: Throwable) {
            false
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { LockViewModel(app) }
        }
    }
}

/** PIN gate shown when app lock is enabled. Biometric is optional via framework prompt. */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    vm: LockViewModel = viewModel(
        factory = LockViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val err = vm.error

    fun biometric() {
        if (Build.VERSION.SDK_INT < 29 || activity == null) return
        val prompt = BiometricPrompt.Builder(context)
            .setTitle("Buka YT2MP3")
            .setNegativeButton("PIN", activity.mainExecutor, { _, _ -> })
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onUnlocked()
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
    ) {
        Text("YT2MP3 terkunci", style = MaterialTheme.typography.titleLarge)
        Text(
            if (!vm.hasPin) "Buat PIN 4+ digit untuk mengunci aplikasi."
            else "Masukkan PIN untuk membuka.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(12) },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!vm.hasPin) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.filter(Char::isDigit).take(12) },
                label = { Text("Ulangi PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        err?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            if (!vm.hasPin) {
                if (pin != confirm) {
                    vm.mismatch()
                    return@Button
                }
                vm.setPin(pin) { onUnlocked() }
            } else {
                vm.unlock(pin, onUnlocked)
            }
        }) {
            Text(if (!vm.hasPin) "Buat & buka" else "Buka")
        }
        if (vm.hasPin && vm.canBiometric(context)) {
            OutlinedButton(onClick = { scope.launch { biometric() } }) { Text("Biometrik") }
        }
    }
}
