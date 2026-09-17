package com.jnd.ngdroid.ui.plot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnd.ngdroid.engine.VectorSeries
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun BoxScope.NetsOverlay(
    dataVectors: List<VectorSeries>,
    activeVectors: Set<String>,
    isLandscape: Boolean,
    isNetsPanelExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleVector: (String) -> Unit,
    onMeasureVector: (VectorSeries) -> Unit,
    /** Double-tap a net chip: measure it with the cursors. */
    onDoubleClickVector: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (dataVectors.isEmpty()) return
    if (isLandscape) {
        Column(
            modifier = modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            OutlinedButton(
                onClick = onToggleExpanded,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = "Nets",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Nets (${activeVectors.size})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (isNetsPanelExpanded) {
                ElevatedCard(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(220.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color.Black.copy(alpha = 0.90f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dataVectors.forEachIndexed { index, vec ->
                            val color = TraceColors[index % TraceColors.size]
                            val isSelected = activeVectors.contains(vec.name)

                            Surface(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = { onToggleVector(vec.name) },
                                        onLongClick = { onMeasureVector(vec) },
                                        onDoubleClick = { onDoubleClickVector(vec.name) }
                                    ),
                                shape = CircleShape,
                                color = if (isSelected) color.copy(alpha = 0.3f) else Color.DarkGray.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(color, CircleShape)
                                    )
                                    Text(
                                        text = vec.name,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            dataVectors.forEachIndexed { index, vec ->
                val color = TraceColors[index % TraceColors.size]
                val isSelected = activeVectors.contains(vec.name)

                Surface(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onToggleVector(vec.name) },
                            onLongClick = { onMeasureVector(vec) },
                            onDoubleClick = { onDoubleClickVector(vec.name) }
                        ),
                    shape = CircleShape,
                    color = if (isSelected) color.copy(alpha = 0.3f) else Color.DarkGray.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, CircleShape)
                        )
                        Text(
                            text = vec.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CursorReadoutBar(
    scaleVector: VectorSeries,
    cursor1Frac: Float,
    cursor2Frac: Float,
    modifier: Modifier = Modifier,
    dataVectors: List<VectorSeries> = emptyList(),
    activeVectors: Set<String> = emptySet(),
    zoomScaleX: Float = 1f,
    panOffsetX: Float = 0f,
    graphWidthPx: Float = 0f,
    /** The one measured signal; null = prompt to pick. */
    measuredTraceName: String? = null,
    /** Opens the signal picker (also tapped from the Y row). */
    onPickSignal: () -> Unit = {}
) {
    if (scaleVector.values.isEmpty()) return
    val isLogX = scaleVector.name.equals("frequency", ignoreCase = true)
    val unitStr = if (isLogX) "Hz" else "s"

    val (x1, x2) = cursorDataRange(
        scaleVector.values, isLogX, cursor1Frac, cursor2Frac,
        zoomScaleX, panOffsetX, graphWidthPx
    ) ?: return

    val dx = abs(x2 - x1)
    val freqHz = if (isLogX) 0.0 else (if (dx > 0) 1.0 / dx else 0.0)

    // The measured signal only: name + Y under each cursor + delta.
    data class MeasuredY(val name: String, val color: Color, val y1: Double?, val y2: Double?, val unit: String)
    val measured: MeasuredY? = dataVectors.mapIndexedNotNull { idx, vec ->
        if (vec.name != measuredTraceName || !activeVectors.contains(vec.name)) null
        else MeasuredY(
            name = vec.name,
            color = TraceColors[idx % TraceColors.size],
            y1 = interpolateYAt(x1, scaleVector.values, vec.values),
            y2 = interpolateYAt(x2, scaleVector.values, vec.values),
            unit = if (vec.isCurrent) "A" else "V"
        )
    }.firstOrNull()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "C1: ${formatEng(x1, unitStr)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "C2: ${formatEng(x2, unitStr)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF9100)
                )
                Text(
                    text = "ΔX: ${formatEng(dx, unitStr)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!isLogX) {
                    Text(
                        text = "Freq: ${formatEng(freqHz, "Hz")}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF76FF03)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickSignal)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (measured == null) {
                    Text(
                        text = "Tap to pick a signal to measure",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(measured.color, CircleShape)
                    )
                    val yText = if (measured.y1 == null || measured.y2 == null) {
                        "${measured.name}: —"
                    } else {
                        val dy = measured.y2 - measured.y1
                        val sign = if (dy < 0) "−" else "+"
                        "${measured.name}: ${formatEng(measured.y1, measured.unit)} → " +
                            "${formatEng(measured.y2, measured.unit)} " +
                            "(Δ$sign${formatEng(abs(dy), measured.unit)})"
                    }
                    Text(
                        text = yText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Signal picker: one measured trace for the cursors, from active signals. */
@Composable
fun SignalPickerDialog(
    dataVectors: List<VectorSeries>,
    activeVectors: Set<String>,
    selectedName: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Per-row stats: full RMS/avg dialog without leaving the picker. */
    onStats: (String) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Measure signal") },
        text = {
            val rows = dataVectors.mapIndexedNotNull { idx, vec ->
                if (!activeVectors.contains(vec.name)) null
                else Triple(vec.name, TraceColors[idx % TraceColors.size], if (vec.isCurrent) "A" else "V")
            }
            if (rows.isEmpty()) {
                Text(
                    "No active signals — enable traces first.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(rows, key = { it.first }) { (name, color, unit) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(name) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color, CircleShape)
                            )
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                unit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (name == selectedName) {
                                Text(
                                    "✓",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onStats(name) }) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Stats for $name",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlotLegend(
    scaleName: String,
    dataVectors: List<VectorSeries>,
    activeVectors: Set<String>,
    darkPlotBackground: Boolean,
    modifier: Modifier = Modifier,
    /** Double-tap a net name: measure it with the cursors. */
    onDoubleClickTrace: (String) -> Unit = {},
    /** Long-press a net name: full RMS/avg stats dialog. */
    onLongClickTrace: (VectorSeries) -> Unit = {}
) {
    val activeDataVecs = dataVectors.filter { activeVectors.contains(it.name) }
    val hasCurrentVecs = activeDataVecs.any { it.isCurrent }
    val hasVoltageVecs = activeDataVecs.any { !it.isCurrent }
    val isDualAxis = hasCurrentVecs && hasVoltageVecs

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "X: $scaleName",
            style = MaterialTheme.typography.labelSmall,
            color = if (darkPlotBackground) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
        )

        dataVectors.forEachIndexed { idx, vec ->
            if (activeVectors.contains(vec.name)) {
                val axisTag = if (isDualAxis) (if (vec.isCurrent) " [A]" else " [V]") else ""
                Row(
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onDoubleClick = { onDoubleClickTrace(vec.name) },
                        onLongClick = { onLongClickTrace(vec) }
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TraceColors[idx % TraceColors.size], CircleShape)
                    )
                    Text(
                        text = "${vec.name}$axisTag",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (darkPlotBackground) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
