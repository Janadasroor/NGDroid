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
}
