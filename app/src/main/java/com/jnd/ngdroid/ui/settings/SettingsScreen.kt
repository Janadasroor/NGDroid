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
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.data.SettingsRepository
import com.jnd.ngdroid.data.ThemeMode
import java.util.Locale

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onSettingsChanged: (transform: (AppSettings) -> AppSettings) -> Unit = {},
    assistantViewModel: com.jnd.ngdroid.ui.assistant.AssistantViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
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
            Text(
                text = "Preferences & Styling",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Theme & Color Section
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
                    Text("App Theme & Color", style = MaterialTheme.typography.titleMedium)
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
            }
        }

        // Netlist Editor Settings
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
                    Text("Netlist Editor Options", style = MaterialTheme.typography.titleMedium)
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

        // Plot Canvas Styling
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
                    Text("Waveform Plot Customization", style = MaterialTheme.typography.titleMedium)
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

        AiAssistantSettingsCard(assistantViewModel = assistantViewModel)
    }
}
