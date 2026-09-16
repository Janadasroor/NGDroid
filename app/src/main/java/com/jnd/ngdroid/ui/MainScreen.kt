package com.jnd.ngdroid.ui

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jnd.ngdroid.data.ThemeMode
import com.jnd.ngdroid.ui.theme.LocalAppSizes
import com.jnd.ngdroid.ui.theme.LocalButtonShape
import com.jnd.ngdroid.ui.theme.LocalDialogShape
import com.jnd.ngdroid.ui.theme.buttonShapeFor
import com.jnd.ngdroid.ui.theme.dialogShapeFor
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

/**
 * Keeps a tab in composition while hidden: measures 0x0 (no layout/draw
 * cost) so every remember* state — scroll positions, lazy lists, text
 * fields, dialogs — survives tab switches while the app runs.
 */
private fun Modifier.keepComposed(visible: Boolean): Modifier = layout { measurable, constraints ->
    if (!visible) {
        layout(0, 0) {}
    } else {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
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
    // Tablets (sw600dp+) keep full chrome in landscape; phones go immersive:
    // top + bottom bars hidden, slim rail keeps every tab reachable.
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isPhoneLandscape = isLandscape && !isTablet

    // Back: any tab returns to EDITOR first; on EDITOR double-press exits.
    // Inner handlers (drawer, dialogs) consume first — this is the fallback.
    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    // Tab switch keeps every screen composed (scroll/focus/dialog state
    // preserved) and drops IME focus so a hidden field keeps no keyboard.
    val focusManager = LocalFocusManager.current
    val selectTab: (AppTab) -> Unit = { tab ->
        focusManager.clearFocus(force = true)
        selectedTab = tab
    }
    BackHandler(enabled = true) {
        if (selectedTab != AppTab.EDITOR) {
            selectTab(AppTab.EDITOR)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackPress = now
                Toast.makeText(context, "Press again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val primaryColor = Color(settings.accentColorTheme.hexValue)

    val tabs: List<Pair<AppTab, ImageVector>> = listOf(
        AppTab.EDITOR to Icons.Default.Code,
        AppTab.CONSOLE to Icons.Default.Terminal,
        AppTab.PLOT to Icons.AutoMirrored.Filled.ShowChart,
        AppTab.DATA to Icons.Default.TableChart,
        AppTab.ASSISTANT to Icons.Default.SmartToy,
        AppTab.SETTINGS to Icons.Default.Settings
    )
    // Landscape hides the bottom bar on phones; tablets keep it.
    // Fullscreen plot keeps maximum area (no rail either).
    val showRail = isPhoneLandscape && !isFullscreenPlot

    NGDroidTheme(darkTheme = isDark, accent = primaryColor) {
        CompositionLocalProvider(
            LocalButtonShape provides remember(settings.buttonStyle) {
                buttonShapeFor(settings.buttonStyle)
            },
            LocalDialogShape provides remember(settings.dialogCornerRadiusDp) {
                dialogShapeFor(settings.dialogCornerRadiusDp)
            }
        ) {
        // Chat tab owns its own header (drawer + model picker); the global bar would double it.
        // Phone landscape is fully immersive (no top bar either).
        val hideGlobalBar = isFullscreenPlot || selectedTab == AppTab.ASSISTANT || isPhoneLandscape
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
                // Phone landscape: no bottom bar (rail takes over).
                // Tablets keep it in landscape; fullscreen plot hides it everywhere.
                if (!isPhoneLandscape && !isFullscreenPlot) {
                    // Six destinations must fit 320dp+ screens: single-line
                    // labels at the bucket size, truncated instead of wrapping.
                    val navLabel = MaterialTheme.typography.labelMedium.copy(
                        fontSize = LocalAppSizes.current.navLabelSize
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
                                onClick = { selectTab(tab) },
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
            // Keyed so rotation (bar <-> rail) keeps screen state, not resets it.
            // Every tab stays composed (hidden ones measure 0x0), so scroll
            // positions and UI state survive tab switches while running.
            val tabContent: @Composable () -> Unit = {
                AppTab.entries.forEach { tab ->
                    key(tab) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .fillMaxSize()
                                .keepComposed(selectedTab == tab)
                        ) {
                            when (tab) {
                    AppTab.EDITOR -> NetlistEditorScreen(
                        viewModel = simulationViewModel,
                        settingsRepository = settingsRepository,
                        onNavigateToPlot = { selectTab(AppTab.PLOT) }
                    )
                    AppTab.CONSOLE -> ConsoleScreen(repository = repository)
                    AppTab.PLOT -> PlotScreen(
                        repository = repository,
                        settingsRepository = settingsRepository,
                        simulationViewModel = simulationViewModel
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
                        onSnapshot = { simulationViewModel.snapshotReport() },
                        onRunAndReport = { net, timeoutMs ->
                            simulationViewModel.runAndReport(net, timeoutMs)
                        },
                        onRenderPlot = { requested ->
                            simulationViewModel.renderPlotThumbnail(requested)
                        },
                        onCurrentNetlist = { simulationViewModel.currentNetlistText() },
                        onNavigateToEditor = { selectTab(AppTab.EDITOR) },
                        onNavigateToPlot = { selectTab(AppTab.PLOT) },
                        onNavigateToSettings = { selectTab(AppTab.SETTINGS) }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        settingsRepository = settingsRepository,
                        onSettingsChanged = { transform -> settingsViewModel.updateSettings(transform) },
                        assistant = assistantViewModel,
                        renderPreview = { com.jnd.ngdroid.ui.assistant.AssistantMarkdownWithMath(it) }
                    )
                            }
                        }
                    }
                }
            }
            // Rail + fullscreen bleed edge-to-edge so the side container
            // runs the full screen height (no sharp corners at top/bottom).
            Surface(
                modifier = if (showRail || isFullscreenPlot) Modifier.fillMaxSize()
                else Modifier.padding(innerPadding)
            ) {
                if (showRail) {
                    Row(Modifier.fillMaxSize()) {
                        key("rail") {
                            // Custom rail (not M3 NavigationRail): six items don't fit
                            // short landscape heights, so the column scrolls instead
                            // of squeezing/cutting the last button.
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .statusBarsPadding()
                                        .navigationBarsPadding()
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    tabs.forEach { (tab, icon) ->
                                        NavigationRailItem(
                                            selected = selectedTab == tab,
                                            onClick = { selectTab(tab) },
                                            icon = { Icon(icon, contentDescription = tab.title) },
                                            label = { Text(tab.title) },
                                            modifier = Modifier.width(80.dp)
                                        )
                                    }
                                }
                            }
                        }
                        key("content") {
                            // Content keeps the Scaffold insets (bars are hidden,
                            // system insets still apply) — only the rail bleeds.
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(innerPadding)
                            ) { tabContent() }
                        }
                    }
                } else {
                    key("content") { tabContent() }
                }
            }
        }
        }
    }
}
