package com.jnd.ngdroid.ui.plot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import kotlin.math.max
import kotlin.math.pow

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
    modifier: Modifier = Modifier
) {
    if (dataVectors.isEmpty()) return
    if (isLandscape) {
        // LANDSCAPE MODE: Single Collapsible Button "Nets"
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
                                        onLongClick = { onMeasureVector(vec) }
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
        // PORTRAIT MODE: Floating Net Labels Bar directly over Canvas Top
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
                            onLongClick = { onMeasureVector(vec) }
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
    modifier: Modifier = Modifier
) {
    if (scaleVector.values.isEmpty()) return
    val xMin = scaleVector.values.first()
    val xMax = scaleVector.values.last()
    val isLogX = scaleVector.name.equals("frequency", ignoreCase = true)
    val unitStr = if (isLogX) "Hz" else "s"

    val x1 = if (isLogX) {
        val logMin = kotlin.math.log10(max(1e-12, xMin))
        val logMax = kotlin.math.log10(max(1e-12, xMax))
        10.0.pow(logMin + cursor1Frac * (logMax - logMin))
    } else {
        xMin + cursor1Frac * (xMax - xMin)
    }

    val x2 = if (isLogX) {
        val logMin = kotlin.math.log10(max(1e-12, xMin))
        val logMax = kotlin.math.log10(max(1e-12, xMax))
        10.0.pow(logMin + cursor2Frac * (logMax - logMin))
    } else {
        xMin + cursor2Frac * (xMax - xMin)
    }

    val dx = abs(x2 - x1)
    val freqHz = if (isLogX) 0.0 else (if (dx > 0) 1.0 / dx else 0.0)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
    }
}

@Composable
fun PlotLegend(
    scaleName: String,
    dataVectors: List<VectorSeries>,
    activeVectors: Set<String>,
    darkPlotBackground: Boolean,
    modifier: Modifier = Modifier
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
