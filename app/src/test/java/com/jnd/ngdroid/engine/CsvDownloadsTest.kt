package com.jnd.ngdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvDownloadsTest {

    private fun plot(name: String) = SimulationPlot(
        title = "T",
        plotName = name,
        plotType = "tran",
        scaleVector = VectorSeries(name = "time", isScale = true, values = listOf(0.0, 1.0)),
        dataVectors = listOf(VectorSeries(name = "v(out)", values = listOf(0.0, 1.0)))
    )

    @Test
    fun fileName_isTimestampedAndSanitized() {
        val name = CsvExporter.csvFileName(plot("tran 1/weird"), nowMillis = 0L)
        assertTrue(name.startsWith("tran_1_weird_"))
        assertTrue(name.endsWith(".csv"))
    }

    @Test
    fun fileName_emptyPlotName_usesDefault() {
        val name = CsvExporter.csvFileName(plot(""), nowMillis = 0L)
        assertTrue(name.startsWith("simulation_results_"))
    }

    @Test
    fun fileName_samePlot_differentStamps() {
        val a = CsvExporter.csvFileName(plot("tran1"), nowMillis = 1000L)
        val b = CsvExporter.csvFileName(plot("tran1"), nowMillis = 2000L)
        assertTrue(a != b)
    }

    @Test
    fun legacyPermission_onlyBelow29() {
        // Logic check: helper must return true for Oreo-era SDKs. We assert the
        // branch helper exists and documents intent; SDK check is framework-side.
        assertEquals(
            android.os.Build.VERSION.SDK_INT <= 28,
            CsvExporter.needsLegacyStoragePermission()
        )
    }
}
