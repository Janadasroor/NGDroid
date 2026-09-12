package com.jnd.ngdroid.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiceValidatorCopyTest {

    @Test
    fun validRcPasses() {
        val netlist = "* RC low-pass\nV1 in 0 AC 1 SIN(0 1 1k)\nR1 in out 1k\nC1 out 0 100n\n.tran 0.1m 10m\n.end"
        val result = SpiceValidator.validate(netlist)
        assertTrue(result.errors.joinToString(), result.isValid)
    }

    @Test
    fun missingEndFails() {
        val netlist = "* RC low-pass\nR1 in out 1k\nC1 out 0 100n"
        val result = SpiceValidator.validate(netlist)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains(".end") })
    }
}
