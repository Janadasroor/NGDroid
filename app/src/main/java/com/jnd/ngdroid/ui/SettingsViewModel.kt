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

package com.jnd.ngdroid.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.data.SettingsDataStore
import com.jnd.ngdroid.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val dataStore: SettingsDataStore
) : AndroidViewModel(application) {
    // Single-arg ctor for the framework factory (which only knows (Application));
    // tests can use the primary ctor to inject a fake store.
    constructor(application: Application) : this(application, SettingsDataStore(application))
    // Single source of truth: in-memory repo mirrors DataStore.
    // UI reads from this repo; all writes go through updateSettings() below.
    val settingsRepository = SettingsRepository()

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    init {
        viewModelScope.launch {
            try {
                val savedSettings = dataStore.settingsFlow.first()
                settingsRepository.setSettings(savedSettings)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to load settings", e)
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = settingsRepository.settings.value
        val updated = transform(current)
        settingsRepository.setSettings(updated)
        viewModelScope.launch {
            try {
                dataStore.updateSettings(updated)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to persist settings", e)
            }
        }
    }
}
