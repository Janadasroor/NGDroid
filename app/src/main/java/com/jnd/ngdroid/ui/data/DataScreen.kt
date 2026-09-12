package com.jnd.ngdroid.ui.data

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnd.ngdroid.domain.ExportCsvUseCase
import com.jnd.ngdroid.domain.ExportPlotUseCase
import com.jnd.ngdroid.engine.SimulationPlot
import com.jnd.ngdroid.ui.SimulationViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DataScreen(
    viewModel: SimulationViewModel,
    settingsRepository: com.jnd.ngdroid.data.SettingsRepository,
    exportCsvUseCase: ExportCsvUseCase = ExportCsvUseCase(),
    exportPlotUseCase: ExportPlotUseCase = ExportPlotUseCase()
) {
    val repository = viewModel.repository
    val state by repository.state.collectAsState()
    val history = state.plotHistory
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val settings by settingsRepository.settings.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var selectedPlotOverride by remember { mutableStateOf<SimulationPlot?>(null) }
    var isHistoryExpanded by remember { mutableStateOf(false) }

    // Legacy storage permission launcher (API 26-28 only, for Downloads save).
    var pendingDownloadPlot by remember { mutableStateOf<SimulationPlot?>(null) }
    var pendingDownloadIsReport by remember { mutableStateOf(false) }
    val storagePermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            val plot = pendingDownloadPlot
            val isReport = pendingDownloadIsReport
            pendingDownloadPlot = null
            pendingDownloadIsReport = false
            if (granted && plot != null) {
                if (isReport) {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val uri = exportPlotUseCase.saveReport(
                            context, plot, state.activeVectors, settings,
                            repository.netlistText.value,
                            viewModel.netlistRepository.library.value.firstOrNull {
                                it.id == viewModel.netlistRepository.activeId.value
                            }?.title.orEmpty()
                        )
                        Toast.makeText(
                            context,
                            if (uri != null) "Report saved to Downloads" else "Report failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val uri = exportCsvUseCase.saveToDownloads(context, plot)
                    Toast.makeText(
                        context,
                        if (uri != null) "Saved to Downloads" else "Save failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (!granted) {
                Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun savePlot(plot: SimulationPlot) {
        if (exportCsvUseCase.needsLegacyStoragePermission()) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingDownloadPlot = plot
                storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        val uri = exportCsvUseCase.saveToDownloads(context, plot)
        Toast.makeText(
            context,
            if (uri != null) "Saved to Downloads" else "Save failed",
            Toast.LENGTH_SHORT
        ).show()
    }

    val activePlot = selectedPlotOverride ?: state.currentPlot ?: history.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row with Title, Copy CSV, and Share CSV Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.TableChart,
                    contentDescription = "Data Table",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "CSV Results Table",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (activePlot != null && activePlot.scaleVector != null && activePlot.scaleVector.values.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Copy CSV
                    IconButton(onClick = {
                        val csv = exportCsvUseCase.generateCsv(activePlot)
                        clipboardManager.setText(AnnotatedString(csv))
                        Toast.makeText(context, "CSV copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy CSV",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Save to Downloads Button (Oreo-friendly)
                    IconButton(onClick = { savePlot(activePlot) }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Save CSV to Downloads",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Share CSV Button
                    Button(
                        onClick = { exportCsvUseCase.shareCsv(context, activePlot) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share CSV")
                    }
                }

                // Lab report row: share PDF or save to Downloads (same Oreo permission path).
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val ok = exportPlotUseCase.shareReport(
                                    context,
                                    activePlot,
                                    state.activeVectors,
                                    settings,
                                    repository.netlistText.value,
                                    viewModel.netlistRepository.library.value.firstOrNull {
                                        it.id == viewModel.netlistRepository.activeId.value
                                    }?.title.orEmpty()
                                )
                                if (!ok) {
                                    android.widget.Toast.makeText(
                                        context, "Report needs plot data", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share lab report PDF",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Report PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            if (exportCsvUseCase.needsLegacyStoragePermission()) {
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    pendingDownloadPlot = activePlot
                                    pendingDownloadIsReport = true
                                    storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    return@OutlinedButton
                                }
                            }
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val uri = exportPlotUseCase.saveReport(
                                    context,
                                    activePlot,
                                    state.activeVectors,
                                    settings,
                                    repository.netlistText.value,
                                    viewModel.netlistRepository.library.value.firstOrNull {
                                        it.id == viewModel.netlistRepository.activeId.value
                                    }?.title.orEmpty()
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    if (uri != null) "Report saved to Downloads" else "Report failed",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Save lab report PDF to Downloads",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save PDF")
                    }
                }
                }
            }
        }

        // COLLAPSIBLE HISTORY SELECTOR DROPDOWN
        if (history.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isHistoryExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "History",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Previous CSV Runs (${history.size}): ${activePlot?.title?.ifEmpty { "Plot" } ?: "Select"}",
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Expand"
                    )
                }

                DropdownMenu(
                    expanded = isHistoryExpanded,
                    onDismissRequest = { isHistoryExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    history.forEachIndexed { index, histPlot ->
                        val isSelected = histPlot == activePlot
                        val pointCount = histPlot.scaleVector?.values?.size ?: 0

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = histPlot.title.ifEmpty { "Simulation #${index + 1}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Plot: ${histPlot.plotName} | Type: ${histPlot.plotType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "$pointCount pts",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                selectedPlotOverride = histPlot
                                isHistoryExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Table Content Container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (activePlot == null || activePlot.scaleVector == null || activePlot.scaleVector.values.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No simulation data available. Run a simulation to view CSV table.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val scaleVec = activePlot.scaleVector
                val activeDataVecs = activePlot.dataVectors
                val rowCount = scaleVec.values.size
                // Single shared ScrollState so header and all rows scroll together.
                val tableScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // Scrollable Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tableScrollState)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    ) {
                        TableCell(text = scaleVec.name, isHeader = true)
                        activeDataVecs.forEach { vec ->
                            TableCell(text = vec.name, isHeader = true)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed((0 until rowCount).toList(), key = { _, rowIndex -> rowIndex }) { index, rowIndex ->
                            val isEven = index % 2 == 0
                            val bgColor = if (isEven) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(tableScrollState)
                                    .background(bgColor, RoundedCornerShape(4.dp))
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            ) {
                                val xVal = scaleVec.values[rowIndex]
                                TableCell(text = String.format(Locale.US, "%.5e", xVal))

                                activeDataVecs.forEach { vec ->
                                    val yVal = if (rowIndex < vec.values.size) vec.values[rowIndex] else 0.0
                                    TableCell(text = String.format(Locale.US, "%.5e", yVal))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, isHeader: Boolean = false) {
    Box(
        modifier = Modifier
            .width(130.dp)
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = if (isHeader) 13.sp else 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = if (isHeader) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}
