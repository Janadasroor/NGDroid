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

package com.jnd.ngdroid.domain

import android.content.Context
import android.net.Uri
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.engine.NativeNgSpice
import com.jnd.ngdroid.engine.PlotExporter
import com.jnd.ngdroid.engine.SimulationPlot
import java.io.File

class ExportPlotUseCase {

    fun render(
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        viewState: PlotExporter.PlotViewState = PlotExporter.PlotViewState()
    ): android.graphics.Bitmap? {
        return PlotExporter.renderPlotBitmap(plot, activeVectors, settings, viewState)
    }

    fun sharePng(
        context: Context,
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        viewState: PlotExporter.PlotViewState = PlotExporter.PlotViewState()
    ): Boolean {
        val bmp = render(plot, activeVectors, settings, viewState) ?: return false
        PlotExporter.sharePng(context, plot, bmp)
        return true
    }

    fun savePng(
        context: Context,
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        viewState: PlotExporter.PlotViewState = PlotExporter.PlotViewState()
    ): Uri? {
        val bmp = render(plot, activeVectors, settings, viewState) ?: return null
        return PlotExporter.savePngToDownloads(context, plot, bmp)
    }

    fun shareReport(
        context: Context,
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        netlistText: String,
        circuitTitle: String,
        viewState: PlotExporter.PlotViewState = PlotExporter.PlotViewState()
    ): Boolean {
        val file = buildReport(context, plot, activeVectors, settings, netlistText, circuitTitle, viewState)
            ?: return false
        PlotExporter.sharePdf(context, file, plot.title.ifEmpty { plot.plotName })
        return true
    }

    fun saveReport(
        context: Context,
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        netlistText: String,
        circuitTitle: String,
        viewState: PlotExporter.PlotViewState = PlotExporter.PlotViewState()
    ): Uri? {
        val file = buildReport(context, plot, activeVectors, settings, netlistText, circuitTitle, viewState)
            ?: return null
        return PlotExporter.savePdfToDownloads(context, file)
    }

    fun buildReport(
        context: Context,
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        netlistText: String,
        circuitTitle: String,
        viewState: PlotExporter.PlotViewState = PlotExporter.PlotViewState()
    ): File? {
        val engineLabel = when {
            NativeNgSpice.isNativeAvailable -> "ngspice native"
            NativeNgSpice.lastInitError != null -> "built-in engine"
            else -> "auto engine"
        }
        return PlotExporter.buildReportPdf(
            context,
            PlotExporter.ReportBundle(
                plot = plot,
                netlistText = netlistText,
                circuitTitle = circuitTitle,
                engineLabel = engineLabel
            ),
            activeVectors,
            settings,
            viewState
        )
    }
}
