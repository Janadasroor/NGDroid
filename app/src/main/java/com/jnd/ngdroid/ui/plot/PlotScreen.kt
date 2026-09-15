package com.jnd.ngdroid.ui.plot

import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.jnd.ngdroid.data.SettingsRepository
import com.jnd.ngdroid.domain.CalculateMeasurementsUseCase
import com.jnd.ngdroid.domain.ExportPlotUseCase
import com.jnd.ngdroid.engine.NetMeasurements
import com.jnd.ngdroid.engine.SimulationRepository
import com.jnd.ngdroid.ui.SimulationViewModel
import com.jnd.ngdroid.ui.plot.TraceColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlotScreen(
    repository: SimulationRepository,
    settingsRepository: SettingsRepository,
    simulationViewModel: SimulationViewModel
) {
    val state by repository.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val plot = state.currentPlot
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Interactive Zoom / Pan State
    var zoomScaleX by remember { mutableFloatStateOf(1.0f) }
    var zoomScaleY by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }
    var panOffsetY by remember { mutableFloatStateOf(0.0f) }

    // Cursors State
    var showCursors by remember { mutableStateOf(false) }
    var cursor1Frac by remember { mutableFloatStateOf(0.3f) }
    var cursor2Frac by remember { mutableFloatStateOf(0.7f) }

    // Collapsible Net Labels Panel (Landscape & Floating)
    var isNetsPanelExpanded by remember { mutableStateOf(false) }

    // Net Measurements Dialog
    var selectedMeasurements by remember { mutableStateOf<NetMeasurements?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // Math channel (fx): expression lives in the VM (survives tab switches);
    // the trace itself is evaluated lazily here — never stored.
    var showMathDialog by remember { mutableStateOf(false) }
    val mathExpr = simulationViewModel.mathExpr
    val mathVec = remember(plot, mathExpr) {
        evalMathExpr(mathExpr.orEmpty(), plot?.dataVectors.orEmpty())
    }
    val allData = remember(plot, mathVec) {
        val base = plot?.dataVectors.orEmpty()
        if (mathVec != null) base + mathVec else base
    }
    val combinedActive = remember(state.activeVectors, mathVec) {
        if (mathVec != null) state.activeVectors + mathVec.name else state.activeVectors
    }
    fun toggleMathDialog() {
        showMathDialog = true
    }
    val measureUseCase = remember { CalculateMeasurementsUseCase() }
    val exportUseCase = remember { ExportPlotUseCase() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun currentViewState() = com.jnd.ngdroid.engine.PlotExporter.PlotViewState(
        zoomScaleX = zoomScaleX,
        zoomScaleY = zoomScaleY,
        panOffsetX = panOffsetX,
        panOffsetY = panOffsetY,
        showCursors = showCursors,
        cursor1Frac = cursor1Frac,
        cursor2Frac = cursor2Frac,
    )

    // API 26-28: public Downloads saves need WRITE_EXTERNAL_STORAGE.
    // Queue the long-press save and run it after the user grants.
    var pendingPngSave by remember { mutableStateOf(false) }
    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (!granted) {
                pendingPngSave = false
                Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            if (!pendingPngSave) return@rememberLauncherForActivityResult
            pendingPngSave = false
            val cur = plot
            if (cur?.scaleVector?.values?.isNullOrEmpty() == true) return@rememberLauncherForActivityResult
            if (cur == null) return@rememberLauncherForActivityResult
            scope.launch {
                val uri = withContext(Dispatchers.IO) {
                    exportUseCase.savePng(
                        context, cur, combinedActive, settings, currentViewState()
                    )
                }
                Toast.makeText(
                    context,
                    if (uri != null) "PNG saved to Downloads" else "PNG export failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    fun savePngWithPermission() {
        val cur = plot ?: return
        if (cur.scaleVector?.values.isNullOrEmpty()) {
            Toast.makeText(context, "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT <= 28) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingPngSave = true
                storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                exportUseCase.savePng(
                    context, cur, combinedActive, settings, currentViewState()
                )
            }
            Toast.makeText(
                context,
                if (uri != null) "PNG saved to Downloads" else "PNG export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (selectedMeasurements != null) {
        NetMeasurementsDialog(
            measurements = selectedMeasurements!!,
            onDismiss = { selectedMeasurements = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isLandscape) 4.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 12.dp)
    ) {
        // Top Header & Controls (Hidden in Landscape to maximize space)
        if (!isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plot?.title?.ifEmpty { "Simulation Waveforms" } ?: "Simulation Waveforms",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.totalPointCount > 0) {
                        Text(
                            text = "${state.totalPointCount} data points",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showCursors = !showCursors }) {
                        Icon(
                            Icons.Default.CenterFocusWeak,
                            contentDescription = "Cursors",
                            tint = if (showCursors) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { toggleMathDialog() }) {
                        Icon(
                            Icons.Default.Functions,
                            contentDescription = "Math channel",
                            tint = if (showMathDialog || mathVec != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (zoomScaleX != 1.0f || zoomScaleY != 1.0f || panOffsetX != 0.0f || panOffsetY != 0.0f) {
                        IconButton(onClick = {
                            zoomScaleX = 1.0f
                            zoomScaleY = 1.0f
                            panOffsetX = 0.0f
                            panOffsetY = 0.0f
                        }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // PNG snapshot: tap to share, long-press to save to Downloads.
                    if (plot != null && plot.scaleVector?.values?.isNotEmpty() == true) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        exportUseCase.sharePng(
                                            context, plot, combinedActive, settings, currentViewState()
                                        )
                                    }
                                    if (!ok) {
                                        Toast.makeText(
                                            context, "Nothing to export yet", Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            exportUseCase.sharePng(
                                                context, plot, combinedActive, settings, currentViewState()
                                            )
                                        }
                                        if (!ok) {
                                            Toast.makeText(
                                                context, "Nothing to export yet", Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onLongClick = { savePngWithPermission() }
                            )
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Share waveform PNG (long-press to save)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.isSimulating) {
                        IconButton(onClick = { repository.haltSimulation() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { repository.haltSimulation() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                    } else if (state.isPaused) {
                        IconButton(onClick = { repository.resumeSimulation() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Math expression dialog: nets dropdown + free equation.
        if (showMathDialog) {
            MathExprDialog(
                initial = mathExpr,
                vectors = plot?.dataVectors.orEmpty(),
                onDismiss = { showMathDialog = false },
                onApply = {
                    simulationViewModel.mathExpr = it
                    showMathDialog = false
                }
            )
        }

        // Main Plot Canvas Container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = if (settings.darkPlotBackground) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (plot == null || plot.scaleVector == null || plot.scaleVector.values.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.hasError) "Simulation error occurred. Check Console logs." else "No plot data available. Run a simulation to view waveforms.",
                        color = if (state.hasError) MaterialTheme.colorScheme.error else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val scaleVec = plot.scaleVector

                Column(modifier = Modifier.fillMaxSize().padding(if (isLandscape) 4.dp else 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        WaveformCanvas(
                            scaleVector = scaleVec,
                            dataVectors = allData,
                            activeVectors = combinedActive,
                            settings = settings,
                            zoomScaleX = zoomScaleX,
                            zoomScaleY = zoomScaleY,
                            panOffsetX = panOffsetX,
                            panOffsetY = panOffsetY,
                            showCursors = showCursors,
                            cursor1Frac = cursor1Frac,
                            cursor2Frac = cursor2Frac,
                            onCursor1Move = { cursor1Frac = it },
                            onCursor2Move = { cursor2Frac = it },
                            onTransform = { zoomChange, panChange ->
                                zoomScaleX = (zoomScaleX * zoomChange).coerceIn(0.5f, 20.0f)
                                zoomScaleY = (zoomScaleY * zoomChange).coerceIn(0.5f, 20.0f)
                                panOffsetX += panChange.x
                                panOffsetY += panChange.y
                            },
                            modifier = Modifier.fillMaxSize(),
                            textMeasurer = textMeasurer
                        )

                        NetsOverlay(
                            dataVectors = allData,
                            activeVectors = combinedActive,
                            isLandscape = isLandscape,
                            isNetsPanelExpanded = isNetsPanelExpanded,
                            onToggleExpanded = { isNetsPanelExpanded = !isNetsPanelExpanded },
                            onToggleVector = {
                                if (it == mathVec?.name) simulationViewModel.clearMath()
                                else repository.toggleVectorActive(it)
                            },
                            onMeasureVector = { vec ->
                                selectedMeasurements = measureUseCase(plot.scaleVector, vec)
                            }
                        )

                        // Floating Landscape Quick Controls (Top-Right)
                        if (isLandscape) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { showCursors = !showCursors }) {
                                    Icon(
                                        Icons.Default.CenterFocusWeak,
                                        contentDescription = "Cursors",
                                        tint = if (showCursors) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                                IconButton(onClick = { toggleMathDialog() }) {
                                    Icon(
                                        Icons.Default.Functions,
                                        contentDescription = "Math channel",
                                        tint = if (showMathDialog || mathVec != null) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                                IconButton(onClick = {
                                    zoomScaleX = 1.0f
                                    zoomScaleY = 1.0f
                                    panOffsetX = 0.0f
                                    panOffsetY = 0.0f
                                }) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = Color.White)
                                }
                            }
                        }
                    }

                    // Cursor Measurement Readout Bar
                    if (showCursors && scaleVec.values.isNotEmpty()) {
                        CursorReadoutBar(
                            scaleVector = scaleVec,
                            cursor1Frac = cursor1Frac,
                            cursor2Frac = cursor2Frac
                        )
                    }

                    // Legend (Portrait)
                    if (!isLandscape) {
                        PlotLegend(
                            scaleName = scaleVec.name,
                            dataVectors = allData,
                            activeVectors = combinedActive,
                            darkPlotBackground = settings.darkPlotBackground
                        )
                    }
                }
            }
        }
    }
}
