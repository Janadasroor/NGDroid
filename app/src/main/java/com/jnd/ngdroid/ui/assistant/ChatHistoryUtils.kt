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
 * Normalizes the composer draft per keystroke: whitespace-only input
 * collapses to empty (placeholder returns, single-line height); real
 * drafts pass through untouched.
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
    timestampMillis = System.currentTimeMillis(),
    attachments = attachments
)

fun StoredMsg.toUi(): ChatMsg = ChatMsg(
    id = id,
    role = when (role) {
        StoredMsgRole.USER -> ChatRoleUi.USER
        StoredMsgRole.ASSISTANT -> ChatRoleUi.ASSISTANT
        StoredMsgRole.SYSTEM -> ChatRoleUi.SYSTEM
    },
    text = text,
    attachments = attachments
)

/** Case-insensitive title/message search for the drawer search box. */
fun filterSessions(sessions: List<ChatSession>, query: String): List<ChatSession> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return sessions
    return sessions.filter { s ->
        s.title.lowercase().contains(q) ||
            s.messages.any { m ->
                m.text.lowercase().contains(q) ||
                    m.attachments.any { it.name.lowercase().contains(q) }
            }
    }
}
