package com.jnd.ngdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
    val secondary = remember(accent) { secondaryFor(accent) }
    val tertiary = remember(accent) { tertiaryFor(accent) }
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            primaryContainer = accent.copy(alpha = 0.2f),
            onPrimaryContainer = accent,
            secondary = secondary,
            secondaryContainer = secondary.copy(alpha = 0.2f),
            tertiary = tertiary,
            tertiaryContainer = tertiary.copy(alpha = 0.2f)
        )
    } else {
        lightColorScheme(
            primary = accent,
            primaryContainer = accent.copy(alpha = 0.15f),
            onPrimaryContainer = accent,
            secondary = secondary,
            secondaryContainer = secondary.copy(alpha = 0.18f),
            tertiary = tertiary,
            tertiaryContainer = tertiary.copy(alpha = 0.18f)
        )
    }

    val bucket = rememberWidthBucket()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = remember(bucket) { appTypography(bucket) },
        content = {
            CompositionLocalProvider(
                LocalAppSizes provides remember(bucket) { appSizesFor(bucket) }
            ) {
                content()
            }
        }
    )
}
