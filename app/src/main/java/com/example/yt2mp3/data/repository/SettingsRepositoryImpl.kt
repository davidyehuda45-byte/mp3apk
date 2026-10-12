package com.example.yt2mp3.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.yt2mp3.data.util.FileNamePattern
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.MediaKind
import com.example.yt2mp3.domain.model.PostOptions
import com.example.yt2mp3.domain.model.SubtitleMode
import com.example.yt2mp3.domain.model.ThemeMode
import com.example.yt2mp3.domain.model.VideoFormat
import com.example.yt2mp3.domain.model.VideoQuality
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

    private val mediaKindKey = stringPreferencesKey("media_kind")
    private val videoFormatKey = stringPreferencesKey("video_format")
    private val videoQualityKey = stringPreferencesKey("video_quality")
    private val subtitleModeKey = stringPreferencesKey("subtitle_mode")
    private val subLangsKey = stringPreferencesKey("sub_langs")
    private val chaptersKey = booleanPreferencesKey("embed_chapters")
    private val sponsorKey = booleanPreferencesKey("sponsor_block")
    private val normalizeKey = booleanPreferencesKey("normalize_audio")
    private val saveThumbKey = booleanPreferencesKey("save_thumb")
    private val stripKey = booleanPreferencesKey("strip_metadata")
    private val patternKey = stringPreferencesKey("rename_pattern")
    private val subfolderKey = booleanPreferencesKey("subfolder_playlist")
    private val skipExistingKey = booleanPreferencesKey("skip_existing")
    private val parallelKey = intPreferencesKey("max_parallel")
    private val retriesKey = intPreferencesKey("max_retries")
    private val fragRetriesKey = intPreferencesKey("fragment_retries")
    private val rateKey = intPreferencesKey("rate_limit_kbps")
    private val wifiKey = booleanPreferencesKey("wifi_only")
    private val proxyKey = stringPreferencesKey("proxy_url")
    private val uaKey = stringPreferencesKey("user_agent")
    private val ytdlpArgsKey = stringPreferencesKey("custom_ytdlp_args")
    private val ffmpegArgsKey = stringPreferencesKey("custom_ffmpeg_args")
    private val customFormatKey = stringPreferencesKey("custom_format")
    private val debugKey = booleanPreferencesKey("debug_mode")
    private val historyKey = booleanPreferencesKey("save_history")
    private val autoPasteKey = booleanPreferencesKey("auto_paste")
    private val disclaimerKey = booleanPreferencesKey("seen_disclaimer")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val lockKey = booleanPreferencesKey("lock_enabled")
    private val pinKey = stringPreferencesKey("pin_hash")
    private val schedOnKey = booleanPreferencesKey("sched_enabled")
    private val schedHourKey = intPreferencesKey("sched_hour")
    private val schedMinKey = intPreferencesKey("sched_minute")
    private val rssKey = stringPreferencesKey("rss_channel")
    private val rssAutoKey = booleanPreferencesKey("rss_auto")
    private val profilesKey = stringPreferencesKey("profiles_json")
    private val simpleKey = booleanPreferencesKey("simple_mode")

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

    override fun observeMediaKind(): Flow<MediaKind> =
        context.prefs.data.map { runCatching { MediaKind.valueOf(it[mediaKindKey] ?: "") }.getOrDefault(MediaKind.AUDIO) }

    override suspend fun setMediaKind(kind: MediaKind) {
        context.prefs.edit { it[mediaKindKey] = kind.name }
    }

    override fun observeVideoFormat(): Flow<VideoFormat> =
        context.prefs.data.map { VideoFormat.fromName(it[videoFormatKey]) }

    override suspend fun setVideoFormat(format: VideoFormat) {
        context.prefs.edit { it[videoFormatKey] = format.name }
    }

    override fun observeVideoQuality(): Flow<VideoQuality> =
        context.prefs.data.map { VideoQuality.fromName(it[videoQualityKey]) }

    override suspend fun setVideoQuality(quality: VideoQuality) {
        context.prefs.edit { it[videoQualityKey] = quality.name }
    }

    override fun observeSubtitleMode(): Flow<SubtitleMode> =
        context.prefs.data.map { SubtitleMode.fromName(it[subtitleModeKey]) }

    override suspend fun setSubtitleMode(mode: SubtitleMode) {
        context.prefs.edit { it[subtitleModeKey] = mode.name }
    }

    override suspend fun getSubLangs(): String =
        context.prefs.data.map { it[subLangsKey] ?: "en,id" }.first()

    override suspend fun setSubLangs(langs: String) {
        context.prefs.edit { it[subLangsKey] = langs.take(60) }
    }

    override suspend fun getPostFlags(): PostOptions {
        val p = context.prefs.data.first()
        return PostOptions(
            subtitleMode = SubtitleMode.fromName(p[subtitleModeKey]),
            subLangs = p[subLangsKey] ?: "en,id",
            embedChapters = p[chaptersKey] == true,
            sponsorBlock = p[sponsorKey] == true,
            normalizeAudio = p[normalizeKey] == true,
            saveThumbSeparate = p[saveThumbKey] == true,
            stripMetadata = p[stripKey] == true,
        )
    }

    override suspend fun setPostFlags(post: PostOptions) {
        context.prefs.edit {
            it[subtitleModeKey] = post.subtitleMode.name
            it[subLangsKey] = post.subLangs.take(60)
            it[chaptersKey] = post.embedChapters
            it[sponsorKey] = post.sponsorBlock
            it[normalizeKey] = post.normalizeAudio
            it[saveThumbKey] = post.saveThumbSeparate
            it[stripKey] = post.stripMetadata
        }
    }

    override suspend fun getRenamePattern(): String =
        context.prefs.data.map { it[patternKey] ?: FileNamePattern.DEFAULT }.first()

    override suspend fun setRenamePattern(pattern: String) {
        context.prefs.edit { it[patternKey] = pattern.take(120) }
    }

    override suspend fun getSubfolderPerPlaylist(): Boolean =
        context.prefs.data.map { it[subfolderKey] != false }.first()

    override suspend fun setSubfolderPerPlaylist(enabled: Boolean) {
        context.prefs.edit { it[subfolderKey] = enabled }
    }

    override suspend fun getSkipExisting(): Boolean =
        context.prefs.data.map { it[skipExistingKey] == true }.first()

    override suspend fun setSkipExisting(enabled: Boolean) {
        context.prefs.edit { it[skipExistingKey] = enabled }
    }

    override suspend fun getMaxParallel(): Int =
        context.prefs.data.map { (it[parallelKey] ?: 1).coerceIn(1, 3) }.first()

    override suspend fun setMaxParallel(n: Int) {
        context.prefs.edit { it[parallelKey] = n.coerceIn(1, 3) }
    }

    override suspend fun getMaxRetries(): Int =
        context.prefs.data.map { (it[retriesKey] ?: 2).coerceIn(0, 5) }.first()

    override suspend fun setMaxRetries(n: Int) {
        context.prefs.edit { it[retriesKey] = n.coerceIn(0, 5) }
    }

    override suspend fun getFragmentRetries(): Int =
        context.prefs.data.map { (it[fragRetriesKey] ?: 3).coerceIn(0, 10) }.first()

    override suspend fun setFragmentRetries(n: Int) {
        context.prefs.edit { it[fragRetriesKey] = n.coerceIn(0, 10) }
    }

    override suspend fun getRateLimitKbps(): Int =
        context.prefs.data.map { (it[rateKey] ?: 0).coerceAtLeast(0) }.first()

    override suspend fun setRateLimitKbps(kbps: Int) {
        context.prefs.edit { it[rateKey] = kbps.coerceAtLeast(0) }
    }

    override suspend fun getWifiOnly(): Boolean =
        context.prefs.data.map { it[wifiKey] == true }.first()

    override suspend fun setWifiOnly(enabled: Boolean) {
        context.prefs.edit { it[wifiKey] = enabled }
    }

    override suspend fun getProxyUrl(): String =
        context.prefs.data.map { it[proxyKey].orEmpty() }.first()

    override suspend fun setProxyUrl(url: String) {
        context.prefs.edit { it[proxyKey] = url.trim().take(200) }
    }

    override suspend fun getUserAgent(): String =
        context.prefs.data.map { it[uaKey].orEmpty() }.first()

    override suspend fun setUserAgent(ua: String) {
        context.prefs.edit { it[uaKey] = ua.take(300) }
    }

    override suspend fun getCustomYtdlpArgs(): String =
        context.prefs.data.map { it[ytdlpArgsKey].orEmpty() }.first()

    override suspend fun setCustomYtdlpArgs(args: String) {
        context.prefs.edit { it[ytdlpArgsKey] = args.take(1000) }
    }

    override suspend fun getCustomFfmpegArgs(): String =
        context.prefs.data.map { it[ffmpegArgsKey].orEmpty() }.first()

    override suspend fun setCustomFfmpegArgs(args: String) {
        context.prefs.edit { it[ffmpegArgsKey] = args.take(500) }
    }

    override suspend fun getCustomFormat(): String =
        context.prefs.data.map { it[customFormatKey].orEmpty() }.first()

    override suspend fun setCustomFormat(format: String) {
        context.prefs.edit { it[customFormatKey] = format.trim().take(200) }
    }

    override suspend fun getDebugMode(): Boolean =
        context.prefs.data.map { it[debugKey] == true }.first()

    override suspend fun setDebugMode(enabled: Boolean) {
        context.prefs.edit { it[debugKey] = enabled }
    }

    override suspend fun getSaveHistory(): Boolean =
        context.prefs.data.map { it[historyKey] != false }.first()

    override suspend fun setSaveHistory(enabled: Boolean) {
        context.prefs.edit { it[historyKey] = enabled }
    }

    override suspend fun getAutoPaste(): Boolean =
        context.prefs.data.map { it[autoPasteKey] == true }.first()

    override suspend fun setAutoPaste(enabled: Boolean) {
        context.prefs.edit { it[autoPasteKey] = enabled }
    }

    override suspend fun getSeenDisclaimer(): Boolean =
        context.prefs.data.map { it[disclaimerKey] == true }.first()

    override suspend fun setSeenDisclaimer() {
        context.prefs.edit { it[disclaimerKey] = true }
    }

    override fun observeThemeMode(): Flow<ThemeMode> =
        context.prefs.data.map { ThemeMode.fromName(it[themeKey]) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.prefs.edit { it[themeKey] = mode.name }
    }

    override suspend fun getDynamicColor(): Boolean =
        context.prefs.data.map { it[dynamicKey] != false }.first()

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.prefs.edit { it[dynamicKey] = enabled }
    }

    override suspend fun getLockEnabled(): Boolean =
        context.prefs.data.map { it[lockKey] == true }.first()

    override suspend fun setLockEnabled(enabled: Boolean) {
        context.prefs.edit { it[lockKey] = enabled }
    }

    override suspend fun getPinHash(): String =
        context.prefs.data.map { it[pinKey].orEmpty() }.first()

    override suspend fun setPinHash(hash: String) {
        context.prefs.edit { it[pinKey] = hash.take(128) }
    }

    override suspend fun getSchedule(): Pair<Boolean, Pair<Int, Int>> {
        val p = context.prefs.data.first()
        return Pair(p[schedOnKey] == true, Pair(p[schedHourKey] ?: 2, p[schedMinKey] ?: 0))
    }

    override suspend fun setSchedule(enabled: Boolean, hour: Int, minute: Int) {
        context.prefs.edit {
            it[schedOnKey] = enabled
            it[schedHourKey] = hour.coerceIn(0, 23)
            it[schedMinKey] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun getRssChannel(): String =
        context.prefs.data.map { it[rssKey].orEmpty() }.first()

    override suspend fun setRssChannel(channel: String) {
        context.prefs.edit { it[rssKey] = channel.trim().take(200) }
    }

    override suspend fun getRssAuto(): Boolean =
        context.prefs.data.map { it[rssAutoKey] == true }.first()

    override suspend fun setRssAuto(enabled: Boolean) {
        context.prefs.edit { it[rssAutoKey] = enabled }
    }

    override suspend fun getProfilesJson(): String =
        context.prefs.data.map { it[profilesKey].orEmpty() }.first()

    override suspend fun setProfilesJson(json: String) {
        context.prefs.edit { it[profilesKey] = json.take(20000) }
    }

    override suspend fun getSimpleMode(): Boolean =
        context.prefs.data.map { it[simpleKey] != false }.first()

    override suspend fun setSimpleMode(enabled: Boolean) {
        context.prefs.edit { it[simpleKey] = enabled }
    }

    override suspend fun clearAll() {
        context.prefs.edit { it.clear() }
    }
}
