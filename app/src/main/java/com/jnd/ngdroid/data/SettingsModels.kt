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
    val darkPlotBackground: Boolean = true
)
