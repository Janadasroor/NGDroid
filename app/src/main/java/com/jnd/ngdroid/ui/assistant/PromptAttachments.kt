package com.jnd.ngdroid.ui.assistant

import com.jnd.ngdroid.data.StoredAttachment

/**
 * Pure prompt-attachment helpers. No Android imports — JVM-testable.
 *
 * Raster images ALSO travel as vision payloads ([com.jnd.ngdroid.agent.LlmImage]):
 * appended text here is grounding metadata, the pixels ride alongside.
 */

const val MAX_ATTACHMENTS_PER_MESSAGE = 4
const val MAX_ATTACHMENT_CHARS = 3000

/** `Paper.pdf, Board.png` ref line kept in agent history for past turns. Pure. */
fun attachmentRefLine(attachments: List<StoredAttachment>): String {
    if (attachments.isEmpty()) return ""
    return "[Attached: ${attachments.joinToString(", ") { it.name.ifBlank { "file" } }}]"
}

/**
 * Full agent-side user text: the typed prompt plus one block per attached
 * file with its locally read content. Pure; [bodies] maps file name to the
 * excerpt/metadata (missing entries degrade to the header alone).
 */
fun buildAgentUserText(clean: String, bodies: Map<String, String>): String {
    if (bodies.isEmpty()) return clean
    return buildString {
        append(clean)
        var n = 0
        for ((name, body) in bodies) {
            n++
            append("\n\n[Attached file $n: ${name.ifBlank { "file" }}]\n")
            val clipped = body.trim().take(MAX_ATTACHMENT_CHARS)
            if (clipped.isNotEmpty()) append(clipped)
            else append("(no readable content — describe the file location to the user)")
        }
    }
}
