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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CursorMeasureTest {

    private val xs = listOf(0.0, 1.0, 2.0, 3.0, 4.0)
    private val ys = listOf(0.0, 10.0, 20.0, 30.0, 40.0)

    @Test
    fun interpolateExactSamples() {
        assertEquals(20.0, interpolateYAt(2.0, xs, ys)!!, 1e-12)
    }

    @Test
    fun interpolateMidpoints() {
        assertEquals(15.0, interpolateYAt(1.5, xs, ys)!!, 1e-12)
        assertEquals(5.0, interpolateYAt(0.5, xs, ys)!!, 1e-12)
    }

    @Test
    fun interpolateClampsOutsideRange() {
        assertEquals(0.0, interpolateYAt(-5.0, xs, ys)!!, 1e-12)
        assertEquals(40.0, interpolateYAt(99.0, xs, ys)!!, 1e-12)
    }

    @Test
    fun interpolateSkipsNonFiniteBracket() {
        val bad = listOf(0.0, Double.NaN, 20.0, 30.0, 40.0)
        // Inside the NaN gap: nearest finite end wins.
        assertEquals(0.0, interpolateYAt(0.9, xs, bad)!!, 1e-12)
        assertEquals(20.0, interpolateYAt(1.1, xs, bad)!!, 1e-12)
    }

    @Test
    fun interpolateRejectsBadInput() {
        assertNull(interpolateYAt(1.0, emptyList(), emptyList()))
        assertNull(interpolateYAt(1.0, xs, listOf(1.0, 2.0)))
        assertNull(interpolateYAt(Double.NaN, xs, ys))
        assertNull(interpolateYAt(1.0, xs, List(5) { Double.NaN }))
    }

    @Test
    fun cursorDataXInvertsTraceMapping() {
        // Linear: screen px for x=2.5 of [0,4] at zoom 1, no pan.
        val xMin = 0.0
        val xRange = 4.0
        val w = 400f
        val frac = (((2.5 - xMin) / xRange) * w / w).toFloat()
        assertEquals(2.5, cursorDataX(frac, xMin, xRange, 0.0, 1.0, 0f, w, false), 1e-6)
    }

    @Test
    fun cursorDataXAccountsZoomAndPan() {
        // Zoom 2x (range halved), panned +50px on a 400px graph.
        val xMin = 0.0
        val xRange = 2.0 // (4-0)/2
        val w = 400f
        val pan = 50f
        // Data x=1.0 draws at: ((1-0)/2)*400 + 50 = 250px -> frac 0.625.
        val back = cursorDataX(0.625f, xMin, xRange, 0.0, 1.0, pan, w, false)
        assertEquals(1.0, back, 1e-6)
    }

    @Test
    fun cursorDataXLogRoundTrips() {
        // 10Hz..10kHz log axis: frac 0.5 -> ~316Hz.
        val lo = kotlin.math.log10(10.0)
        val range = kotlin.math.log10(10000.0) - lo
        val x = cursorDataX(0.5f, 10.0, 9990.0, lo, range, 0f, 400f, true)
        assertTrue(abs(x - 316.227) < 0.5)
    }

    @Test
    fun cursorDataXFallbackWithoutViewport() {
        assertEquals(2.0, cursorDataX(0.5f, 0.0, 4.0, 0.0, 1.0, 0f, 0f, false), 1e-9)
    }
}
