package com.jnd.ngdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun emptyScaleVector_returnsEmptyString() {
        val plot = SimulationPlot(
            title = "Empty",
            plotName = "empty",
            scaleVector = null,
            dataVectors = listOf(VectorSeries("v(out)", values = listOf(1.0)))
        )
        assertEquals("", CsvExporter.generateCsvString(plot))
    }

    @Test
    fun headerAndRowCount_matchVectors() {
        val scale = VectorSeries(name = "time", values = listOf(0.0, 0.001, 0.002))
        val v1 = VectorSeries(name = "v(in)", values = listOf(0.0, 1.0, 2.0))
        val v2 = VectorSeries(name = "v(out)", values = listOf(0.0, 0.5, 1.0))
        val plot = SimulationPlot(
            title = "Tran",
            plotName = "tran",
            scaleVector = scale,
            dataVectors = listOf(v1, v2)
        )

        val csv = CsvExporter.generateCsvString(plot)
        val lines = csv.trim().lines()

        // header + 3 rows
        assertEquals(4, lines.size)
        assertEquals("\"time\",\"v(in)\",\"v(out)\"", lines[0])
        // Each data row has 3 columns
        for (i in 1..3) {
            assertEquals(3, lines[i].split(",").size)
        }
        assertTrue(lines[1].startsWith("0.00000000e+00"))
    }

    @Test
    fun missingDataValues_paddedWithZero() {
        val scale = VectorSeries(name = "time", values = listOf(0.0, 0.001))
        val short = VectorSeries(name = "v(out)", values = listOf(1.0))
        val plot = SimulationPlot(
            title = "Tran",
            plotName = "tran",
            scaleVector = scale,
            dataVectors = listOf(short)
        )

        val csv = CsvExporter.generateCsvString(plot)
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[2].endsWith("0.00000000e+00"))
    }
}
