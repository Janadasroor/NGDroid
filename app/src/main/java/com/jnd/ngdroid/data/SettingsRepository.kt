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

class SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setSettings(newSettings: AppSettings) {
        _settings.value = newSettings
    }

    fun updateThemeMode(mode: ThemeMode) {
        _settings.update { it.copy(themeMode = mode) }
    }

    fun updateAccentColor(accent: AccentColorTheme) {
        _settings.update { it.copy(accentColorTheme = accent) }
    }

    fun updateEditorFontSize(fontSize: Int) {
        _settings.update { it.copy(editorFontSizeSp = fontSize) }
    }

    fun toggleShowLineNumbers(show: Boolean) {
        _settings.update { it.copy(showLineNumbers = show) }
    }

    fun updateTraceStrokeWidth(width: Float) {
        _settings.update { it.copy(traceStrokeWidthDp = width) }
    }

    fun toggleShowGridLines(show: Boolean) {
        _settings.update { it.copy(showGridLines = show) }
    }

    fun toggleShowDataPoints(show: Boolean) {
        _settings.update { it.copy(showDataPoints = show) }
    }

    fun toggleDarkPlotBackground(dark: Boolean) {
        _settings.update { it.copy(darkPlotBackground = dark) }
    }
}
