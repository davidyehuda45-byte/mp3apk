package com.example.yt2mp3.data.storage

import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.domain.model.VideoMetadata
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes ID3 metadata after conversion (PRD §17). Never fails the download. */
object Id3Writer {
    suspend fun writeMp3Tags(mp3: File, metadata: VideoMetadata) = withContext(Dispatchers.IO) {
        try {
            val tag: ID3v2 = ID3v24Tag()
            tag.title = metadata.title
            metadata.artist?.let { tag.artist = it }
            tag.url = metadata.sourceUrl
            metadata.thumbnailUrl?.let { thumb ->
                runCatching {
                    val conn = URL(thumb).openConnection().apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                    }
                    val bytes = conn.getInputStream().use { it.readBytes() }
                    if (bytes.size in 1..3_000_000) {
                        tag.setAlbumImage(bytes, "image/jpeg")
                    }
                }.onFailure { AppLog.w("METADATA", "cover art skipped", it) }
            }
            val tmp = File(mp3.parentFile, mp3.nameWithoutExtension + "_tagged.mp3")
            val wrapper = Mp3File(mp3.absolutePath)
            wrapper.id3v2Tag = tag
            wrapper.save(tmp.absolutePath)
            if (tmp.exists() && tmp.length() > 0) {
                mp3.delete()
                tmp.renameTo(mp3)
            } else {
                tmp.delete()
            }
        } catch (t: Throwable) {
            // PRD §17: missing metadata must not fail the download.
            AppLog.w("METADATA", "ID3 write skipped", t)
        }
    }
}
