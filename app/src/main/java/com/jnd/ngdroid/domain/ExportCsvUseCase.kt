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
