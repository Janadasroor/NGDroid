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

import java.util.UUID

const val MAX_SAVED_NETLISTS = 50
const val MAX_RECENT_FILES = 5
const val MAX_FILE_CHARS = 200_000

data class SavedNetlist(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val netlist: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class RecentFile(
    val uri: String,
    val name: String,
    val openedAtMillis: Long = System.currentTimeMillis()
)

enum class AutosaveStatus {
    SAVED, SAVING, DIRTY
}

data class AutosaveUiState(
    val status: AutosaveStatus = AutosaveStatus.SAVED,
    val lastSavedAtMillis: Long? = null
)
