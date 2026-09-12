package com.jnd.ngdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.max

class DualAxisPlotTest {

    @Test
    fun vectorSeries_identifiesCurrentsCorrectly() {
        val branchVec = VectorSeries(name = "v1#branch")
        val iInVec = VectorSeries(name = "i(in)")
        val iCapitalVec = VectorSeries(name = "I(R1)")
        val voltageVec = VectorSeries(name = "v(out)")
        val netVec = VectorSeries(name = "node1")

        assertTrue(branchVec.isCurrent)
        assertTrue(iInVec.isCurrent)
        assertTrue(iCapitalVec.isCurrent)
        assertFalse(voltageVec.isCurrent)
        assertFalse(netVec.isCurrent)
    }

    @Test
    fun logScale_conversionIsMonotonic() {
        val freq1 = 10.0
        val freq2 = 1000.0
        val freq3 = 100000.0

        val log1 = log10(max(1e-12, freq1))
        val log2 = log10(max(1e-12, freq2))
        val log3 = log10(max(1e-12, freq3))

        assertEquals(1.0, log1, 1e-6)
        assertEquals(3.0, log2, 1e-6)
        assertEquals(5.0, log3, 1e-6)

        assertEquals(log2 - log1, log3 - log2, 1e-6) // 10..1000 (2 decades) is same log distance as 1000..100k
    }
}
