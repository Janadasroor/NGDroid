package com.jnd.ngdroid.ui.assistant

import com.jnd.ngdroid.data.ChatSession
import com.jnd.ngdroid.data.StoredMsg
import com.jnd.ngdroid.data.StoredMsgRole

/**
 * Pure chat-history helpers. No Android imports — JVM-testable.
 */

/** Drawer title from the first user message: single line, ~40 chars. */
fun buildTitle(firstUserText: String, fallback: String = "New chat"): String {
    val single = firstUserText.trim().replace(Regex("\\s+"), " ")
    if (single.isEmpty()) return fallback
    return if (single.length <= 42) single else single.take(41).trimEnd() + "…"
}

/**
 * Normalizes the composer draft on every keystroke: whitespace-only input
 * (spaces, newlines) collapses to empty so the input bar falls back to its
 * single-line height and the placeholder returns. Real drafts pass through
 * untouched, preserving in-progress multi-line text.
 */
fun normalizeChatInput(raw: String): String =
    if (raw.isBlank()) "" else raw

/** Keep messages up to and including [msgId]; unknown id returns the list unchanged. */
fun truncateAfter(messages: List<ChatMsg>, msgId: String): List<ChatMsg> {
    val idx = messages.indexOfFirst { it.id == msgId }
    if (idx < 0) return messages
    return messages.subList(0, idx + 1)
}

fun ChatMsg.toStored(): StoredMsg = StoredMsg(
    id = id,
    role = when (role) {
        ChatRoleUi.USER -> StoredMsgRole.USER
        ChatRoleUi.ASSISTANT -> StoredMsgRole.ASSISTANT
        ChatRoleUi.SYSTEM -> StoredMsgRole.SYSTEM
    },
    text = text,
    timestampMillis = System.currentTimeMillis()
)

fun StoredMsg.toUi(): ChatMsg = ChatMsg(
    id = id,
    role = when (role) {
        StoredMsgRole.USER -> ChatRoleUi.USER
        StoredMsgRole.ASSISTANT -> ChatRoleUi.ASSISTANT
        StoredMsgRole.SYSTEM -> ChatRoleUi.SYSTEM
    },
    text = text
)

/** Case-insensitive title/message search for the drawer search box. */
fun filterSessions(sessions: List<ChatSession>, query: String): List<ChatSession> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return sessions
    return sessions.filter { s ->
        s.title.lowercase().contains(q) ||
            s.messages.any { it.text.lowercase().contains(q) }
    }
}
