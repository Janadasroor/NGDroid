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

package com.jnd.ngdroid.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jnd.ngdroid.data.AccentColorTheme
import com.jnd.ngdroid.data.ButtonStyle
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.data.SettingsRepository
import com.jnd.ngdroid.data.ThemeMode
import java.util.Locale

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onSettingsChanged: (transform: (AppSettings) -> AppSettings) -> Unit = {},
    assistant: AssistantSettingsFacade,
    renderPreview: SkillPreviewRenderer
) {
    val settings by settingsRepository.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Preferences & Styling",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tuned for Android 8+; changes apply instantly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("App Theme & Color", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onSettingsChanged {
                                it.copy(
                                    themeMode = ThemeMode.SYSTEM,
                                    accentColorTheme = AccentColorTheme.CYAN,
                                    buttonStyle = ButtonStyle.ROUNDED,
                                    dialogCornerRadiusDp = 28
                                )
                            }
                        }
                    ) { Text("Reset") }
                }

                HorizontalDivider()

                Text("Theme Mode", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "SYSTEM follows the OS on Android 10+; on Android 8–9 it uses light.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = {
                                onSettingsChanged { it.copy(themeMode = mode) }
                            },
                            label = { Text(mode.name) }
                        )
                    }
                }

                Text("Accent Color", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccentColorTheme.entries.forEach { accent ->
                        val isSelected = settings.accentColorTheme == accent
                        val color = Color(accent.hexValue)

                        IconButton(
                            onClick = {
                                onSettingsChanged { it.copy(accentColorTheme = accent) }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = "${accent.displayName} accent color"
                                    role = Role.RadioButton
                                    selected = isSelected
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Text("Button Style", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Applies to action buttons across the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ButtonStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.buttonStyle == style,
                            onClick = {
                                onSettingsChanged { it.copy(buttonStyle = style) }
                            },
                            label = { Text(style.displayName) }
                        )
                    }
                }

                Text("Dialog Corners (${settings.dialogCornerRadiusDp} dp)")
                Text(
                    text = "Applies to popup dialogs; their buttons follow Button Style.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Square / Round")
                    Slider(
                        value = settings.dialogCornerRadiusDp.toFloat(),
                        onValueChange = { radius ->
                            onSettingsChanged { it.copy(dialogCornerRadiusDp = radius.toInt()) }
                        },
                        valueRange = 0f..28f,
                        steps = 27,
                        modifier = Modifier
                            .width(160.dp)
                            .semantics {
                                contentDescription = "Dialog corner radius ${settings.dialogCornerRadiusDp} dp"
                            }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Netlist Editor Options", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onSettingsChanged { it.copy(editorFontSizeSp = 14, showLineNumbers = true) }
                        }
                    ) { Text("Reset") }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editor Font Size (${settings.editorFontSizeSp} sp)")
                    Slider(
                        value = settings.editorFontSizeSp.toFloat(),
                        onValueChange = { fontSize ->
                            onSettingsChanged { it.copy(editorFontSizeSp = fontSize.toInt()) }
                        },
                        valueRange = 12f..22f,
                        steps = 7,
                        modifier = Modifier
                            .width(160.dp)
                            .semantics {
                                contentDescription = "Editor font size ${settings.editorFontSizeSp} sp"
                            }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Line Numbers")
                    Switch(
                        checked = settings.showLineNumbers,
                        onCheckedChange = { show ->
                            onSettingsChanged { it.copy(showLineNumbers = show) }
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Waveform Plot Customization", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onSettingsChanged {
                                it.copy(
                                    traceStrokeWidthDp = 2.5f,
                                    showGridLines = true,
                                    showDataPoints = false,
                                    darkPlotBackground = true
                                )
                            }
                        }
                    ) { Text("Reset") }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trace Line Thickness (${String.format(Locale.US, "%.1f", settings.traceStrokeWidthDp)} dp)")
                    Slider(
                        value = settings.traceStrokeWidthDp,
                        onValueChange = { strokeWidth ->
                            onSettingsChanged { it.copy(traceStrokeWidthDp = strokeWidth) }
                        },
                        valueRange = 1.0f..6.0f,
                        steps = 9,
                        modifier = Modifier
                            .width(160.dp)
                            .semantics {
                                contentDescription = "Trace line thickness ${String.format(Locale.US, "%.1f", settings.traceStrokeWidthDp)} dp"
                            }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Grid Lines")
                    Switch(
                        checked = settings.showGridLines,
                        onCheckedChange = { show ->
                            onSettingsChanged { it.copy(showGridLines = show) }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Data Point Dots")
                    Switch(
                        checked = settings.showDataPoints,
                        onCheckedChange = { show ->
                            onSettingsChanged { it.copy(showDataPoints = show) }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark / OLED Plot Background")
                    Switch(
                        checked = settings.darkPlotBackground,
                        onCheckedChange = { dark ->
                            onSettingsChanged { it.copy(darkPlotBackground = dark) }
                        }
                    )
                }
            }
        }

        AiAssistantSettingsCard(assistant = assistant, renderPreview = renderPreview)
    }
}
