package com.jnd.ngdroid.data

import java.util.UUID

const val MAX_SAVED_CHATS = 50
const val MAX_MESSAGES_PER_CHAT = 200

enum class StoredMsgRole { USER, ASSISTANT, SYSTEM }

enum class AttachmentKind { IMAGE, DOC, OTHER }

/**
 * File attached to a user prompt (image or doc picked from storage).
 * [localPath] is the app-private uploads copy the agent reads; the chat
 * codec persists the metadata so resumes keep working while the copy lasts.
 */
data class StoredAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val kind: AttachmentKind = AttachmentKind.OTHER,
    val localPath: String = ""
)

data class StoredMsg(
    val id: String = UUID.randomUUID().toString(),
    val role: StoredMsgRole = StoredMsgRole.USER,
    val text: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val attachments: List<StoredAttachment> = emptyList()
)

/**
 * Attachment list codec for the 5th chat-codec token. Records split on
 * U+001E, fields on U+001F, with backslash-escaping for both. Pure.
 */
object AttachmentCodec {
    private const val REC = '\u001E'
    private const val FLD = '\u001F'

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            REC -> append("\\r")
            FLD -> append("\\f")
            else -> append(c)
        }
    }

    private fun unesc(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { append('\\'); i += 2; continue }
                    'r' -> { append(REC); i += 2; continue }
                    'f' -> { append(FLD); i += 2; continue }
                }
            }
            append(c)
            i++
        }
    }

    fun encode(list: List<StoredAttachment>): String = list.joinToString(REC.toString()) { a ->
        listOf(a.id, a.name, a.mimeType, a.sizeBytes.toString(), a.kind.name, a.localPath)
            .joinToString(FLD.toString()) { esc(it) }
    }

    fun decode(raw: String): List<StoredAttachment> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(REC).mapNotNull { rec ->
            val f = rec.split(FLD).map(::unesc)
            if (f.size != 6) return@mapNotNull null
            StoredAttachment(
                id = f[0],
                name = f[1],
                mimeType = f[2],
                sizeBytes = f[3].toLongOrNull() ?: 0L,
                kind = try { AttachmentKind.valueOf(f[4]) } catch (_: Exception) {
                    AttachmentKind.OTHER
                },
                localPath = f[5]
            )
        }
    }
}

/** IMAGE for raster/vector images, DOC for readable docs, OTHER for the rest. Pure. */
fun kindForUpload(mimeType: String, fileName: String): AttachmentKind {
    val mime = mimeType.substringBefore(';').trim().lowercase()
    if (mime.startsWith("image/")) return AttachmentKind.IMAGE
    if (mime.startsWith("text/") || mime == "application/pdf" ||
        mime == "application/json" || mime == "application/xml"
    ) return AttachmentKind.DOC
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico" -> AttachmentKind.IMAGE
        "pdf", "txt", "csv", "md", "json", "xml", "html", "htm", "log" -> AttachmentKind.DOC
        else -> AttachmentKind.OTHER
    }
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val providerName: String = "",
    val model: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val messages: List<StoredMsg> = emptyList()
)
