/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.jnd.ngdroid.agent.LlmImage
import com.jnd.ngdroid.agent.describeImage
import com.jnd.ngdroid.agent.extractPdfTextSnippet
import com.jnd.ngdroid.agent.formatFileSize
import com.jnd.ngdroid.agent.guessMimeFromName
import com.jnd.ngdroid.agent.sanitizeFileName
import com.jnd.ngdroid.ui.assistant.MAX_ATTACHMENT_CHARS
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val MAX_UPLOAD_BYTES = 15 * 1024 * 1024
private const val MAX_UPLOADS_KEPT = 20
/** Longest side for vision payloads; keeps base64 small while staying readable. */
const val MAX_VISION_SIDE = 1280
/** Originals under this size + dimensions pass through without recompression. */
private const val VISION_PASSTHROUGH_BYTES = 600 * 1024

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
 * Raster images ALSO travel as vision payloads ([loadVisionImages]); the
 * text here is grounding metadata. PDFs/docs report an excerpt, anything
 * else reports metadata.
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
                        "). The full pixel content is also attached as a vision image — " +
                        "look at it directly and describe what it shows."
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

/** True for raster images we can send as vision (SVG travels as text instead). Pure. */
fun isVisionImage(mimeType: String, fileName: String): Boolean {
    val mime = mimeType.substringBefore(';').trim().lowercase()
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext == "svg" || mime == "image/svg+xml") return false
    if (mime.startsWith("image/")) return true
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico")
}

fun visionMimeFor(mimeType: String, fileName: String): String {
    val mime = mimeType.substringBefore(';').trim().lowercase()
    if (mime.startsWith("image/") && mime != "image/svg+xml") return mime
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }
}

/**
 * Vision payloads for the current turn: downscaled + base64-encoded rasters,
 * max 4. Small originals pass through untouched; large ones are resized to
 * [MAX_VISION_SIDE] and JPEG-compressed so free-tier gateways stay happy.
 */
suspend fun loadVisionImages(
    store: AndroidUploadStore,
    attachments: List<StoredAttachment>,
    maxImages: Int = 4
): List<LlmImage> = withContext(Dispatchers.IO) {
    val out = mutableListOf<LlmImage>()
    for (a in attachments.take(maxImages)) {
        if (!isVisionImage(a.mimeType, a.name)) continue
        val raw = store.readBytes(a) ?: continue
        if (raw.isEmpty()) continue
        try {
            val (bytes, mime) = downscaleForVision(raw, visionMimeFor(a.mimeType, a.name))
            if (bytes.isEmpty() || bytes.size > MAX_UPLOAD_BYTES) continue
            out.add(LlmImage(mime, Base64.encodeToString(bytes, Base64.NO_WRAP), a.name))
        } catch (_: Exception) { }
    }
    out
}

/** Resize + recompress when needed; returns (bytes to send, mime to claim). */
fun downscaleForVision(raw: ByteArray, mime: String): Pair<ByteArray, String> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    // Unparseable (or already small): send the original bytes as-is.
    if (w <= 0 || h <= 0) return raw to mime
    if (raw.size <= VISION_PASSTHROUGH_BYTES && maxOf(w, h) <= MAX_VISION_SIDE) {
        return raw to mime
    }
    val scale = MAX_VISION_SIDE.toFloat() / maxOf(w, h).toFloat()
    val targetW = (w * scale).toInt().coerceAtLeast(1)
    val targetH = (h * scale).toInt().coerceAtLeast(1)
    // Sample down first to avoid allocating a huge bitmap on 1440x1920+ photos.
    var sample = 1
    while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH && sample < 8) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return raw to mime
    return try {
        val scaled = if (decoded.width != targetW || decoded.height != targetH) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        } else decoded
        val stream = ByteArrayOutputStream()
        // JPEG keeps photos small; schematics stay readable at quality 82.
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        if (scaled !== decoded) runCatching { scaled.recycle() }
        runCatching { decoded.recycle() }
        val bytes = stream.toByteArray()
        if (bytes.isEmpty()) raw to mime else bytes to "image/jpeg"
    } catch (_: Exception) {
        raw to mime
    }
}
