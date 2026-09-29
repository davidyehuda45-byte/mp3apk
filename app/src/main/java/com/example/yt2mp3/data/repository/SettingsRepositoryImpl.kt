package com.example.yt2mp3.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefs by preferencesDataStore("yt2mp3_settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
    private val destKey = stringPreferencesKey("destination_uri")
    private val qualityKey = intPreferencesKey("default_quality_kbps")
    private val formatKey = stringPreferencesKey("default_format")
    private val engineKey = stringPreferencesKey("engine_version")
    private val engineCheckKey = longPreferencesKey("engine_last_check")

    override fun observeDestinationUri(): Flow<String?> =
        context.prefs.data.map { it[destKey] }

    override suspend fun getDestinationUri(): String? =
        context.prefs.data.map { it[destKey] }.first()

    override suspend fun setDestinationUri(uri: String) {
        context.prefs.edit { it[destKey] = uri }
    }

    override fun observeDefaultQuality(): Flow<AudioQuality> =
        context.prefs.data.map { AudioQuality.fromBitrate(it[qualityKey] ?: AudioQuality.DEFAULT.bitrateKbps) }

    override suspend fun getDefaultQuality(): AudioQuality =
        AudioQuality.fromBitrate(context.prefs.data.map { it[qualityKey] }.first() ?: AudioQuality.DEFAULT.bitrateKbps)

    override suspend fun setDefaultQuality(quality: AudioQuality) {
        context.prefs.edit { it[qualityKey] = quality.bitrateKbps }
    }

    override fun observeDefaultFormat(): Flow<AudioFormat> =
        context.prefs.data.map { AudioFormat.fromName(it[formatKey]) }

    override suspend fun setDefaultFormat(format: AudioFormat) {
        context.prefs.edit { it[formatKey] = format.name }
    }

    override suspend fun getEngineVersion(): String? =
        context.prefs.data.map { it[engineKey] }.first()

    override suspend fun setEngineVersion(version: String) {
        context.prefs.edit { it[engineKey] = version }
    }

    override fun observeEngineVersion(): Flow<String?> =
        context.prefs.data.map { it[engineKey] }

    override suspend fun getLastEngineCheck(): Long =
        context.prefs.data.map { it[engineCheckKey] ?: 0L }.first()

    override suspend fun setLastEngineCheck(epochMillis: Long) {
        context.prefs.edit { it[engineCheckKey] = epochMillis }
    }
}
