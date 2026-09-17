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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ngdroid_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val BUTTON_STYLE = stringPreferencesKey("button_style")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val DARK_PLOT_BG = booleanPreferencesKey("dark_plot_bg")
        val SHOW_GRID_LINES = booleanPreferencesKey("show_grid_lines")
        val SHOW_DATA_POINTS = booleanPreferencesKey("show_data_points")
        val TRACE_STROKE_WIDTH = floatPreferencesKey("trace_stroke_width")
        val DIALOG_CORNER_RADIUS = intPreferencesKey("dialog_corner_radius")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val themeModeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val accentColorStr = prefs[Keys.ACCENT_COLOR] ?: AccentColorTheme.CYAN.name
        val buttonStyleStr = prefs[Keys.BUTTON_STYLE] ?: ButtonStyle.ROUNDED.name

        AppSettings(
            themeMode = try { ThemeMode.valueOf(themeModeStr) } catch (e: Exception) { ThemeMode.SYSTEM },
            accentColorTheme = try { AccentColorTheme.valueOf(accentColorStr) } catch (e: Exception) { AccentColorTheme.CYAN },
            buttonStyle = try { ButtonStyle.valueOf(buttonStyleStr) } catch (e: Exception) { ButtonStyle.ROUNDED },
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 14,
            darkPlotBackground = prefs[Keys.DARK_PLOT_BG] ?: true,
            showGridLines = prefs[Keys.SHOW_GRID_LINES] ?: true,
            showDataPoints = prefs[Keys.SHOW_DATA_POINTS] ?: false,
            traceStrokeWidthDp = prefs[Keys.TRACE_STROKE_WIDTH] ?: 2.5f,
            dialogCornerRadiusDp = prefs[Keys.DIALOG_CORNER_RADIUS] ?: 28
        )
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.ACCENT_COLOR] = settings.accentColorTheme.name
            prefs[Keys.BUTTON_STYLE] = settings.buttonStyle.name
            prefs[Keys.SHOW_LINE_NUMBERS] = settings.showLineNumbers
            prefs[Keys.EDITOR_FONT_SIZE] = settings.editorFontSizeSp
            prefs[Keys.DARK_PLOT_BG] = settings.darkPlotBackground
            prefs[Keys.SHOW_GRID_LINES] = settings.showGridLines
            prefs[Keys.SHOW_DATA_POINTS] = settings.showDataPoints
            prefs[Keys.TRACE_STROKE_WIDTH] = settings.traceStrokeWidthDp
            prefs[Keys.DIALOG_CORNER_RADIUS] = settings.dialogCornerRadiusDp
        }
    }
}
