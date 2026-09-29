package com.example.yt2mp3.data.repository

import com.example.yt2mp3.data.engine.YtDlpEngine
import com.example.yt2mp3.domain.model.PlaylistInfo
import com.example.yt2mp3.domain.model.VideoEntry
import com.example.yt2mp3.domain.model.VideoMetadata
import com.example.yt2mp3.domain.repository.MetadataRepository

class MetadataRepositoryImpl(private val engine: YtDlpEngine) : MetadataRepository {
    override suspend fun fetchMetadata(url: String): VideoMetadata = engine.fetchMetadata(url)
    override suspend fun search(query: String): List<VideoEntry> = engine.search(query)
    override suspend fun fetchPlaylist(url: String): PlaylistInfo = engine.fetchPlaylist(url)
}
