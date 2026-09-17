package com.jnd.ngdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class WaveformAnalyzerTest {

    @Test
    fun emptyValues_returnsZeroMeasurements() {
        val vec = VectorSeries(name = "v(out)", values = emptyList())
        val result = WaveformAnalyzer.calculateMeasurements(null, vec)

        assertEquals("v(out)", result.netName)
        assertEquals(0, result.count)
        assertEquals(0.0, result.minVal, 0.0)
        assertEquals(0.0, result.maxVal, 0.0)
        assertEquals(0.0, result.peakToPeak, 0.0)
        assertEquals(0.0, result.avgVal, 0.0)
        assertEquals(0.0, result.rmsVal, 0.0)
        assertNull(result.estimatedFreqHz)
        assertNull(result.estimatedPeriod)
    }

    @Test
    fun constantValues_minMaxAvgRmsPeakToPeak() {
        val vec = VectorSeries(name = "v(dc)", values = listOf(5.0, 5.0, 5.0, 5.0))
        val result = WaveformAnalyzer.calculateMeasurements(null, vec)

        assertEquals(4, result.count)
        assertEquals(5.0, result.minVal, 1e-9)
        assertEquals(5.0, result.maxVal, 1e-9)
        assertEquals(0.0, result.peakToPeak, 1e-9)
        assertEquals(5.0, result.avgVal, 1e-9)
        assertEquals(5.0, result.rmsVal, 1e-9)
    }

    @Test
    fun rampValues_minMaxAvgRmsPeakToPeak() {
        // 0, 1, 2, 3, 4
        val values = listOf(0.0, 1.0, 2.0, 3.0, 4.0)
        val vec = VectorSeries(name = "v(ramp)", values = values)
        val result = WaveformAnalyzer.calculateMeasurements(null, vec)

        assertEquals(0.0, result.minVal, 1e-9)
        assertEquals(4.0, result.maxVal, 1e-9)
        assertEquals(4.0, result.peakToPeak, 1e-9)
        assertEquals(2.0, result.avgVal, 1e-9)
        // rms = sqrt((0+1+4+9+16)/5) = sqrt(6)
        assertEquals(sqrt(6.0), result.rmsVal, 1e-9)
    }

    @Test
    fun sineWave_estimatesFrequencyAround100Hz() {
        // 100 Hz sine over 0..0.02 s (two full periods), fine step
        val freq = 100.0
        val step = 0.00005 // 50 us -> 400 samples over 20 ms
        val times = mutableListOf<Double>()
        val values = mutableListOf<Double>()
        var t = 0.0
        while (t <= 0.02 + 1e-12) {
            times.add(t)
            values.add(sin(2 * PI * freq * t))
            t += step
        }
        val scale = VectorSeries(name = "time", values = times)
        val data = VectorSeries(name = "v(out)", values = values)

        val result = WaveformAnalyzer.calculateMeasurements(scale, data)

        assertEquals(times.size, result.count)
        assertEquals(-1.0, result.minVal, 0.05)
        assertEquals(1.0, result.maxVal, 0.05)
        assertEquals(2.0, result.peakToPeak, 0.1)
        assertNotNull(result.estimatedFreqHz)
        assertNotNull(result.estimatedPeriod)
        assertEquals(100.0, result.estimatedFreqHz!!, 10.0)
        assertEquals(0.01, result.estimatedPeriod!!, 0.002)
    }

    @Test
    fun rangeWindow_limitsSamplesToCursorSpan() {
        // 0..10 s ramp 0..100: window [2,4] keeps ~1/5 of the samples,
        // min/max/avg describe the window, not the trace.
        val times = List(101) { it * 0.1 }
        val values = List(101) { it.toDouble() }
        val scale = VectorSeries(name = "time", values = times)
        val data = VectorSeries(name = "v(out)", values = values)

        val full = WaveformAnalyzer.calculateMeasurements(scale, data)
        assertEquals(101, full.count)
        assertEquals(50.0, full.avgVal, 1e-9)

        val win = WaveformAnalyzer.calculateRangeMeasurements(
            scale, data, 2.0, 4.0, rangeLabel = "Between cursors"
        )
        assertEquals(21, win.count)
        assertEquals(20.0, win.minVal, 1e-9)
        assertEquals(40.0, win.maxVal, 1e-9)
        assertEquals(30.0, win.avgVal, 1e-9)
        assertEquals("Between cursors", win.rangeLabel)
    }

    @Test
    fun rangeWindow_reversedCursorsStillWork() {
        val times = List(11) { it.toDouble() }
        val values = List(11) { it * 2.0 }
        val scale = VectorSeries(name = "time", values = times)
        val data = VectorSeries(name = "v(out)", values = values)
        // C2 left of C1: same window either way.
        val win = WaveformAnalyzer.calculateRangeMeasurements(scale, data, 8.0, 4.0)
        assertEquals(5, win.count)
        assertEquals(8.0, win.minVal, 1e-9)
        assertEquals(16.0, win.maxVal, 1e-9)
    }

    @Test
    fun rangeWindow_emptySpanYieldsZeroCount() {
        val times = List(11) { it.toDouble() }
        val values = List(11) { it.toDouble() }
        val scale = VectorSeries(name = "time", values = times)
        val data = VectorSeries(name = "v(out)", values = values)
        val win = WaveformAnalyzer.calculateRangeMeasurements(scale, data, 50.0, 60.0)
        assertEquals(0, win.count)
        assertEquals(0.0, win.rmsVal, 1e-12)
    }

    @Test
    fun rangeWindow_withoutScaleFallsBackToFullTrace() {
        val values = List(11) { it.toDouble() }
        val data = VectorSeries(name = "v(out)", values = values)
        val win = WaveformAnalyzer.calculateRangeMeasurements(null, data, 2.0, 4.0)
        assertEquals(11, win.count)
        assertEquals(5.0, win.avgVal, 1e-9)
    }
}
