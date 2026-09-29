package com.example.yt2mp3.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.yt2mp3.domain.model.AudioFormat
import com.example.yt2mp3.domain.model.AudioQuality
import com.example.yt2mp3.domain.model.DownloadItem
import com.example.yt2mp3.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/** PRD §28 entity. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val outputFileName: String,
    /** SAF document URI of the finished file, for "Open file" (§PRD-19). */
    val outputUri: String? = null,
    val qualityKbps: Int,
    val format: String = AudioFormat.MP3.name,
    val sectionStart: Int? = null,
    val sectionEnd: Int? = null,
    val status: DownloadStatus,
    val progress: Int?,
    val stage: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

fun DownloadEntity.toDomain() = DownloadItem(
    id = id,
    sourceUrl = sourceUrl,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    outputFileName = outputFileName,
    outputUri = outputUri,
    quality = AudioQuality.fromBitrate(qualityKbps),
    format = AudioFormat.fromName(format),
    sectionStart = sectionStart,
    sectionEnd = sectionEnd,
    status = status,
    progress = progress,
    stage = stage,
    errorMessage = errorMessage,
    createdAt = createdAt,
    completedAt = completedAt,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','DOWNLOADING','CONVERTING','SAVING') ORDER BY createdAt ASC, id ASC")
    fun observeActiveQueue(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Insert
    suspend fun insertAll(entities: List<DownloadEntity>): List<Long>

    @Query("UPDATE downloads SET status = :status, errorMessage = :error, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String?, completedAt: Long?)

    @Query("UPDATE downloads SET progress = :percent, stage = :stage WHERE id = :id")
    suspend fun updateProgress(id: Long, percent: Int?, stage: String?)

    @Query("UPDATE downloads SET outputUri = :uri WHERE id = :id")
    suspend fun updateOutputUri(id: Long, uri: String)

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun nextQueued(): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('DOWNLOADING','CONVERTING','SAVING')")
    suspend fun activeCount(): Int

    @Query("UPDATE downloads SET status = 'QUEUED', progress = NULL, stage = 'Waiting' WHERE status IN ('DOWNLOADING','CONVERTING','SAVING')")
    suspend fun requeueInterrupted()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}
