package com.jnd.ngdroid.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val fileJson = Json { ignoreUnknownKeys = true }

/** Raw bytes fetch for downloads: (url, headers) -> fetched file. Throws on non-2xx. */
typealias HttpBytes = (url: String, headers: Map<String, String>) -> FetchedFile

data class FetchedFile(
    val bytes: ByteArray,
    val contentType: String = "",
    val finalUrl: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FetchedFile) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType && finalUrl == other.finalUrl
    }
    override fun hashCode(): Int {
        var r = bytes.contentHashCode()
        r = 31 * r + contentType.hashCode()
        r = 31 * r + finalUrl.hashCode()
        return r
    }
}

data class SavedFile(
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    /** User-visible location, e.g. `Downloads/NGDroid/x.pdf`. */
    val displayPath: String,
    /** Agent-readable internal path (may equal displayPath on JVM). */
    val internalPath: String = displayPath
)

/** Storage behind download_file/read_file. Android saves to Downloads + app files; tests use memory. */
interface AssistantFileStore {
    fun save(fileName: String, bytes: ByteArray, mimeType: String): SavedFile
    fun list(): List<SavedFile>
    fun readBytes(fileName: String): ByteArray?
    fun findFile(query: String): SavedFile?
}

/** JVM-testable in-memory store. */
class InMemoryFileStore : AssistantFileStore {
    private val files = linkedMapOf<String, Pair<ByteArray, String>>()
    override fun save(fileName: String, bytes: ByteArray, mimeType: String): SavedFile {
        val safe = sanitizeFileName(fileName)
        files[safe] = bytes.copyOf() to mimeType
        return SavedFile(safe, bytes.size.toLong(), mimeType, "Downloads/NGDroid/$safe")
    }
    override fun list(): List<SavedFile> = files.map { (name, v) ->
        SavedFile(name, v.first.size.toLong(), v.second, "Downloads/NGDroid/$name")
    }
    override fun readBytes(fileName: String): ByteArray? {
        findFile(fileName)?.let { return files[it.fileName]?.first?.copyOf() }
        return null
    }
    override fun findFile(query: String): SavedFile? {
        val q = query.trim().substringAfterLast('/').trim()
        if (q.isEmpty()) return null
        files.keys.firstOrNull { it.equals(q, ignoreCase = true) }?.let { name ->
            val v = files[name]!!
            return SavedFile(name, v.first.size.toLong(), v.second, "Downloads/NGDroid/$name")
        }
        // Suffix match so "NGDroid/x.pdf" or partial names resolve.
        files.keys.firstOrNull { it.lowercase().endsWith(q.lowercase()) }?.let { name ->
            val v = files[name]!!
            return SavedFile(name, v.first.size.toLong(), v.second, "Downloads/NGDroid/$name")
        }
        return null
    }
}

const val MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024
const val MAX_READ_CHARS = 12000

fun guessExtensionFromMime(mime: String): String {
    val m = mime.substringBefore(';').trim().lowercase()
    return when (m) {
        "application/pdf" -> "pdf"
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        "image/svg+xml" -> "svg"
        "image/x-icon", "image/vnd.microsoft.icon" -> "ico"
        "text/csv" -> "csv"
        "text/plain" -> "txt"
        "text/markdown" -> "md"
        "text/html" -> "html"
        "application/json" -> "json"
        "application/zip" -> "zip"
        else -> ""
    }
}

fun guessMimeFromName(fileName: String, fallback: String = ""): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val guessed = when (ext) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "ico" -> "image/x-icon"
        "csv" -> "text/csv"
        "txt", "net", "cir", "spice", "lib", "mod", "log" -> "text/plain"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "xml" -> "application/xml"
        "zip" -> "application/zip"
        "mp4" -> "video/mp4"
        else -> ""
    }
    if (guessed.isNotEmpty()) return guessed
    val clean = fallback.substringBefore(';').trim()
    return clean.ifEmpty { "application/octet-stream" }
}

/**
 * Sanitize a file name for storage. Pure; JVM-testable.
 * Strips paths, illegal chars, caps length, adds extension from MIME when missing.
 */
fun sanitizeFileName(raw: String, contentType: String = ""): String {
    var name = raw.trim().substringAfterLast('/').substringAfterLast('\\')
        .substringBefore('?').substringBefore('#').trim()
    if (name.isEmpty()) name = "download"
    // URL-decode best-effort (%20 etc), keep literal on failure.
    name = runCatching { java.net.URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
    name = name.replace(Regex("[^A-Za-z0-9._()+\\- ]"), "_").trim().trim('.', ' ')
    if (name.isEmpty()) name = "download"
    if ('.' !in name) {
        val ext = guessExtensionFromMime(contentType)
        if (ext.isNotEmpty()) name = "$name.$ext"
    }
    if (name.length > 80) {
        val ext = name.substringAfterLast('.', "")
        val base = name.substringBeforeLast('.', name)
        name = if ('.' in name && ext.length <= 5) {
            base.take(80 - ext.length - 1) + "." + ext
        } else name.take(80)
    }
    return name
}

/** Derive a file name from URL + optional hint + content type. Pure. */
fun fileNameForDownload(url: String, hint: String, contentType: String): String {
    val h = hint.trim()
    if (h.isNotEmpty()) return sanitizeFileName(h, contentType)
    val last = url.substringBefore('?').substringBefore('#').substringAfterLast('/').trim()
    if (last.isNotEmpty() && '.' in last && last.length <= 80) {
        return sanitizeFileName(last, contentType)
    }
    val ext = guessExtensionFromMime(contentType).ifEmpty {
        when {
            url.contains(".pdf", ignoreCase = true) -> "pdf"
            url.contains(".png", ignoreCase = true) -> "png"
            else -> "bin"
        }
    }
    return sanitizeFileName("download.$ext", contentType)
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes / 102.4).toInt() / 10.0} KB"
    else -> "${(bytes / 104857.6).toInt() / 10.0} MB"
}

private fun argFileString(argsJson: String, vararg keys: String): String? = runCatching {
    val obj = fileJson.parseToJsonElement(argsJson).jsonObject
    keys.firstNotNullOfOrNull { obj[it]?.jsonPrimitive?.contentOrNull }
}.getOrNull()

private fun argFileInt(argsJson: String, key: String): Int? = runCatching {
    fileJson.parseToJsonElement(argsJson).jsonObject[key]?.jsonPrimitive?.intOrNull
}.getOrNull()

/**
 * Best-effort PDF text sniff: page count via `/Count`/`/Type /Page`, text via
 * parenthesized strings inside BT..ET blocks (uncompressed parts). Pure.
 * Scanned/compressed PDFs yield metadata + a short honest note.
 */
fun extractPdfTextSnippet(bytes: ByteArray, maxChars: Int = 4000): String {
    val latin = String(bytes, Charsets.ISO_8859_1)
    val pages = Regex("""/Count\s+(\d+)""").findAll(latin)
        .mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
        ?: Regex("""/Type\s*/Page[^s]""").findAll(latin).count().takeIf { it > 0 }
    val blocks = Regex("BT(.*?)ET", RegexOption.DOT_MATCHES_ALL).findAll(latin)
        .map { it.groupValues[1] }.toList()
    val scope = if (blocks.isNotEmpty()) blocks.joinToString(" ") else latin
    val out = StringBuilder()
    val re = Regex("""\((?:\\.|[^\\()])*\)""")
    for (m in re.findAll(scope)) {
        if (out.length >= maxChars) break
        var s = m.value.substring(1, m.value.length - 1)
        s = s.replace("\\n", "\n").replace("\\r", "\n").replace("\\t", " ")
            .replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
        s = s.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), " ").replace(Regex("\\s+"), " ").trim()
        if (s.length < 3) continue
        if (s.length > 400) s = s.take(400)
        out.append(s).append('\n')
    }
    val text = out.toString().trim().take(maxChars)
    return buildString {
        append("PDF")
        if (pages != null) append(", ~$pages page(s)")
        append(", ${formatFileSize(bytes.size.toLong())}.")
        if (text.isNotBlank()) {
            append("\nText excerpt:\n")
            append(text)
        } else {
            append(" No extractable text found (likely scanned or compressed images) — ")
            append("open the file from Downloads to view it.")
        }
    }
}

/** Image kind + dimensions from magic bytes where parseable. Pure. */
fun describeImage(bytes: ByteArray): String {
    if (bytes.size < 12) return "image, ${formatFileSize(bytes.size.toLong())} (too small to parse)"
    fun u16BE(at: Int): Int = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
    fun u32BE(at: Int): Long = ((bytes[at].toLong() and 0xFF) shl 24) or
        ((bytes[at + 1].toLong() and 0xFF) shl 16) or
        ((bytes[at + 2].toLong() and 0xFF) shl 8) or (bytes[at + 3].toLong() and 0xFF)
    // PNG: 89 50 4E 47 … IHDR width/height at 16/20.
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()) {
        return if (bytes.size >= 24) {
            "PNG ${u32BE(16)}x${u32BE(20)}, ${formatFileSize(bytes.size.toLong())}"
        } else "PNG, ${formatFileSize(bytes.size.toLong())}"
    }
    // JPEG: FF D8, scan for SOF0/2 markers for dimensions.
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
        var i = 2
        while (i + 8 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) { i++; continue }
            val marker = bytes[i + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0xD9 || (marker in 0xD0..0xD7)) { i += 2; continue }
            if (i + 3 >= bytes.size) break
            val len = u16BE(i + 2)
            if (len < 7 || i + len >= bytes.size) break
            if (marker == 0xC0 || marker == 0xC2) {
                val h = u16BE(i + 5); val w = u16BE(i + 7)
                return "JPEG ${w}x$h, ${formatFileSize(bytes.size.toLong())}"
            }
            i += 2 + len
        }
        return "JPEG, ${formatFileSize(bytes.size.toLong())}"
    }
    // GIF: width/height LE at 6/8.
    if (bytes.size >= 10 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()) {
        val w = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        val h = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        return "GIF ${w}x$h, ${formatFileSize(bytes.size.toLong())}"
    }
    // BMP: width/height LE at 18/22.
    if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() && bytes.size >= 26) {
        val w = (bytes[18].toInt() and 0xFF) or ((bytes[19].toInt() and 0xFF) shl 8)
        val h = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
        return "BMP ${w}x$h, ${formatFileSize(bytes.size.toLong())}"
    }
    // WEBP RIFF....WEBP.
    if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()) {
        return "WEBP, ${formatFileSize(bytes.size.toLong())}"
    }
    val head = String(bytes.take(512).toByteArray(), Charsets.UTF_8).trimStart()
    if (head.contains("<svg", ignoreCase = true)) return "SVG vector, ${formatFileSize(bytes.size.toLong())}"
    return "image, ${formatFileSize(bytes.size.toLong())}"
}

private val textExtensions = setOf(
    "txt", "csv", "md", "json", "xml", "html", "htm", "net", "cir",
    "spice", "lib", "mod", "log", "text", "asc", "tsv"
)
private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico")

class DownloadFileTool(
    private val httpBytes: HttpBytes,
    private val store: AssistantFileStore
) : AgentTool {
    override val name: String = "download_file"
    override val description: String =
        "Download a file (PDF datasheet, image, CSV, ZIP) into device storage " +
            "(Downloads/NGDroid) so the user can open it. " +
            "Input JSON: {\"url\": \"https://...\", \"fileName\": \"tl494.pdf\"}. " +
            "Returns the saved location + size; use read_file to inspect docs/images. " +
            "http(s) only, max 20 MB."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"url":{"type":"string"},""" +
            """"fileName":{"type":"string"},"file_name":{"type":"string"},""" +
            """"headers":{"type":"object"}},"required":["url"]}"""

    override suspend fun execute(argsJson: String): String {
        val url = argFileString(argsJson, "url")?.trim().orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "ERROR: only http(s) URLs are supported"
        }
        val hint = argFileString(argsJson, "fileName", "file_name", "name").orEmpty()
        val headers = runCatching {
            fileJson.parseToJsonElement(argsJson).jsonObject["headers"]?.jsonObject
                ?.entries?.mapNotNull { (k, v) ->
                    val value = v.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                    if (!k.matches(Regex("[A-Za-z0-9-]+")) || value.length > 2048) null else k to value
                }?.toMap()
        }.getOrNull().orEmpty()
        return try {
            val fetched = withContext(Dispatchers.IO) { httpBytes(url, headers) }
            if (fetched.bytes.isEmpty()) return "ERROR: empty download at $url"
            if (fetched.bytes.size > MAX_DOWNLOAD_BYTES) {
                return "ERROR: file is ${formatFileSize(fetched.bytes.size.toLong())} " +
                    "(max 20 MB) — link it instead of downloading."
            }
            val mime = fetched.contentType.substringBefore(';').trim()
                .ifEmpty { guessMimeFromName(url, "") }
            val name = fileNameForDownload(fetched.finalUrl.ifBlank { url }, hint, mime)
            val saved = store.save(name, fetched.bytes, mime.ifBlank { guessMimeFromName(name) })
            buildString {
                append("Saved ${saved.fileName} (${formatFileSize(saved.sizeBytes)}")
                if (saved.mimeType.isNotBlank()) append(", ${saved.mimeType}")
                append(") to ${saved.displayPath}.")
                append(" Use read_file {\"fileName\": \"${saved.fileName}\"} to inspect it.")
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}

class ReadFileTool(private val store: AssistantFileStore) : AgentTool {
    override val name: String = "read_file"
    override val description: String =
        "Read a downloaded file (docs, text, images) from device storage. " +
            "Input JSON: {\"fileName\": \"tl494.pdf\", \"maxChars\": 4000}. " +
            "No fileName lists saved files. Text returns an excerpt; PDFs return " +
            "metadata + text excerpt; images return format/dimensions/size."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"fileName":{"type":"string"},""" +
            """"file_name":{"type":"string"},"path":{"type":"string"},""" +
            """"name":{"type":"string"},"maxChars":{"type":"integer"}}}"""

    override suspend fun execute(argsJson: String): String {
        val query = argFileString(argsJson, "fileName", "file_name", "path", "name")?.trim().orEmpty()
        if (query.isBlank()) return listFiles()
        val found = store.findFile(query)
            ?: return "ERROR: no saved file matches '$query'.\n" + listFiles()
        val bytes = store.readBytes(found.fileName) ?: return "ERROR: could not read ${found.fileName}"
        if (bytes.isEmpty()) return "${found.fileName}: empty file."
        val maxChars = argFileInt(argsJson, "maxChars")?.coerceIn(500, MAX_READ_CHARS) ?: 4000
        val ext = found.fileName.substringAfterLast('.', "").lowercase()
        val mime = found.mimeType.lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> {
                "${found.fileName} (${formatFileSize(bytes.size.toLong())}, " +
                    "at ${found.displayPath}):\n" + extractPdfTextSnippet(bytes, maxChars)
            }
            ext in imageExtensions || mime.startsWith("image/") -> {
                if (ext == "svg" || mime == "image/svg+xml") {
                    val text = String(bytes, Charsets.UTF_8).replace(Regex("\\s+"), " ").trim()
                    "${found.fileName} (${describeImage(bytes)}, at ${found.displayPath}):\n" +
                        text.take(maxChars)
                } else {
                    "${found.fileName} (${describeImage(bytes)}, at ${found.displayPath}). " +
                        "Pixel content is not directly visible to this text model — " +
                        "report the format/size to the user and point them at the saved file."
                }
            }
            ext in textExtensions || mime.startsWith("text/") ||
                mime == "application/json" || mime == "application/xml" -> {
                val text = String(bytes, Charsets.UTF_8).trim()
                if (text.isEmpty()) "${found.fileName}: empty text file."
                else "${found.fileName} (${formatFileSize(bytes.size.toLong())}, at ${found.displayPath}):\n" +
                    text.take(maxChars)
            }
            else -> "${found.fileName} (${formatFileSize(bytes.size.toLong())}, " +
                "${found.mimeType.ifBlank { "binary" }}, at ${found.displayPath}). " +
                "Binary preview is not supported — share the saved location with the user."
        }
    }

    private fun listFiles(): String {
        val files = store.list()
        if (files.isEmpty()) {
            return "No downloaded files yet. Use download_file {\"url\": \"https://...\"} first."
        }
        return "Saved files:\n" + files.joinToString("\n") { f ->
            "- ${f.fileName} (${formatFileSize(f.sizeBytes)}" +
                (if (f.mimeType.isNotBlank()) ", ${f.mimeType}" else "") +
                ", ${f.displayPath})"
        } + "\nUse read_file {\"fileName\": \"<name>\"} to inspect one."
    }
}
