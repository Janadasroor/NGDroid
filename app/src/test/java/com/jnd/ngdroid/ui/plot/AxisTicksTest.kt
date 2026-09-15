package com.jnd.ngdroid.ui.plot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AxisTicksTest {

    @Test
    fun linearTicksSpanRange() {
        val ticks = axisTicks(2.5e6, 250e6, 6, log = false)
        assertEquals(6, ticks.size)
        assertEquals(2.5e6, ticks.first(), 1.0)
        assertEquals(250e6, ticks.last(), 1.0)
        // Even spacing.
        val step = ticks[1] - ticks[0]
        for (i in 2 until ticks.size) {
            assertEquals(step, ticks[i] - ticks[i - 1], abs(step) * 1e-9)
        }
    }

    @Test
    fun logTicksAreDecades() {
        val ticks = axisTicks(1e6, 10e6, 3, log = true)
        assertEquals(3, ticks.size)
        assertEquals(1e6, ticks[0], 1.0)
        assertEquals(10e6, ticks[2], 1.0)
        // Geometric middle of a decade.
        assertEquals(3.16227766e6, ticks[1], 10.0)
    }

    @Test
    fun singleTickReturnsMin() {
        assertEquals(listOf(4.0), axisTicks(4.0, 9.0, 1, log = false))
    }

    @Test
    fun sparseCountStillSpansRange() {
        // Adaptive density may drop to 4 labels on narrow screens.
        val ticks = axisTicks(2.5e6, 250e6, 4, log = true)
        assertEquals(4, ticks.size)
        assertEquals(2.5e6, ticks.first(), 1.0)
        assertEquals(250e6, ticks.last(), 1.0)
    }

    @Test
    fun formatTickUsesEngNotation() {
        assertEquals("2.5 M", formatTick(2.5e6))
        assertEquals("33.2 n", formatTick(33.2e-9))
        assertEquals("250 M", formatTick(250e6))
        assertEquals("0", formatTick(0.0))
        assertEquals("—", formatTick(Double.NaN))
        assertEquals("—", formatTick(Double.POSITIVE_INFINITY))
    }

    @Test
    fun compactTickTrimsZerosButKeepsExponents() {
        assertEquals("2.5 M", compactTick("2.500 M"))
        assertEquals("250 M", compactTick("250.000 M"))
        assertEquals("0", compactTick("0.000"))
        assertEquals("5.000e+10", compactTick("5.000e+10"))
        assertEquals("-12.28 m", compactTick("-12.280 m"))
    }
}
