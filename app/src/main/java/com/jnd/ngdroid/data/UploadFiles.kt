package com.jnd.ngdroid.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jnd.ngdroid.agent.describeImage
import com.jnd.ngdroid.agent.extractPdfTextSnippet
import com.jnd.ngdroid.agent.formatFileSize
import com.jnd.ngdroid.agent.guessMimeFromName
import com.jnd.ngdroid.agent.sanitizeFileName
import com.jnd.ngdroid.ui.assistant.MAX_ATTACHMENT_CHARS
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val MAX_UPLOAD_BYTES = 15 * 1024 * 1024
private const val MAX_UPLOADS_KEPT = 20

private val textExtensions = setOf(
    "txt", "csv", "md", "json", "xml", "html", "htm",
    "net", "cir", "spice", "lib", "mod", "log", "text", "asc", "tsv"
)

/**
 * App-private uploads behind prompt attachments. Picker URIs are copied here
 * at attach time (15 MB cap) so the agent can read them later without
 * holding SAF permissions.
 */
class AndroidUploadStore(private val appContext: Context) {

    private fun dir(): File = File(appContext.filesDir, "uploads").apply { mkdirs() }

    /** Copy a Storage Access Framework URI into uploads. Throws with a user-facing message. */
    fun saveFromUri(uri: Uri): StoredAttachment {
        val cr = appContext.contentResolver
        var name: String? = null
        var mime: String? = null
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }?.let { runCatching { c.getString(it) }.getOrNull() }
            }
        }
        mime = runCatching { cr.getType(uri) }.getOrNull()
        val fallback = name ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        val bytes = cr.openInputStream(uri)?.use { it.readCapped(MAX_UPLOAD_BYTES + 1L) }
            ?: throw IllegalStateException("Couldn't read that file")
        if (bytes.size > MAX_UPLOAD_BYTES) {
            throw IllegalStateException(
                "${fallback.ifBlank { "That file" }} is ${formatFileSize(bytes.size.toLong())} (max 15 MB)"
            )
        }
        if (bytes.isEmpty()) throw IllegalStateException("That file is empty")
        val resolvedMime = mime?.substringBefore(';')?.trim()
            .orEmpty().ifBlank { guessMimeFromName(fallback) }
        val safe = sanitizeFileName(fallback.ifBlank { "upload" }, resolvedMime)
        val file = uniqueFile(dir(), safe)
        file.writeBytes(bytes)
        prune()
        return StoredAttachment(
            name = safe,
            mimeType = resolvedMime.ifBlank { guessMimeFromName(safe) },
            sizeBytes = bytes.size.toLong(),
            kind = kindForUpload(resolvedMime, safe),
            localPath = file.absolutePath
        )
    }

    fun readBytes(a: StoredAttachment): ByteArray? {
        if (a.localPath.isBlank()) return null
        return runCatching { File(a.localPath).takeIf { it.isFile }?.readBytes() }.getOrNull()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").takeIf { '.' in name }.orEmpty()
        var n = 1
        while (candidate.exists() && n < 100) {
            n++
            candidate = File(dir, if (ext.isEmpty()) "$base-$n" else "$base-$n.$ext")
        }
        return candidate
    }

    private fun prune() {
        val files = dir().listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_UPLOADS_KEPT).forEach { runCatching { it.delete() } }
    }

    private fun java.io.InputStream.readCapped(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) {
                out.write(buf, 0, n)
                break
            }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

/**
 * Local content for the agent prompt: file name → excerpt/metadata.
 * Images report format/dimensions/size (text models can't see pixels);
 * PDFs and text docs report an excerpt; anything else reports metadata.
 */
suspend fun describeUploads(
    store: AndroidUploadStore,
    attachments: List<StoredAttachment>,
    perFileChars: Int = MAX_ATTACHMENT_CHARS
): Map<String, String> = withContext(Dispatchers.IO) {
    val out = linkedMapOf<String, String>()
    for (a in attachments) {
        val bytes = store.readBytes(a)
        if (bytes == null || bytes.isEmpty()) {
            out[a.name] = "(file is no longer on this device — ask the user to re-attach it)"
            continue
        }
        val ext = a.name.substringAfterLast('.', "").lowercase()
        val mime = a.mimeType.lowercase()
        out[a.name] = when {
            a.kind == AttachmentKind.IMAGE || mime.startsWith("image/") -> {
                if (ext == "svg" || mime == "image/svg+xml") {
                    val text = String(bytes, Charsets.UTF_8).replace(Regex("\\s+"), " ").trim()
                    "SVG image (${formatFileSize(bytes.size.toLong())}):\n" +
                        text.take(perFileChars)
                } else {
                    "Image (${describeImage(bytes)}" +
                        (if (a.mimeType.isNotBlank()) ", ${a.mimeType}" else "") +
                        "). Pixel content isn't directly visible to this text model — " +
                        "report the format/size and ask the user what it shows if needed."
                }
            }
            ext == "pdf" || mime == "application/pdf" ->
                extractPdfTextSnippet(bytes, perFileChars)
            ext in textExtensions || mime.startsWith("text/") ||
                mime == "application/json" || mime == "application/xml" ->
                String(bytes, Charsets.UTF_8).trim().take(perFileChars)
                    .ifBlank { "(empty text file)" }
            else -> "Binary file (${a.mimeType.ifBlank { "unknown type" }}, " +
                "${formatFileSize(bytes.size.toLong())}) — content isn't readable as text."
        }
    }
    out
}
