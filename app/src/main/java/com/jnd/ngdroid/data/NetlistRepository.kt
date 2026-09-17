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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory mirror of NetlistDataStore (same pattern as SettingsRepository).
 * ViewModel is the only writer; UI collects these flows.
 */
class NetlistRepository {
    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _library = MutableStateFlow<List<SavedNetlist>>(emptyList())
    val library: StateFlow<List<SavedNetlist>> = _library.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _autosave = MutableStateFlow(AutosaveUiState())
    val autosave: StateFlow<AutosaveUiState> = _autosave.asStateFlow()

    private val _recentFiles = MutableStateFlow<List<RecentFile>>(emptyList())
    val recentFiles: StateFlow<List<RecentFile>> = _recentFiles.asStateFlow()

    fun setDraft(text: String) {
        _draftText.value = text
    }

    fun setLibrary(list: List<SavedNetlist>) {
        _library.value = list.sortedByDescending { it.updatedAtMillis }.take(MAX_SAVED_NETLISTS)
    }

    fun setActiveId(id: String?) {
        _activeId.value = id
    }

    fun setAutosave(state: AutosaveUiState) {
        _autosave.value = state
    }

    fun setRecentFiles(list: List<RecentFile>) {
        _recentFiles.value = list.sortedByDescending { it.openedAtMillis }.take(MAX_RECENT_FILES)
    }

    fun markDirty() {
        _autosave.update { it.copy(status = AutosaveStatus.DIRTY) }
    }
}
