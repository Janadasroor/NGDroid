/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotExporterTest {

    private fun plot(name: String) = SimulationPlot(
        title = "RC",
        plotName = name,
        plotType = "tran",
        scaleVector = VectorSeries(name = "time", isScale = true, values = listOf(0.0, 0.5, 1.0)),
        dataVectors = listOf(VectorSeries(name = "v(out)", values = listOf(0.0, 0.5, 1.0)))
    )

    @Test
    fun pngFileName_isTimestamped() {
        val name = PlotExporter.pngFileName(plot("tran 1/a"), nowMillis = 0L)
        assertTrue(name.startsWith("tran_1_a_"))
        assertTrue(name.endsWith(".png"))
    }

    @Test
    fun pdfFileName_defaultsWhenEmpty() {
        val name = PlotExporter.pdfFileName(plot(""), nowMillis = 0L)
        assertTrue(name.startsWith("report_"))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun render_emptyScale_returnsNull() {
        val empty = plot("t").copy(scaleVector = VectorSeries(name = "time", isScale = true))
        assertEquals(null, PlotExporter.renderPlotBitmap(empty, setOf("v(out)"), testSettings()))
    }

    private fun testSettings(): com.jnd.ngdroid.data.AppSettings {
        return com.jnd.ngdroid.data.AppSettings()
    }
}
