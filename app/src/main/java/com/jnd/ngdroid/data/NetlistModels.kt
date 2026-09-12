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
