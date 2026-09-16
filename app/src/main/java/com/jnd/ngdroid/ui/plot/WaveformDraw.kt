package com.jnd.ngdroid.ui.plot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.engine.VectorSeries
import com.jnd.ngdroid.engine.decimateXY
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Evenly spaced tick values across the visible [effMin, effMax] range
 * (log=true spaces in log10, for the frequency axis). Pure; JVM-testable.
 */
fun axisTicks(effMin: Double, effMax: Double, count: Int, log: Boolean): List<Double> {
    if (count <= 1) return listOf(effMin)
    return List(count) { i ->
        val f = i.toDouble() / (count - 1)
        if (log) {
            val lo = log10(max(1e-12, effMin))
            val hi = log10(max(1e-12, effMax))
            10.0.pow(lo + f * (hi - lo))
        } else {
            effMin + f * (effMax - effMin)
        }
    }
}

/** Compact engineering-notation tick label (no unit). Pure; JVM-testable. */
fun formatTick(v: Double): String {
    if (!v.isFinite()) return "—"
    return compactTick(formatEng(v, "").trim())
}

/** "2.500 M" -> "2.5 M", "250.000 M" -> "250 M" (never touches exponents). Pure. */
fun compactTick(s: String): String {
    val parts = s.split(" ")
    if (parts.isEmpty()) return s
    var mant = parts[0]
    if ('.' in mant && 'e' !in mant && 'E' !in mant) {
        mant = mant.trimEnd('0').trimEnd('.')
    }
    return (listOf(mant) + parts.drop(1)).joinToString(" ")
}

/** True when voltage and current traces share the plot (right Y axis). Pure. */
fun isDualAxis(dataVectors: List<VectorSeries>, activeVectors: Set<String>): Boolean {
    val active = dataVectors.filter { activeVectors.contains(it.name) && it.values.isNotEmpty() }
    return active.any { it.isCurrent } && active.any { !it.isCurrent }
}

/** Graph paddings + plot area in pixels. Shared by the renderer and tap mapping. Pure. */
data class PlotGeometry(
    val paddingLeft: Float,
    val paddingTop: Float,
    val paddingRight: Float,
    val paddingBottom: Float,
    val graphWidth: Float,
    val graphHeight: Float
)

fun plotGeometry(density: Density, dualAxis: Boolean, canvasWidth: Float, canvasHeight: Float): PlotGeometry {
    val paddingLeft = with(density) { 58.dp.toPx() }
    val paddingRight = with(density) { (if (dualAxis) 56.dp else 12.dp).toPx() }
    val paddingBottom = with(density) { 46.dp.toPx() }
    val paddingTop = with(density) { 10.dp.toPx() }
    return PlotGeometry(
        paddingLeft, paddingTop, paddingRight, paddingBottom,
        graphWidth = canvasWidth - paddingLeft - paddingRight,
        graphHeight = canvasHeight - paddingTop - paddingBottom
    )
}

/** X fraction (0..1 across the graph) for a tap at [xPx]. Pure; JVM-testable. */
fun tapToFrac(xPx: Float, paddingLeft: Float, graphWidth: Float): Float =
    ((xPx - paddingLeft) / graphWidth).coerceIn(0f, 1f)

/** 1 = move C1, 2 = move C2: whichever cursor is nearer the tap. Pure. */
fun nearestCursor(frac: Float, cursor1Frac: Float, cursor2Frac: Float): Int =
    if (abs(frac - cursor1Frac) <= abs(frac - cursor2Frac)) 1 else 2

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
    cursor2Frac: Float = 0.7f,
    /** Null = skip axis tick labels (tests / label-less callers). */
    textMeasurer: TextMeasurer? = null
) {
    val width = size.width
    val height = size.height

    val activeDataVecs = dataVectors.filter { activeVectors.contains(it.name) && it.values.isNotEmpty() }
    val isDualAxis = isDualAxis(dataVectors, activeVectors)

    val geo = plotGeometry(this, isDualAxis, width, height)
    val paddingLeft = geo.paddingLeft
    val paddingRight = geo.paddingRight
    val paddingBottom = geo.paddingBottom
    val paddingTop = geo.paddingTop

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

    // Min-max decimated once per frame: bounds + path both run on the
    // <=1500-point envelope instead of O(N) full scans per trace, and
    // narrow spikes survive (plain i+=step stride aliases them away).
    // Small traces pass through untouched.
    val decimated = activeDataVecs.associate { vec ->
        vec.name to decimateXY(scaleVector.values, vec.values)
    }

    // Compute Left Y-Axis bounds (Voltages / Default) on the decimated
    // envelope — exact, since bucketing preserves global min/max.
    var yMinLeft = Double.MAX_VALUE
    var yMaxLeft = -Double.MAX_VALUE
    activeDataVecs.filter { !it.isCurrent || !isDualAxis }.forEach { vec ->
        decimated[vec.name]?.forEach { (_, v) ->
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
            decimated[vec.name]?.forEach { (_, v) ->
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

    // Axis tick labels: visible data range (zoom/pan aware) mapped back
    // onto the fixed grid lines, so labels always match what is drawn.
    textMeasurer?.let { tm ->
        val labelStyle = TextStyle(
            color = if (settings.darkPlotBackground) Color(0xFFBDBDBD) else Color(0xFF424242),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        val tickIntervals = 5
        val tickCount = tickIntervals + 1

        // Visible X range at the graph edges (invert the trace mapping).
        val visXMin = if (isLogX) {
            10.0.pow(xMinLog + ((-panOffsetX) / graphWidth) * xRangeLog)
        } else {
            xMin + ((-panOffsetX) / graphWidth) * xRangeLinear
        }
        val visXMax = if (isLogX) {
            10.0.pow(xMinLog + ((graphWidth - panOffsetX) / graphWidth) * xRangeLog)
        } else {
            xMin + ((graphWidth - panOffsetX) / graphWidth) * xRangeLinear
        }
        val xTicksFull = axisTicks(visXMin, visXMax, tickCount, isLogX)
        // Adaptive X density: shrink the tick count until the widest
        // label fits its slot (portrait fits ~4, landscape ~6).
        val widestTick = xTicksFull.maxOf { tm.measure(formatTick(it), labelStyle).size.width }
        val xIntervals = ((graphWidth + 8.dp.toPx()) / (widestTick + 16.dp.toPx()))
            .toInt().coerceIn(1, tickIntervals)
        val xTickCount = xIntervals + 1
        val xTicks = axisTicks(visXMin, visXMax, xTickCount, isLogX)
        for (i in 0..xIntervals) {
            val frac = i / xIntervals.toFloat()
            val label = formatTick(xTicks[i])
            val layout = tm.measure(label, labelStyle)
            val lx = (paddingLeft + graphWidth * frac - layout.size.width / 2)
                .coerceIn(0f, width - layout.size.width)
            drawText(
                tm, label,
                topLeft = Offset(lx, paddingTop + graphHeight + 6.dp.toPx()),
                style = labelStyle
            )
        }

        // Visible Y range (top = max). Row i uses the descending tick.
        val visYTopLeft = yMinLeft + ((graphHeight + panOffsetY) / graphHeight) * yRangeLeft
        val visYBotLeft = yMinLeft + ((panOffsetY) / graphHeight) * yRangeLeft
        val yTicksLeft = axisTicks(visYBotLeft, visYTopLeft, tickCount, false)
        for (i in 0..tickIntervals) {
            val frac = i / tickIntervals.toFloat()
            val label = formatTick(yTicksLeft[tickIntervals - i])
            val layout = tm.measure(label, labelStyle)
            val lx = (paddingLeft - 5.dp.toPx() - layout.size.width).coerceAtLeast(0f)
            drawText(
                tm, label,
                topLeft = Offset(lx, paddingTop + graphHeight * frac - layout.size.height / 2),
                style = labelStyle
            )
        }

        if (isDualAxis) {
            val visYTopRight = yMinRight + ((graphHeight + panOffsetY) / graphHeight) * yRangeRight
            val visYBotRight = yMinRight + ((panOffsetY) / graphHeight) * yRangeRight
            val yTicksRight = axisTicks(visYBotRight, visYTopRight, tickCount, false)
            for (i in 0..tickIntervals) {
                val frac = i / tickIntervals.toFloat()
                val label = formatTick(yTicksRight[tickIntervals - i])
                val layout = tm.measure(label, labelStyle)
                val lx = (paddingLeft + graphWidth + 5.dp.toPx())
                    .coerceAtMost(width - layout.size.width)
                drawText(
                    tm, label,
                    topLeft = Offset(lx, paddingTop + graphHeight * frac - layout.size.height / 2),
                    style = labelStyle
                )
            }
        }
    }

    // Draw Traces
    dataVectors.forEachIndexed { vecIdx, vec ->
        if (activeVectors.contains(vec.name) && vec.values.isNotEmpty()) {
            val path = Path()
            val color = TraceColors[vecIdx % TraceColors.size]

            val useRightAxis = isDualAxis && vec.isCurrent
            val currentYMin = if (useRightAxis) yMinRight else yMinLeft
            val currentYRange = if (useRightAxis) yRangeRight else yRangeLeft

            val pts = decimated[vec.name]
                ?: decimateXY(scaleVector.values, vec.values)
            val count = pts.size

            var isFirst = true
            for ((xVal, yVal) in pts) {

                // Non-finite samples (e.g. A/B divide-by-zero): break the
                // path so the next valid sample starts a fresh segment.
                // (Only reachable on small pass-through traces — decimation
                // drops non-finite samples for large ones.)
                if (!xVal.isFinite() || !yVal.isFinite()) {
                    isFirst = true
                    continue
                }

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
