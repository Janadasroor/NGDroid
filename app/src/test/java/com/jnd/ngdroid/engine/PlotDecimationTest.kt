package com.jnd.ngdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotDecimationTest {

    @Test
    fun smallTrace_passesThroughUntouched() {
        val x = listOf(0.0, 1.0, 2.0)
        val y = listOf(3.0, Double.NaN, 5.0)
        val out = decimateXY(x, y)
        assertEquals(3, out.size)
        assertEquals(0.0 to 3.0, out[0])
        assertTrue(out[1].second.isNaN())
    }

    @Test
    fun largeTrace_cappedAtMaxPoints() {
        val n = 100_000
        val x = List(n) { it.toDouble() }
        val y = List(n) { kotlin.math.sin(it / 100.0) }
        val out = decimateXY(x, y)
        assertTrue("size=${out.size}", out.size <= MAX_DRAW_POINTS)
        assertTrue(out.size > MAX_DRAW_POINTS / 2)
    }

    @Test
    fun narrowSpike_survivesDecimation() {
        val n = 50_000
        val x = List(n) { it.toDouble() }
        val y = List(n) { if (it == 25_001) 999.0 else 0.0 }
        val out = decimateXY(x, y, maxPoints = 1000)
        assertEquals(999.0, out.maxOf { it.second }, 0.0)
    }

    @Test
    fun globalBounds_preserved() {
        val n = 20_000
        val x = List(n) { it.toDouble() }
        val y = List(n) { if (it % 2 == 0) -7.5 else 3.25 }
        val out = decimateXY(x, y)
        assertEquals(-7.5, out.minOf { it.second }, 0.0)
        assertEquals(3.25, out.maxOf { it.second }, 0.0)
    }

    @Test
    fun xStaysMonotonic() {
        val n = 30_000
        val x = List(n) { it.toDouble() }
        val y = List(n) { kotlin.math.sin(it / 50.0) * it }
        val out = decimateXY(x, y)
        for (i in 1 until out.size) {
            assertTrue("x[$i]=${out[i].first} < x[${i - 1}]=${out[i - 1].first}", out[i].first >= out[i - 1].first)
        }
    }

    @Test
    fun nonFinite_droppedOnLargeTrace() {
        val n = 10_000
        val x = List(n) { it.toDouble() }
        val y = List(n) { if (it % 100 == 0) Double.NaN else it.toDouble() }
        val out = decimateXY(x, y)
        assertTrue(out.all { it.first.isFinite() && it.second.isFinite() })
    }

    @Test
    fun empty_returnsEmpty() {
        assertTrue(decimateXY(emptyList(), emptyList()).isEmpty())
        assertTrue(decimateXY(listOf(1.0), emptyList()).isEmpty())
    }
}
