package com.example.yt2mp3.di

import android.content.Context
import com.example.yt2mp3.data.database.AppDatabase
import com.example.yt2mp3.data.engine.EngineVersionManager
import com.example.yt2mp3.data.engine.YtDlpEngine
import com.example.yt2mp3.data.engine.YtDlpEngineImpl
import com.example.yt2mp3.data.queue.QueueRunner
import com.example.yt2mp3.data.repository.DownloadRepositoryImpl
import com.example.yt2mp3.data.repository.MetadataRepositoryImpl
import com.example.yt2mp3.data.repository.SettingsRepositoryImpl
import com.example.yt2mp3.data.storage.SafStorageManager
import com.example.yt2mp3.data.storage.StorageManager
import com.example.yt2mp3.domain.repository.DownloadRepository
import com.example.yt2mp3.domain.repository.MetadataRepository
import com.example.yt2mp3.domain.repository.SettingsRepository
import com.example.yt2mp3.domain.usecase.CancelDownloadUseCase
import com.example.yt2mp3.domain.usecase.RetryDownloadUseCase
import com.example.yt2mp3.domain.usecase.StartDownloadUseCase
import com.example.yt2mp3.domain.usecase.UpdateEngineUseCase
import com.example.yt2mp3.service.DownloadForegroundService

/** Manual DI container (no Hilt) — interfaces keep engine/storage testable (PRD §53.5). */
class AppContainer(private val context: Context) {
    private val db by lazy { AppDatabase.get(context) }

    val downloads: DownloadRepository by lazy { DownloadRepositoryImpl(db.downloadDao()) }
    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(context) }
    val engine: YtDlpEngine by lazy { YtDlpEngineImpl(context) }
    val storage: StorageManager by lazy {
        SafStorageManager(context, getDestinationUri = { settings.getDestinationUri() })
    }

    val queueRunner: QueueRunner by lazy {
        QueueRunner(downloads, settings, engine, storage)
    }

    val metadataRepo: MetadataRepository by lazy { MetadataRepositoryImpl(engine) }

    val startDownload by lazy {
        StartDownloadUseCase(downloads, settings) {
            queueRunner.kick()
            DownloadForegroundService.start(context)
        }
    }
    val cancelDownload by lazy {
        CancelDownloadUseCase(downloads) { id, processId -> queueRunner.cancel(id, processId) }
    }
    val retryDownload by lazy {
        RetryDownloadUseCase(downloads) {
            queueRunner.kick()
            DownloadForegroundService.start(context)
        }
    }
    val engineManager by lazy { EngineVersionManager(context) }
    val updateEngine by lazy { UpdateEngineUseCase(engineManager) }
}
