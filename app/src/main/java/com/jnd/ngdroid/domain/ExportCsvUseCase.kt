package com.jnd.ngdroid.domain

import android.content.Context
import com.jnd.ngdroid.engine.CsvExporter
import com.jnd.ngdroid.engine.SimulationPlot

class ExportCsvUseCase {

    fun generateCsv(plot: SimulationPlot): String {
        return CsvExporter.generateCsvString(plot)
    }

    fun shareCsv(context: Context, plot: SimulationPlot) {
        CsvExporter.shareCsvFile(context, plot)
    }

    fun saveToDownloads(context: Context, plot: SimulationPlot): android.net.Uri? {
        return CsvExporter.saveCsvToDownloads(context, plot)
    }

    fun needsLegacyStoragePermission(): Boolean {
        return CsvExporter.needsLegacyStoragePermission()
    }
}
