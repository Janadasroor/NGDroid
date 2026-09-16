package com.jnd.ngdroid.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSimulationReportTest {

    @Test
    fun parseDefaultsOnEmpty() {
        val (netlist, timeout) = parseRunSimulationArgs("{}")
        assertEquals(null, netlist)
        assertEquals(DEFAULT_RUN_TIMEOUT_MS, timeout)
    }

    @Test
    fun parseNetlistAndTimeout() {
        val (netlist, timeout) = parseRunSimulationArgs(
            """{"netlist":"* RC\n.end","timeout_ms":5000}"""
        )
        assertEquals("* RC\n.end", netlist)
        assertEquals(5000L, timeout)
    }

    @Test
    fun parseClampsTimeout() {
        val (_, low) = parseRunSimulationArgs("""{"timeout_ms":10}""")
        assertEquals(1_000L, low)
        val (_, high) = parseRunSimulationArgs("""{"timeout_ms":9999999}""")
        assertEquals(120_000L, high)
    }

    @Test
    fun parseBlankNetlistIsNull() {
        val (netlist, _) = parseRunSimulationArgs("""{"netlist":"  "}""")
        assertEquals(null, netlist)
    }

    @Test
    fun formatReportIncludesStatusLogsVectors() {
        val out = formatSimulationReport(
            statusText = "Simulation Complete",
            hasError = false,
            errorMessage = null,
            logs = listOf("--- Running Simulation ---", "done"),
            vectors = listOf("time: 200 pts min=0.0 max=0.0199 last=0.0199")
        )
        assertTrue("status: Simulation Complete" in out)
        assertTrue("- done" in out)
        assertTrue("vectors:" in out)
    }

    @Test
    fun formatReportMarksError() {
        val out = formatSimulationReport(
            statusText = "Simulation Failed",
            hasError = true,
            errorMessage = "singular matrix",
            logs = emptyList(),
            vectors = emptyList()
        )
        assertTrue("[ERROR]" in out)
        assertTrue("singular matrix" in out)
    }

    @Test
    fun runToolDelegatesNetlistAndTimeout() {
        var gotNetlist: String? = "unset"
        var gotTimeout = 0L
        val tool = RunSimulationTool { netlist, timeoutMs ->
            gotNetlist = netlist
            gotTimeout = timeoutMs
            "status: ok"
        }
        val out = runBlocking {
            tool.execute("""{"netlist":"* RC\n.end","timeout_ms":5000}""")
        }
        assertEquals("status: ok", out)
        assertEquals("* RC\n.end", gotNetlist)
        assertEquals(5000L, gotTimeout)
    }

    @Test
    fun runToolWrapsExceptions() {
        val tool = RunSimulationTool { _, _ -> throw RuntimeException("boom") }
        val out = runBlocking { tool.execute("{}") }
        assertTrue(out.startsWith("ERROR:"))
    }

    @Test
    fun downsampleCapsPoints() {
        val big = (0 until 1000).map { it.toDouble() }
        val down = downsampleSeries(big, 64)
        assertTrue(down.size <= 64)
        assertEquals(0.0, down.first(), 0.0)
        assertEquals(999.0, down.last(), 0.0)
    }

    @Test
    fun downsampleZeroReturnsEmpty() {
        assertEquals(emptyList<Double>(), downsampleSeries(listOf(1.0, 2.0), 0))
    }

    @Test
    fun downsampleKeepsSmall() {
        val small = listOf(1.0, 2.0, 3.0)
        assertEquals(small, downsampleSeries(small, 64))
    }

    @Test
    fun formatSampleIsCompact() {
        assertEquals("0", formatSample(0.0))
        assertEquals("NaN", formatSample(Double.NaN))
        assertEquals("Inf", formatSample(Double.POSITIVE_INFINITY))
        assertEquals("-Inf", formatSample(Double.NEGATIVE_INFINITY))
        assertEquals("1.5", formatSample(1.5))
        assertTrue(formatSample(2.5e6).length <= 8)
    }

    @Test
    fun reportIncludesSamples() {
        val out = formatSimulationReport(
            statusText = "Simulation Complete",
            hasError = false,
            errorMessage = null,
            logs = emptyList(),
            vectors = listOf("v(out): 200 pts min=0.0 max=5.0 last=4.9"),
            series = mapOf("v(out)" to listOf(0.0, 2.5, 5.0))
        )
        assertTrue("samples:" in out)
        assertTrue("- v(out): [0, 2.5, 5]" in out)
    }

    @Test
    fun reportOmitsSamplesWhenEmpty() {
        val out = formatSimulationReport(
            statusText = "Idle",
            hasError = false,
            errorMessage = null,
            logs = emptyList(),
            vectors = emptyList()
        )
        assertTrue("samples:" !in out)
    }

    @Test
    fun reportCapsTraces() {
        val series = (0 until 8).associate { "v$it" to listOf(1.0, 2.0) }
        val out = formatSimulationReport(
            statusText = "ok",
            hasError = false,
            errorMessage = null,
            logs = emptyList(),
            vectors = emptyList(),
            series = series,
            maxTraces = 6
        )
        assertTrue("+2 more traces omitted" in out)
    }
}
