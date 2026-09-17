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

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AccentColorTheme(val displayName: String, val hexValue: Long) {
    CYAN("Cyber Cyan", 0xFF00E5FF),
    BLUE("Electric Blue", 0xFF2979FF),
    VIOLET("Deep Violet", 0xFFD500F9),
    EMERALD("Emerald Green", 0xFF00E676),
    AMBER("Solar Amber", 0xFFFFAB00),
    CRIMSON("Crimson Red", 0xFFFF5252),
    LIME("Lime Pop", 0xFFB2FF59)
}

enum class ButtonStyle(val displayName: String, val cornerDp: Int) {
    ROUNDED("Rounded", 12),
    PILL("Pill", 24),
    SQUARE("Square", 4)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColorTheme: AccentColorTheme = AccentColorTheme.CYAN,
    val buttonStyle: ButtonStyle = ButtonStyle.ROUNDED,
    val editorFontSizeSp: Int = 14,
    val showLineNumbers: Boolean = true,
    val traceStrokeWidthDp: Float = 2.5f,
    val showGridLines: Boolean = true,
    val showDataPoints: Boolean = false,
    val darkPlotBackground: Boolean = true,
    /** Dialog container corner radius in dp (0 = square, 28 = Material default). */
    val dialogCornerRadiusDp: Int = 28
)
