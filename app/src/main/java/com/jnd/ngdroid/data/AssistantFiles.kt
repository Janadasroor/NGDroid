package com.jnd.ngdroid.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jnd.ngdroid.agent.AssistantFileStore
import com.jnd.ngdroid.agent.SavedFile
import com.jnd.ngdroid.agent.guessMimeFromName
import com.jnd.ngdroid.agent.sanitizeFileName
import java.io.File

/**
 * Device storage behind download_file/read_file.
 * Agent truth lives in app files (`assistant_files/`); a public copy goes to
 * Downloads/NGDroid via MediaStore (29+) or the legacy public dir (26-28).
 */
class AndroidAssistantFileStore(private val appContext: Context) : AssistantFileStore {

    private fun dir(): File = File(appContext.filesDir, "assistant_files").apply { mkdirs() }

    override fun save(fileName: String, bytes: ByteArray, mimeType: String): SavedFile {
        val safe = sanitizeFileName(fileName, mimeType)
        val mime = mimeType.ifBlank { guessMimeFromName(safe) }
        val internal = File(dir(), safe)
        internal.writeBytes(bytes)
        publishToDownloads(safe, bytes, mime.ifBlank { "application/octet-stream" })
        return SavedFile(safe, bytes.size.toLong(), mime, "Downloads/NGDroid/$safe", internal.absolutePath)
    }

    override fun list(): List<SavedFile> {
        val files = dir().listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        return files.filter { it.isFile }.map { f ->
            SavedFile(f.name, f.length(), guessMimeFromName(f.name), "Downloads/NGDroid/${f.name}", f.absolutePath)
        }
    }

    override fun readBytes(fileName: String): ByteArray? {
        findFile(fileName)?.let { return runCatching { File(it.internalPath).readBytes() }.getOrNull() }
        return null
    }

    override fun findFile(query: String): SavedFile? {
        val q = query.trim().substringAfterLast('/').trim()
        if (q.isEmpty()) return null
        val files = dir().listFiles()?.filter { it.isFile }.orEmpty()
        files.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { f ->
            return SavedFile(f.name, f.length(), guessMimeFromName(f.name), "Downloads/NGDroid/${f.name}", f.absolutePath)
        }
        files.firstOrNull { it.name.lowercase().endsWith(q.lowercase()) }?.let { f ->
            return SavedFile(f.name, f.length(), guessMimeFromName(f.name), "Downloads/NGDroid/${f.name}", f.absolutePath)
        }
        return null
    }

    private fun publishToDownloads(name: String, bytes: ByteArray, mime: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/NGDroid")
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = appContext.contentResolver.insert(collection, values) ?: return
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            } else {
                // API 26-28: legacy public dir needs WRITE_EXTERNAL_STORAGE.
                // Internal app-files copy above already succeeded, so skip the
                // public copy when the grant is missing instead of throwing.
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!granted) return
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outDir = File(downloads, "NGDroid").apply { mkdirs() }
                File(outDir, name).writeBytes(bytes)
            }
        }
    }
}
