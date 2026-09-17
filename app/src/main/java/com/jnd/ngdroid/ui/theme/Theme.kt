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
