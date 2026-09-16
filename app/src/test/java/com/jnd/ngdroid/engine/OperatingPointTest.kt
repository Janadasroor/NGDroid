package com.jnd.ngdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatingPointTest {

    private val sampleLogs = listOf(
        "--- Running Simulation ---",
        "stdout Initial Transient Solution",
        "stdout --------------------------",
        "stdout Node                                   Voltage",
        "stdout ----                                   -------",
        "stdout in                                           0",
        "stdout ground                                       0",
        "stdout l                                            0",
        "stdout c                                            0",
        "stdout l1#branch                                    0",
        "stdout v1#branch                                    0",
        "stdout  Reference value :  0.00000e+00",
        "stdout No. of Data Rows : 108"
    )

    @Test
    fun parsesNodeTable() {
        val op = parseOperatingPoint(sampleLogs)!!
        assertEquals(6, op.rows.size)
        assertEquals("in", op.rows[0].name)
        assertEquals(0.0, op.rows[0].value, 0.0)
        assertTrue(!op.rows[0].isCurrent)
        assertEquals("l1#branch", op.rows[4].name)
        assertTrue(op.rows[4].isCurrent)
        assertTrue(op.rows[5].isCurrent)
    }

    @Test
    fun parsesScientificValues() {
        val logs = listOf(
            "Node Voltage",
            "---- -------",
            "out -3.04823e-05",
            "in 5"
        )
        val op = parseOperatingPoint(logs)!!
        assertEquals(-3.04823e-05, op.rows[0].value, 1e-12)
        assertEquals(5.0, op.rows[1].value, 0.0)
    }

    @Test
    fun lastTableWins() {
        val logs = sampleLogs + listOf(
            "stdout Node Voltage",
            "stdout ---- -------",
            "stdout out 1.5"
        )
        val op = parseOperatingPoint(logs)!!
        assertEquals(1, op.rows.size)
        assertEquals("out", op.rows[0].name)
    }

    @Test
    fun noTableReturnsNull() {
        assertNull(parseOperatingPoint(listOf("hello", "No. of Data Rows : 5")))
        assertNull(parseOperatingPoint(emptyList()))
        // Header without rows is not a table.
        assertNull(parseOperatingPoint(listOf("Node Voltage", "---- -------", "Reference value : 0")))
    }

    @Test
    fun stripsTagVariants() {
        val logs = listOf(
            "[stdout] Node Voltage",
            "STDERR: ---- -------",
            "stdout\tout 1.5"
        )
        val op = parseOperatingPoint(logs)!!
        assertEquals(1, op.rows.size)
        assertEquals("out", op.rows[0].name)
    }

    @Test
    fun parsesSourceCurrentTable() {
        val logs = listOf(
            "Node Voltage",
            "---- -------",
            "out 1.5",
            "Source Current",
            "---- -------",
            "v1 -1.2e-03"
        )
        val op = parseOperatingPoint(logs)!!
        assertEquals(2, op.rows.size)
        assertTrue(!op.rows[0].isCurrent)
        assertEquals("v1", op.rows[1].name)
        assertTrue(op.rows[1].isCurrent)
        assertEquals(-1.2e-03, op.rows[1].value, 1e-12)
    }
}
