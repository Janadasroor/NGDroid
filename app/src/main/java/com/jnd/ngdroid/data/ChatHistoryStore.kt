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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chatDataStore by preferencesDataStore(name = "ngdroid_chats")

/**
 * Escaped-string codec for chat sessions. Mirrors NetlistJsonCodec:
 * `|` and newlines are escaped so raw text can never break the format.
 */
object ChatJsonCodec {
    private const val SESSION_SEP = "\n---CHAT_SEPARATOR---\n"
    private const val MSG_SEP = "\n---MSG_SEPARATOR---\n"

    fun encode(sessions: List<ChatSession>): String {
        return sessions.joinToString(SESSION_SEP) { s ->
            listOf(
                escape(s.id),
                escape(s.title),
                escape(s.providerName),
                escape(s.model),
                s.createdAtMillis.toString(),
                s.updatedAtMillis.toString(),
                s.messages.joinToString(MSG_SEP) { m ->
                    "${escape(m.id)}|${m.role.name}|${m.timestampMillis}|${escape(m.text)}|" +
                        escape(AttachmentCodec.encode(m.attachments))
                }
            ).joinToString("\n")
        }
    }

    fun decode(raw: String): List<ChatSession> {
        if (raw.isBlank()) return emptyList()
        return raw.split(SESSION_SEP).mapNotNull { block ->
            // First 6 lines are header, remainder is the joined message blob.
            val lines = block.split("\n", limit = 7)
            if (lines.size < 7) return@mapNotNull null
            val messages = if (lines[6].isEmpty()) {
                emptyList()
            } else {
                lines[6].split(MSG_SEP).mapNotNull { mline ->
                    // 5 tokens with attachments; legacy 4-token rows decode with none.
                    val tokens = mline.split("|", limit = 5)
                    if (tokens.size == 4 || tokens.size == 5) {
                        val role = try {
                            StoredMsgRole.valueOf(tokens[1])
                        } catch (_: Exception) {
                            null
                        } ?: return@mapNotNull null
                        StoredMsg(
                            id = unescape(tokens[0]),
                            role = role,
                            timestampMillis = tokens[2].toLongOrNull() ?: System.currentTimeMillis(),
                            text = unescape(tokens[3]),
                            attachments = if (tokens.size == 5) {
                                AttachmentCodec.decode(unescape(tokens[4]))
                            } else emptyList()
                        )
                    } else null
                }
            }
            ChatSession(
                id = unescape(lines[0]),
                title = unescape(lines[1]),
                providerName = unescape(lines[2]),
                model = unescape(lines[3]),
                createdAtMillis = lines[4].toLongOrNull() ?: System.currentTimeMillis(),
                updatedAtMillis = lines[5].toLongOrNull() ?: System.currentTimeMillis(),
                messages = messages
            )
        }
    }

    private fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '|' -> append("\\p")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }

    /**
     * Single-pass decode: chained replaces corrupted `\` + `p`/`n` sequences
     * (e.g. LaTeX `\pi` decoded as `|i`). Each `\x` pair resolves atomically.
     */
    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { append('\\'); i += 2; continue }
                    'p' -> { append('|'); i += 2; continue }
                    'n' -> { append('\n'); i += 2; continue }
                }
            }
            append(c)
            i++
        }
    }
}

class ChatHistoryStore(private val context: Context) {

    private object Keys {
        val CHATS_JSON = stringPreferencesKey("chats_json")
        val ACTIVE_ID = stringPreferencesKey("active_chat_id")
    }

    val chatsFlow: Flow<List<ChatSession>> = context.chatDataStore.data.map {
        ChatJsonCodec.decode(it[Keys.CHATS_JSON] ?: "")
            .sortedByDescending { s -> s.updatedAtMillis }
    }

    val activeIdFlow: Flow<String?> = context.chatDataStore.data.map { it[Keys.ACTIVE_ID] }

    suspend fun saveChats(sessions: List<ChatSession>) {
        val pruned = sessions
            .sortedByDescending { it.updatedAtMillis }
            .take(MAX_SAVED_CHATS)
            .map { s ->
                if (s.messages.size > MAX_MESSAGES_PER_CHAT) {
                    // Keep head (first prompt) + newest tail so resume stays coherent.
                    val head = s.messages.take(1)
                    val tail = s.messages.takeLast(MAX_MESSAGES_PER_CHAT - 1)
                    s.copy(messages = (head + tail).distinctBy { it.id })
                } else s
            }
        context.chatDataStore.edit { prefs ->
            prefs[Keys.CHATS_JSON] = ChatJsonCodec.encode(pruned)
        }
    }

    suspend fun setActiveId(id: String?) {
        context.chatDataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.ACTIVE_ID) else prefs[Keys.ACTIVE_ID] = id
        }
    }
}
