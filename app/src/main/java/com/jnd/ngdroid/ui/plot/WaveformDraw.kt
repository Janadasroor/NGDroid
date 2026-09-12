package com.jnd.ngdroid.ui.plot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.engine.VectorSeries
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Shared waveform renderer used by both the on-screen [WaveformCanvas]
 * and offscreen PNG/PDF export. Supports Dual Y-Axes (Voltage vs Current)
 * and Logarithmic Frequency X-Axis for AC Bode plots.
 */
fun DrawScope.drawWaveform(
    scaleVector: VectorSeries,
    dataVectors: List<VectorSeries>,
    activeVectors: Set<String>,
    settings: AppSettings,
    zoomScaleX: Float = 1f,
    zoomScaleY: Float = 1f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    showCursors: Boolean = false,
    cursor1Frac: Float = 0.3f,
    cursor2Frac: Float = 0.7f
) {
    val width = size.width
    val height = size.height

    val activeDataVecs = dataVectors.filter { activeVectors.contains(it.name) && it.values.isNotEmpty() }
    val hasCurrentVecs = activeDataVecs.any { it.isCurrent }
    val hasVoltageVecs = activeDataVecs.any { !it.isCurrent }
    val isDualAxis = hasCurrentVecs && hasVoltageVecs

    val paddingLeft = 40.dp.toPx()
    val paddingRight = if (isDualAxis) 40.dp.toPx() else 10.dp.toPx()
    val paddingBottom = 30.dp.toPx()
    val paddingTop = 10.dp.toPx()

    val graphWidth = width - paddingLeft - paddingRight
    val graphHeight = height - paddingTop - paddingBottom

    if (graphWidth <= 0 || graphHeight <= 0) return

    val isLogX = scaleVector.name.equals("frequency", ignoreCase = true)

    val xMin = scaleVector.values.minOrNull() ?: 0.0
    val xMax = scaleVector.values.maxOrNull() ?: 1.0

    val xMinLog = log10(max(1e-12, xMin))
    val xMaxLog = log10(max(1e-12, xMax))
    val xRangeLog = if (xMaxLog > xMinLog) (xMaxLog - xMinLog) / zoomScaleX else 1.0
    val xRangeLinear = if (xMax > xMin) (xMax - xMin) / zoomScaleX else 1.0

    // Compute Left Y-Axis bounds (Voltages / Default)
    var yMinLeft = Double.MAX_VALUE
    var yMaxLeft = -Double.MAX_VALUE
    activeDataVecs.filter { !it.isCurrent || !isDualAxis }.forEach { vec ->
        vec.values.forEach { v ->
            if (v < yMinLeft) yMinLeft = v
            if (v > yMaxLeft) yMaxLeft = v
        }
    }
    if (yMinLeft == Double.MAX_VALUE) yMinLeft = -1.0
    if (yMaxLeft == -Double.MAX_VALUE) yMaxLeft = 1.0
    if (yMaxLeft == yMinLeft) { yMaxLeft += 1.0; yMinLeft -= 1.0 }
    val yRangeLeft = if (yMaxLeft > yMinLeft) (yMaxLeft - yMinLeft) / zoomScaleY else 1.0

    // Compute Right Y-Axis bounds (Currents) if dual axis
    var yMinRight = Double.MAX_VALUE
    var yMaxRight = -Double.MAX_VALUE
    if (isDualAxis) {
        activeDataVecs.filter { it.isCurrent }.forEach { vec ->
            vec.values.forEach { v ->
                if (v < yMinRight) yMinRight = v
                if (v > yMaxRight) yMaxRight = v
            }
        }
        if (yMinRight == Double.MAX_VALUE) yMinRight = -1.0
        if (yMaxRight == -Double.MAX_VALUE) yMaxRight = 1.0
        if (yMaxRight == yMinRight) { yMaxRight += 1.0; yMinRight -= 1.0 }
    }
    val yRangeRight = if (yMaxRight > yMinRight) (yMaxRight - yMinRight) / zoomScaleY else 1.0

    // Grid lines
    if (settings.showGridLines) {
        val gridColor = if (settings.darkPlotBackground) Color(0xFF2C2C2C) else Color(0xFFD0D0D0)
        val gridRows = 5
        val gridCols = 5

        for (i in 0..gridRows) {
            val y = paddingTop + (graphHeight / gridRows) * i
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(paddingLeft + graphWidth, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        for (i in 0..gridCols) {
            val x = paddingLeft + (graphWidth / gridCols) * i
            drawLine(
                color = gridColor,
                start = Offset(x, paddingTop),
                end = Offset(x, paddingTop + graphHeight),
                strokeWidth = 1.dp.toPx()
            )
        }
    }

    // Axis lines (Left Y-Axis & X-Axis)
    val axisColor = if (settings.darkPlotBackground) Color.Gray else Color.DarkGray
    drawLine(
        color = axisColor,
        start = Offset(paddingLeft, paddingTop),
        end = Offset(paddingLeft, paddingTop + graphHeight),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = axisColor,
        start = Offset(paddingLeft, paddingTop + graphHeight),
        end = Offset(paddingLeft + graphWidth, paddingTop + graphHeight),
        strokeWidth = 2.dp.toPx()
    )

    // Right Y-Axis line if dual axis
    if (isDualAxis) {
        drawLine(
            color = axisColor,
            start = Offset(paddingLeft + graphWidth, paddingTop),
            end = Offset(paddingLeft + graphWidth, paddingTop + graphHeight),
            strokeWidth = 2.dp.toPx()
        )
    }

    // Draw Traces
    dataVectors.forEachIndexed { vecIdx, vec ->
        if (activeVectors.contains(vec.name) && vec.values.isNotEmpty()) {
            val path = Path()
            val color = TraceColors[vecIdx % TraceColors.size]

            val useRightAxis = isDualAxis && vec.isCurrent
            val currentYMin = if (useRightAxis) yMinRight else yMinLeft
            val currentYRange = if (useRightAxis) yRangeRight else yRangeLeft

            val count = min(scaleVector.values.size, vec.values.size)
            val step = max(1, count / 1500)

            var isFirst = true
            var i = 0
            while (i < count) {
                val xVal = scaleVector.values[i]
                val yVal = vec.values[i]

                val px = if (isLogX) {
                    val logVal = log10(max(1e-12, xVal))
                    paddingLeft + ((logVal - xMinLog) / xRangeLog * graphWidth).toFloat() + panOffsetX
                } else {
                    paddingLeft + ((xVal - xMin) / xRangeLinear * graphWidth).toFloat() + panOffsetX
                }

                val py = paddingTop + graphHeight - ((yVal - currentYMin) / currentYRange * graphHeight).toFloat() + panOffsetY

                if (px in (paddingLeft - 10f)..(paddingLeft + graphWidth + 10f)) {
                    if (isFirst) {
                        path.moveTo(px, py)
                        isFirst = false
                    } else {
                        path.lineTo(px, py)
                    }

                    if (settings.showDataPoints && count < 500) {
                        drawCircle(
                            color = color,
                            radius = settings.traceStrokeWidthDp.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                i += step
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = settings.traceStrokeWidthDp.dp.toPx())
            )
        }
    }

    // Cursors
    if (showCursors) {
        val c1Px = paddingLeft + cursor1Frac * graphWidth
        val c2Px = paddingLeft + cursor2Frac * graphWidth

        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(c1Px, paddingTop),
            end = Offset(c1Px, paddingTop + graphHeight),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 6.dp.toPx(),
            center = Offset(c1Px, paddingTop + graphHeight / 2)
        )

        drawLine(
            color = Color(0xFFFF9100),
            start = Offset(c2Px, paddingTop),
            end = Offset(c2Px, paddingTop + graphHeight),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = Color(0xFFFF9100),
            radius = 6.dp.toPx(),
            center = Offset(c2Px, paddingTop + graphHeight / 2)
        )
    }
}
