package com.jnd.ngdroid.ui.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.engine.VectorSeries

val TraceColors = listOf(
    Color(0xFF00E5FF), // Bright Cyan
    Color(0xFFFF9100), // Orange
    Color(0xFF76FF03), // Lime Green
    Color(0xFFFF4081), // Pink
    Color(0xFFFFEA00), // Yellow
    Color(0xFFD500F9)  // Purple
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun WaveformCanvas(
    scaleVector: VectorSeries,
    dataVectors: List<VectorSeries>,
    activeVectors: Set<String>,
    settings: AppSettings,
    zoomScaleX: Float,
    zoomScaleY: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    showCursors: Boolean,
    cursor1Frac: Float,
    cursor2Frac: Float,
    onCursor1Move: (Float) -> Unit,
    onCursor2Move: (Float) -> Unit,
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                onTransform(zoom, pan)
            }
        }
    ) {
        drawWaveform(
            scaleVector = scaleVector,
            dataVectors = dataVectors,
            activeVectors = activeVectors,
            settings = settings,
            zoomScaleX = zoomScaleX,
            zoomScaleY = zoomScaleY,
            panOffsetX = panOffsetX,
            panOffsetY = panOffsetY,
            showCursors = showCursors,
            cursor1Frac = cursor1Frac,
            cursor2Frac = cursor2Frac
        )
    }
}
