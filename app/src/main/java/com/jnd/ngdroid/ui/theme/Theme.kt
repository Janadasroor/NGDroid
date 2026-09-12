package com.jnd.ngdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Single app theme entry point. Accent-driven (from Settings) so there is
 * exactly one MaterialTheme in the hierarchy (no double-wrap with MainScreen).
 */
@Composable
fun NGDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = Purple40,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            primaryContainer = accent.copy(alpha = 0.2f),
            onPrimaryContainer = accent,
            secondary = PurpleGrey80,
            tertiary = Pink80
        )
    } else {
        lightColorScheme(
            primary = accent,
            primaryContainer = accent.copy(alpha = 0.15f),
            onPrimaryContainer = accent,
            secondary = PurpleGrey40,
            tertiary = Pink40
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
