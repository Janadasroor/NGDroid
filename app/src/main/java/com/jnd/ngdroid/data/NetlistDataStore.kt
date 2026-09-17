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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.netlistDataStore by preferencesDataStore(name = "ngdroid_netlists")

object NetlistJsonCodec {
    fun encode(list: List<SavedNetlist>): String {
        return list.joinToString("\n---NETLIST_SEPARATOR---\n") { item ->
            "${escape(item.id)}|${escape(item.title)}|${item.updatedAtMillis}|${escape(item.netlist)}"
        }
    }

    fun decode(raw: String): List<SavedNetlist> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.split("\n---NETLIST_SEPARATOR---\n")
        return parts.mapNotNull { line ->
            val tokens = line.split("|", limit = 4)
            if (tokens.size == 4) {
                SavedNetlist(
                    id = unescape(tokens[0]),
                    title = unescape(tokens[1]),
                    updatedAtMillis = tokens[2].toLongOrNull() ?: System.currentTimeMillis(),
                    netlist = unescape(tokens[3])
                )
            } else null
        }
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")
    private fun unescape(s: String): String = s.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\")
}

object RecentFileCodec {
    fun encode(list: List<RecentFile>): String {
        return list.joinToString("\n") { "${escape(it.uri)}|${escape(it.name)}|${it.openedAtMillis}" }
    }

    fun decode(raw: String): List<RecentFile> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val tokens = line.split("|", limit = 3)
            if (tokens.size == 3) {
                RecentFile(
                    uri = unescape(tokens[0]),
                    name = unescape(tokens[1]),
                    openedAtMillis = tokens[2].toLongOrNull() ?: System.currentTimeMillis()
                )
            } else null
        }
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")
    private fun unescape(s: String): String = s.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\")
}

class NetlistDataStore(private val context: Context) {

    private object Keys {
        val DRAFT_TEXT = stringPreferencesKey("draft_text")
        val DRAFT_UPDATED = longPreferencesKey("draft_updated")
        val LIBRARY_JSON = stringPreferencesKey("saved_netlists_json")
        val ACTIVE_ID = stringPreferencesKey("active_netlist_id")
        val RECENT_FILES = stringPreferencesKey("recent_files")
    }

    val draftTextFlow: Flow<String?> = context.netlistDataStore.data.map { it[Keys.DRAFT_TEXT] }
    val draftUpdatedFlow: Flow<Long?> = context.netlistDataStore.data.map { it[Keys.DRAFT_UPDATED] }
    val libraryFlow: Flow<List<SavedNetlist>> = context.netlistDataStore.data.map {
        NetlistJsonCodec.decode(it[Keys.LIBRARY_JSON] ?: "")
    }
    val activeIdFlow: Flow<String?> = context.netlistDataStore.data.map { it[Keys.ACTIVE_ID] }
    val recentFilesFlow: Flow<List<RecentFile>> = context.netlistDataStore.data.map {
        RecentFileCodec.decode(it[Keys.RECENT_FILES] ?: "")
    }

    suspend fun saveDraft(text: String) {
        context.netlistDataStore.edit { prefs ->
            prefs[Keys.DRAFT_TEXT] = text
            prefs[Keys.DRAFT_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun saveLibrary(list: List<SavedNetlist>) {
        val pruned = list.sortedByDescending { it.updatedAtMillis }.take(MAX_SAVED_NETLISTS)
        context.netlistDataStore.edit { prefs ->
            prefs[Keys.LIBRARY_JSON] = NetlistJsonCodec.encode(pruned)
        }
    }

    suspend fun setActiveId(id: String?) {
        context.netlistDataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.ACTIVE_ID) else prefs[Keys.ACTIVE_ID] = id
        }
    }

    suspend fun saveRecentFiles(list: List<RecentFile>) {
        val pruned = list.sortedByDescending { it.openedAtMillis }.take(MAX_RECENT_FILES)
        context.netlistDataStore.edit { prefs ->
            prefs[Keys.RECENT_FILES] = RecentFileCodec.encode(pruned)
        }
    }
}
