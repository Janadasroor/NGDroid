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

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun csvFileName(plot: SimulationPlot, nowMillis: Long = System.currentTimeMillis()): String {
        val base = plot.plotName.ifEmpty { "simulation_results" }
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        return "${base}_${stamp}.csv"
    }

    fun generateCsvString(plot: SimulationPlot): String {
        val scaleVec = plot.scaleVector ?: return ""
        val activeDataVecs = plot.dataVectors

        val sb = StringBuilder()
        
        val headers = mutableListOf<String>()
        headers.add("\"${scaleVec.name}\"")
        activeDataVecs.forEach { headers.add("\"${it.name}\"") }
        sb.append(headers.joinToString(",")).append("\n")

        val rowCount = scaleVec.values.size
        for (i in 0 until rowCount) {
            val row = mutableListOf<String>()
            row.add(String.format(Locale.US, "%.8e", scaleVec.values[i]))

            for (vec in activeDataVecs) {
                if (i < vec.values.size) {
                    row.add(String.format(Locale.US, "%.8e", vec.values[i]))
                } else {
                    row.add("0.00000000e+00")
                }
            }
            sb.append(row.joinToString(",")).append("\n")
        }

        return sb.toString()
    }

    fun shareCsvFile(context: Context, plot: SimulationPlot) {
        val csvContent = generateCsvString(plot)
        if (csvContent.isEmpty()) return

        val cacheDir = File(context.cacheDir, "csv_exports")
        cacheDir.mkdirs()

        cleanOldCacheFiles(cacheDir)

        val fileName = csvFileName(plot)
        val csvFile = File(cacheDir, fileName)
        csvFile.writeText(csvContent)

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, csvFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "SPICE Simulation Results: ${plot.title}")
            putExtra(Intent.EXTRA_TEXT, "Attached CSV simulation data exported from NGDroid.")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "simulation.csv", contentUri)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share Simulation CSV via")
        context.startActivity(chooserIntent)
    }

    /**
     * Save CSV into Downloads (Oreo-friendly). Returns the destination Uri or null.
     * API 29+: MediaStore Downloads (no permission). API 26-28: legacy public
     * Downloads dir (needs WRITE_EXTERNAL_STORAGE granted by the caller).
     */
    fun saveCsvToDownloads(context: Context, plot: SimulationPlot): android.net.Uri? {
        val csvContent = generateCsvString(plot)
        if (csvContent.isEmpty()) return null
        val fileName = csvFileName(plot)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/NGDroid")
                }
                val collection = android.provider.MediaStore.Downloads.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                val uri = context.contentResolver.insert(collection, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csvContent.toByteArray())
                } ?: return null
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val dir = File(downloads, "NGDroid").apply { mkdirs() }
                val file = File(dir, fileName)
                file.writeText(csvContent)
                android.net.Uri.fromFile(file)
            }
        } catch (e: Exception) {
            android.util.Log.e("CsvExporter", "Failed to save CSV to Downloads", e)
            null
        }
    }

    fun needsLegacyStoragePermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT <= 28
    }

    private fun cleanOldCacheFiles(cacheDir: File) {
        if (!cacheDir.exists()) return
        val files = cacheDir.listFiles { file -> file.extension == "csv" } ?: return
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        files.forEach { file ->
            if (now - file.lastModified() > oneDayMillis) {
                file.delete()
            }
        }

        val remainingFiles = cacheDir.listFiles { file -> file.extension == "csv" }?.sortedByDescending { it.lastModified() } ?: return
        if (remainingFiles.size > 5) {
            remainingFiles.drop(5).forEach { file ->
                file.delete()
            }
        }
    }
}
