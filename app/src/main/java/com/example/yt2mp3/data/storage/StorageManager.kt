package com.example.yt2mp3.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.yt2mp3.data.util.AppLog
import com.example.yt2mp3.data.util.FileNameSanitizer
import java.io.File

/**
 * SAF storage abstraction (PRD §11, §29). Engine/queue never touch SAF directly.
 * Temp files live in app cache; only finished MP3s are copied to the user tree.
 */
interface StorageManager {
    suspend fun isDestinationValid(): Boolean
    suspend fun createUniqueFileName(baseName: String): String
    suspend fun saveToDestination(source: File, fileName: String, mimeType: String): Uri?
    fun newTempDir(prefix: String): File
    fun cleanup(dir: File?)
}

class SafStorageManager(
    private val context: Context,
    private val getDestinationUri: suspend () -> String?,
) : StorageManager {

    override suspend fun isDestinationValid(): Boolean {
        val raw = getDestinationUri() ?: return false
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(raw))
            tree != null && tree.exists() && tree.canWrite()
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "destination invalid", t)
            false
        }
    }

    override suspend fun createUniqueFileName(baseName: String): String {
        val raw = getDestinationUri() ?: return baseName
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(raw)) ?: return baseName
            val existing = tree.listFiles().mapNotNull { it.name }.toSet()
            FileNameSanitizer.resolveCollision(baseName) { it in existing }
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "collision check failed", t)
            baseName
        }
    }

    override suspend fun saveToDestination(source: File, fileName: String, mimeType: String): Uri? {
        val raw = getDestinationUri() ?: return null
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(raw)) ?: return null
            // Never overwrite user files (PRD §18): delete only our own collision-free target if stale.
            tree.findFile(fileName)?.takeIf { it.exists() }?.delete()
            val doc = tree.createFile(mimeType, fileName) ?: return null
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: return null
            doc.uri
        } catch (t: Throwable) {
            AppLog.e("STORAGE", "save failed", t)
            null
        }
    }

    override fun newTempDir(prefix: String): File =
        File(context.cacheDir, "yt2mp3_${prefix}_${System.currentTimeMillis()}").also { it.mkdirs() }

    override fun cleanup(dir: File?) {
        runCatching { dir?.deleteRecursively() }
    }
}
