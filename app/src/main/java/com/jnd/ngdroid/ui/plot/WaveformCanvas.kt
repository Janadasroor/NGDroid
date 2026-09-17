/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid.ui.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
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
    modifier: Modifier = Modifier,
    textMeasurer: TextMeasurer? = null,
    /** Reports graph width px so the readout can invert cursor fractions to data. */
    onGraphWidth: (Float) -> Unit = {},
    /** The one measured trace: only its cursor dots draw. */
    measuredTraceName: String? = null
) {
    val density = LocalDensity.current
    // Tap places the nearer cursor (pinch/pan stays on the transform block;
    // a tap never pans, so the two gestures don't fight).
    val tapModifier = if (showCursors) {
        Modifier.pointerInput(density, dataVectors, activeVectors, cursor1Frac, cursor2Frac) {
            detectTapGestures(onTap = { offset ->
                val geo = plotGeometry(
                    density,
                    isDualAxis(dataVectors, activeVectors),
                    size.width.toFloat(),
                    size.height.toFloat()
                )
                if (geo.graphWidth <= 0) return@detectTapGestures
                val frac = tapToFrac(offset.x, geo.paddingLeft, geo.graphWidth)
                if (nearestCursor(frac, cursor1Frac, cursor2Frac) == 1) {
                    onCursor1Move(frac)
                } else {
                    onCursor2Move(frac)
                }
            })
        }
    } else {
        Modifier
    }
    Canvas(
        modifier = modifier
            .onSizeChanged {
                val geo = plotGeometry(
                    density,
                    isDualAxis(dataVectors, activeVectors),
                    it.width.toFloat(),
                    it.height.toFloat()
                )
                if (geo.graphWidth > 0) onGraphWidth(geo.graphWidth)
            }
            .then(tapModifier)
            .pointerInput(Unit) {
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
            cursor2Frac = cursor2Frac,
            measuredTraceName = measuredTraceName,
            textMeasurer = textMeasurer
        )
    }
}
