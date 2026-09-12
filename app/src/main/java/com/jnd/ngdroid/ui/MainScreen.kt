package com.jnd.ngdroid.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jnd.ngdroid.data.ThemeMode
import com.jnd.ngdroid.ui.theme.LocalAppSizes
import com.jnd.ngdroid.ui.theme.LocalButtonShape
import com.jnd.ngdroid.ui.theme.buttonShapeFor
import com.jnd.ngdroid.ui.console.ConsoleScreen
import com.jnd.ngdroid.ui.data.DataScreen
import com.jnd.ngdroid.ui.editor.NetlistEditorScreen
import com.jnd.ngdroid.ui.plot.PlotScreen
import com.jnd.ngdroid.ui.settings.SettingsScreen
import com.jnd.ngdroid.ui.theme.NGDroidTheme

enum class AppTab(val title: String) {
    EDITOR("Netlist"),
    CONSOLE("Console"),
    PLOT("Plot"),
    DATA("Data"),
    ASSISTANT("Assistant"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    simulationViewModel: SimulationViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    assistantViewModel: com.jnd.ngdroid.ui.assistant.AssistantViewModel = viewModel()
) {
    val repository = simulationViewModel.repository
    val settingsRepository = settingsViewModel.settingsRepository

    val settings by settingsViewModel.settings.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.EDITOR) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isFullscreenPlot = selectedTab == AppTab.PLOT && isLandscape

    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val primaryColor = Color(settings.accentColorTheme.hexValue)

    NGDroidTheme(darkTheme = isDark, accent = primaryColor) {
        CompositionLocalProvider(
            LocalButtonShape provides remember(settings.buttonStyle) {
                buttonShapeFor(settings.buttonStyle)
            }
        ) {
        // Chat tab owns its own header (drawer + model picker); the global bar would double it.
        val hideGlobalBar = isFullscreenPlot || selectedTab == AppTab.ASSISTANT
        Scaffold(
            topBar = {
                if (!hideGlobalBar) {
                    TopAppBar(
                        title = { Text("NGDroid — SPICE Simulator") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            },
            bottomBar = {
                if (!isFullscreenPlot) {
                    // Six destinations must fit 320dp+ screens: single-line
                    // labels at the bucket size, truncated instead of wrapping.
                    val navLabel = MaterialTheme.typography.labelMedium.copy(
                        fontSize = LocalAppSizes.current.navLabelSize
                    )
                    val tabs = listOf(
                        AppTab.EDITOR to Icons.Default.Code,
                        AppTab.CONSOLE to Icons.Default.Terminal,
                        AppTab.PLOT to Icons.AutoMirrored.Filled.ShowChart,
                        AppTab.DATA to Icons.Default.TableChart,
                        AppTab.ASSISTANT to Icons.Default.SmartToy,
                        AppTab.SETTINGS to Icons.Default.Settings
                    )
                    // Accent edge above the bar + themed container (no default look).
                    Column {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        tabs.forEach { (tab, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(icon, contentDescription = tab.title) },
                                label = {
                                    Text(
                                        tab.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = navLabel
                                    )
                                }
                            )
                        }
                    }
                    }
                }
            }
        ) { innerPadding ->
            Surface(modifier = Modifier.padding(if (isFullscreenPlot) PaddingValues() else innerPadding)) {
                when (selectedTab) {
                    AppTab.EDITOR -> NetlistEditorScreen(
                        viewModel = simulationViewModel,
                        settingsRepository = settingsRepository,
                        onNavigateToPlot = { selectedTab = AppTab.PLOT }
                    )
                    AppTab.CONSOLE -> ConsoleScreen(repository = repository)
                    AppTab.PLOT -> PlotScreen(
                        repository = repository,
                        settingsRepository = settingsRepository
                    )
                    AppTab.DATA -> DataScreen(
                        viewModel = simulationViewModel,
                        settingsRepository = settingsRepository
                    )
                    AppTab.ASSISTANT -> com.jnd.ngdroid.ui.assistant.AssistantScreen(
                        assistantViewModel = assistantViewModel,
                        onApplyNetlist = { simulationViewModel.updateNetlist(it) },
                        onApplyAndRun = { net ->
                            if (net.isNotEmpty()) simulationViewModel.updateNetlist(net)
                            simulationViewModel.runSimulation()
                        },
                        onNavigateToEditor = { selectedTab = AppTab.EDITOR },
                        onNavigateToPlot = { selectedTab = AppTab.PLOT },
                        onNavigateToSettings = { selectedTab = AppTab.SETTINGS }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        settingsRepository = settingsRepository,
                        onSettingsChanged = { transform -> settingsViewModel.updateSettings(transform) },
                        assistantViewModel = assistantViewModel
                    )
                }
            }
        }
        }
    }
}
