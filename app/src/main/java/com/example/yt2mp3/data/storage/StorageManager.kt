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
    /** Saves into a subfolder (created on demand, sanitized). Null/blank = root. */
    suspend fun saveToSubfolder(source: File, subfolder: String?, fileName: String, mimeType: String): Uri?
    suspend fun createUniqueFileNameIn(subfolder: String?, baseName: String): String
    fun newTempDir(prefix: String): File
    fun cleanup(dir: File?)
    suspend fun saveBytesToDestination(subfolder: String?, fileName: String, mimeType: String, bytes: ByteArray): Uri?
    suspend fun renameOutput(outputUri: String, newFileName: String): Uri?
    suspend fun deleteOutput(outputUri: String): Boolean
    /** Total bytes of files in the destination tree (0 when unavailable). */
    suspend fun storageUsageBytes(): Long
    /** Creates/deletes .nomedia to hide audio from gallery apps. */
    suspend fun setHideFromGallery(hide: Boolean): Boolean
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

    override suspend fun createUniqueFileName(baseName: String): String =
        createUniqueFileNameIn(null, baseName)

    override suspend fun createUniqueFileNameIn(subfolder: String?, baseName: String): String {
        val dir = resolveDir(subfolder) ?: return baseName
        return try {
            val existing = dir.listFiles().mapNotNull { it.name }.toSet()
            FileNameSanitizer.resolveCollision(baseName) { it in existing }
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "collision check failed", t)
            baseName
        }
    }

    override suspend fun saveToDestination(source: File, fileName: String, mimeType: String): Uri? =
        saveToSubfolder(source, null, fileName, mimeType)

    override suspend fun saveToSubfolder(source: File, subfolder: String?, fileName: String, mimeType: String): Uri? {
        val dir = resolveDir(subfolder) ?: return null
        return try {
            // Never overwrite user files (PRD §18): delete only our own collision-free target if stale.
            dir.findFile(fileName)?.takeIf { it.exists() }?.delete()
            val doc = dir.createFile(mimeType, fileName) ?: return null
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: return null
            doc.uri
        } catch (t: Throwable) {
            AppLog.e("STORAGE", "save failed", t)
            null
        }
    }

    override suspend fun saveBytesToDestination(
        subfolder: String?,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Uri? {
        val dir = resolveDir(subfolder) ?: return null
        return try {
            val unique = createUniqueFileNameIn(subfolder, fileName)
            val doc = dir.createFile(mimeType, unique) ?: return null
            context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) } ?: return null
            doc.uri
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "save bytes failed", t)
            null
        }
    }

    override suspend fun renameOutput(outputUri: String, newFileName: String): Uri? {
        return try {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(outputUri)) ?: return null
            if (!doc.exists() || !doc.canWrite()) return null
            if (doc.renameTo(newFileName)) doc.uri else null
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "rename failed", t)
            null
        }
    }

    override suspend fun deleteOutput(outputUri: String): Boolean {
        return try {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(outputUri)) ?: return false
            doc.exists() && doc.delete()
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "delete file failed", t)
            false
        }
    }

    override suspend fun storageUsageBytes(): Long {
        val raw = getDestinationUri() ?: return 0L
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(raw)) ?: return 0L
            fun sum(dir: DocumentFile): Long {
                var total = 0L
                for (f in dir.listFiles()) {
                    total += if (f.isDirectory) sum(f) else f.length().coerceAtLeast(0)
                }
                return total
            }
            sum(tree)
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "usage check failed", t)
            0L
        }
    }

    override suspend fun setHideFromGallery(hide: Boolean): Boolean {
        val raw = getDestinationUri() ?: return false
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(raw)) ?: return false
            if (hide) {
                if (tree.findFile(".nomedia") == null) tree.createFile("", ".nomedia")
                true
            } else {
                tree.findFile(".nomedia")?.delete()
                true
            }
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "nomedia failed", t)
            false
        }
    }

    private fun sanitizeFolder(name: String): String {
        var base = name.trim().replace(Regex("""[/\\:*?"<>|]"""), "_").replace(Regex("""\s+"""), " ").trim(' ', '.', '_')
        if (base.isEmpty()) base = "YT2MP3"
        return base.take(80)
    }

    private suspend fun resolveDir(subfolder: String?): DocumentFile? {
        val raw = getDestinationUri() ?: return null
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(raw)) ?: return null
            val name = subfolder?.takeIf { it.isNotBlank() }?.let(::sanitizeFolder) ?: return tree
            tree.findFile(name)?.takeIf { it.isDirectory }
                ?: tree.createDirectory(name)
                ?: return tree
        } catch (t: Throwable) {
            AppLog.w("STORAGE", "resolve dir failed", t)
            null
        }
    }

    override fun newTempDir(prefix: String): File =
        File(context.cacheDir, "yt2mp3_${prefix}_${System.currentTimeMillis()}").also { it.mkdirs() }

    override fun cleanup(dir: File?) {
        runCatching { dir?.deleteRecursively() }
    }
}
