package com.jnd.ngdroid.data

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AccentColorTheme(val displayName: String, val hexValue: Long) {
    CYAN("Cyber Cyan", 0xFF00E5FF),
    BLUE("Electric Blue", 0xFF2979FF),
    VIOLET("Deep Violet", 0xFFD500F9),
    EMERALD("Emerald Green", 0xFF00E676),
    AMBER("Solar Amber", 0xFFFFAB00)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColorTheme: AccentColorTheme = AccentColorTheme.CYAN,
    val editorFontSizeSp: Int = 14,
    val showLineNumbers: Boolean = true,
    val traceStrokeWidthDp: Float = 2.5f,
    val showGridLines: Boolean = true,
    val showDataPoints: Boolean = false,
    val darkPlotBackground: Boolean = true
)
