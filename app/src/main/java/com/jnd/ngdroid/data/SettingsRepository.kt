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
