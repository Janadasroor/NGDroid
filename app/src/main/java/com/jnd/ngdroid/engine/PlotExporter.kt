package com.jnd.ngdroid.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.jnd.ngdroid.data.AppSettings
import com.jnd.ngdroid.ui.plot.TraceColors
import com.jnd.ngdroid.ui.plot.formatEng
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PlotExporter {

    private const val TAG = "PlotExporter"
    const val PNG_WIDTH = 1600
    const val PNG_HEIGHT = 1000

    data class PlotViewState(
        val zoomScaleX: Float = 1f,
        val zoomScaleY: Float = 1f,
        val panOffsetX: Float = 0f,
        val panOffsetY: Float = 0f,
        val showCursors: Boolean = false,
        val cursor1Frac: Float = 0.3f,
        val cursor2Frac: Float = 0.7f
    )

    data class ReportBundle(
        val plot: SimulationPlot,
        val netlistText: String,
        val circuitTitle: String,
        val engineLabel: String
    )

    fun pngFileName(plot: SimulationPlot, nowMillis: Long = System.currentTimeMillis()): String {
        val base = plot.plotName.ifEmpty { "waveform" }.replace(Regex("[^A-Za-z0-9_-]+"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        return "${base}_${stamp}.png"
    }

    fun pdfFileName(plot: SimulationPlot, nowMillis: Long = System.currentTimeMillis()): String {
        val base = plot.plotName.ifEmpty { "report" }.replace(Regex("[^A-Za-z0-9_-]+"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        return "${base}_${stamp}.pdf"
    }

    /** Render the waveform offscreen at [PNG_WIDTH]x[PNG_HEIGHT], identical to on-screen. */
    fun renderPlotBitmap(
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        viewState: PlotViewState = PlotViewState()
    ): Bitmap? {
        val scaleVec = plot.scaleVector ?: return null
        if (scaleVec.values.isEmpty()) return null
        return try {
            val bg = if (settings.darkPlotBackground) 0xFF0A0A0A else 0xFFE8E4F0
            // Android-canvas renderer mirrors drawWaveform math (Compose DrawScope
            // can't easily run offscreen without a view; math is shared by inspection).
            val androidBitmap = Bitmap.createBitmap(PNG_WIDTH, PNG_HEIGHT, Bitmap.Config.ARGB_8888)
            val androidCanvas = Canvas(androidBitmap)
            androidCanvas.drawColor(bg.toInt())
            drawTracesAndroid(androidCanvas, scaleVec, plot, activeVectors, settings, viewState)
            drawLegendAndroid(androidCanvas, plot, activeVectors, settings.darkPlotBackground)
            androidBitmap
        } catch (e: Exception) {
            Log.e(TAG, "renderPlotBitmap failed", e)
            renderPlotBitmapFallback(plot, activeVectors, settings, viewState)
        }
    }

    private fun drawTracesAndroid(
        canvas: Canvas,
        scaleVec: VectorSeries,
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        viewState: PlotViewState
    ) {
        val activeDataVecs = plot.dataVectors.filter { activeVectors.contains(it.name) && it.values.isNotEmpty() }
        val hasCurrentVecs = activeDataVecs.any { it.isCurrent }
        val hasVoltageVecs = activeDataVecs.any { !it.isCurrent }
        val isDualAxis = hasCurrentVecs && hasVoltageVecs

        val w = PNG_WIDTH.toFloat()
        val h = (PNG_HEIGHT - 90).toFloat()
        val padL = 110f
        val padR = if (isDualAxis) 110f else 30f
        val padT = 30f
        val padB = 80f
        val gw = w - padL - padR
        val gh = h - padT - padB
        if (gw <= 0 || gh <= 0) return

        val isLogX = scaleVec.name.equals("frequency", ignoreCase = true)

        val xMin = scaleVec.values.minOrNull() ?: 0.0
        val xMax = scaleVec.values.maxOrNull() ?: 1.0
        val xMinLog = kotlin.math.log10(max(1e-12, xMin))
        val xMaxLog = kotlin.math.log10(max(1e-12, xMax))
        val xRangeLog = if (xMaxLog > xMinLog) (xMaxLog - xMinLog) / viewState.zoomScaleX else 1.0
        val xRangeLinear = if (xMax > xMin) (xMax - xMin) / viewState.zoomScaleX else 1.0

        var yMinLeft = Double.MAX_VALUE
        var yMaxLeft = -Double.MAX_VALUE
        // Min-max decimated envelope (shared with on-screen drawWaveform):
        // bounds + path on <=1500 points, exact global min/max, spikes kept.
        val decimated = activeDataVecs.associate { vec ->
            vec.name to decimateXY(scaleVec.values, vec.values)
        }
        activeDataVecs.filter { !it.isCurrent || !isDualAxis }.forEach { vec ->
            decimated[vec.name]?.forEach { (_, v) ->
                if (v < yMinLeft) yMinLeft = v
                if (v > yMaxLeft) yMaxLeft = v
            }
        }
        if (yMinLeft == Double.MAX_VALUE) yMinLeft = -1.0
        if (yMaxLeft == -Double.MAX_VALUE) yMaxLeft = 1.0
        if (yMaxLeft == yMinLeft) { yMaxLeft += 1.0; yMinLeft -= 1.0 }
        val yRangeLeft = if (yMaxLeft > yMinLeft) (yMaxLeft - yMinLeft) / viewState.zoomScaleY else 1.0

        var yMinRight = Double.MAX_VALUE
        var yMaxRight = -Double.MAX_VALUE
        if (isDualAxis) {
            activeDataVecs.filter { it.isCurrent }.forEach { vec ->
                decimated[vec.name]?.forEach { (_, v) ->
                    if (v < yMinRight) yMinRight = v
                    if (v > yMaxRight) yMaxRight = v
                }
            }
            if (yMinRight == Double.MAX_VALUE) yMinRight = -1.0
            if (yMaxRight == -Double.MAX_VALUE) yMaxRight = 1.0
            if (yMaxRight == yMinRight) { yMaxRight += 1.0; yMinRight -= 1.0 }
        }
        val yRangeRight = if (yMaxRight > yMinRight) (yMaxRight - yMinRight) / viewState.zoomScaleY else 1.0

        val dark = settings.darkPlotBackground
        val gridPaint = Paint().apply {
            color = if (dark) 0xFF2C2C2C.toInt() else 0xFFD0D0D0.toInt()
            strokeWidth = 2f
        }
        val axisPaint = Paint().apply {
            color = if (dark) android.graphics.Color.GRAY else android.graphics.Color.DKGRAY
            strokeWidth = 4f
        }

        if (settings.showGridLines) {
            for (i in 0..5) {
                val y = padT + (gh / 5) * i
                canvas.drawLine(padL, y, padL + gw, y, gridPaint)
            }
            for (i in 0..5) {
                val x = padL + (gw / 5) * i
                canvas.drawLine(x, padT, x, padT + gh, gridPaint)
            }
        }
        canvas.drawLine(padL, padT, padL, padT + gh, axisPaint)
        canvas.drawLine(padL, padT + gh, padL + gw, padT + gh, axisPaint)
        if (isDualAxis) {
            canvas.drawLine(padL + gw, padT, padL + gw, padT + gh, axisPaint)
        }

        val tracePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = max(3f, settings.traceStrokeWidthDp * 2.5f)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        val scaleX = viewState.panOffsetX / 2.5f
        val scaleY = viewState.panOffsetY / 2.5f

        plot.dataVectors.forEachIndexed { vecIdx, vec ->
            if (!activeVectors.contains(vec.name) || vec.values.isEmpty()) return@forEachIndexed
            val composeColor = TraceColors[vecIdx % TraceColors.size]
            tracePaint.color = android.graphics.Color.rgb(
                (composeColor.red * 255).toInt(),
                (composeColor.green * 255).toInt(),
                (composeColor.blue * 255).toInt()
            )

            val useRightAxis = isDualAxis && vec.isCurrent
            val currentYMin = if (useRightAxis) yMinRight else yMinLeft
            val currentYRange = if (useRightAxis) yRangeRight else yRangeLeft

            val path = android.graphics.Path()
            val pts = decimated[vec.name]
                ?: decimateXY(scaleVec.values, vec.values)
            var first = true
            for ((xVal, yVal) in pts) {
                if (!xVal.isFinite() || !yVal.isFinite()) {
                    first = true
                    continue
                }
                val px = if (isLogX) {
                    val logVal = kotlin.math.log10(max(1e-12, xVal))
                    padL + ((logVal - xMinLog) / xRangeLog * gw).toFloat() + scaleX
                } else {
                    padL + ((xVal - xMin) / xRangeLinear * gw).toFloat() + scaleX
                }

                val py = padT + gh - ((yVal - currentYMin) / currentYRange * gh).toFloat() + scaleY
                if (px in (padL - 20f)..(padL + gw + 20f)) {
                    if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
                }
            }
            canvas.drawPath(path, tracePaint)
        }

        val labelPaint = Paint().apply {
            color = if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY
            textSize = 30f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val xUnit = if (isLogX) "Hz" else ""
        canvas.drawText(fmtShort(xMin) + xUnit, padL, padT + gh + 55f, labelPaint)
        val xMaxStr = fmtShort(xMax) + xUnit
        canvas.drawText(xMaxStr, padL + gw - labelPaint.measureText(xMaxStr), padT + gh + 55f, labelPaint)

        canvas.drawText(fmtShort(yMaxLeft) + (if (isDualAxis) "V" else ""), 8f, padT + 30f, labelPaint)
        canvas.drawText(fmtShort(yMinLeft) + (if (isDualAxis) "V" else ""), 8f, padT + gh, labelPaint)

        if (isDualAxis) {
            val rMaxStr = fmtShort(yMaxRight) + "A"
            val rMinStr = fmtShort(yMinRight) + "A"
            canvas.drawText(rMaxStr, padL + gw + 8f, padT + 30f, labelPaint)
            canvas.drawText(rMinStr, padL + gw + 8f, padT + gh, labelPaint)
        }
    }

    private fun drawLegendAndroid(
        canvas: Canvas,
        plot: SimulationPlot,
        activeVectors: Set<String>,
        dark: Boolean
    ) {
        val paint = Paint().apply { textSize = 30f; typeface = Typeface.MONOSPACE; isAntiAlias = true }
        paint.color = if (dark) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
        var x = 30f
        val y = PNG_HEIGHT - 30f
        plot.dataVectors.forEachIndexed { idx, vec ->
            if (!activeVectors.contains(vec.name)) return@forEachIndexed
            val c = TraceColors[idx % TraceColors.size]
            paint.color = android.graphics.Color.rgb(
                (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()
            )
            canvas.drawCircle(x + 12f, y - 10f, 12f, paint.apply { style = Paint.Style.FILL })
            paint.color = android.graphics.Color.WHITE
            canvas.drawText(vec.name, x + 32f, y, paint)
            x += 32f + paint.measureText(vec.name) + 50f
            if (x > PNG_WIDTH - 200) return
        }
    }

    private fun renderPlotBitmapFallback(
        plot: SimulationPlot,
        activeVectors: Set<String>,
        settings: AppSettings,
        viewState: PlotViewState
    ): Bitmap? {
        return try {
            val bmp = Bitmap.createBitmap(PNG_WIDTH, PNG_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(if (settings.darkPlotBackground) 0xFF0A0A0A.toInt() else 0xFFFFFFFF.toInt())
            plot.scaleVector?.let { drawTracesAndroid(canvas, it, plot, activeVectors, settings, viewState) }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "fallback render failed", e)
            null
        }
    }

    private fun fmtShort(v: Double): String {
        return formatEng(v, "").trim()
    }

    private fun exportsDir(context: Context, sub: String): File {
        return File(context.cacheDir, sub).apply { mkdirs() }
    }

    private fun cleanOldFiles(dir: File, ext: String, maxKept: Int = 5) {
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.extension == ext } ?: return
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        files.forEach { if (now - it.lastModified() > day) it.delete() }
        val remaining = dir.listFiles { f -> f.extension == ext }
            ?.sortedByDescending { it.lastModified() } ?: return
        if (remaining.size > maxKept) remaining.drop(maxKept).forEach { it.delete() }
    }

    fun writePng(context: Context, plot: SimulationPlot, bitmap: Bitmap): File? {
        return try {
            val dir = exportsDir(context, "png_exports")
            cleanOldFiles(dir, "png")
            val file = File(dir, pngFileName(plot))
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "writePng failed", e)
            null
        }
    }

    fun sharePng(context: Context, plot: SimulationPlot, bitmap: Bitmap) {
        val file = writePng(context, plot, bitmap) ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_SUBJECT, "NGDroid waveform: ${plot.title.ifEmpty { plot.plotName }}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "waveform.png", uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share waveform PNG via"))
    }

    fun savePngToDownloads(context: Context, plot: SimulationPlot, bitmap: Bitmap): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, pngFileName(plot))
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/NGDroid")
                }
                // Same Downloads collection as CSV/PDF so the file lands in
                // Downloads/NGDroid on every API level (Images.Media + a
                // Download/ path is inconsistent and fails on some OEM builds).
                val collection = android.provider.MediaStore.Downloads.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                val uri = context.contentResolver.insert(collection, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
                } ?: return null
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(downloads, "NGDroid").apply { mkdirs() }
                val file = File(dir, pngFileName(plot))
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "savePngToDownloads failed", e)
            null
        }
    }

    fun buildReportPdf(
        context: Context,
        bundle: ReportBundle,
        activeVectors: Set<String>,
        settings: AppSettings,
        viewState: PlotViewState = PlotViewState()
    ): File? {
        val plot = bundle.plot
        if (plot.scaleVector?.values.isNullOrEmpty()) return null
        val doc = PdfDocument()
        try {
            val pageW = 595 // A4 @72dpi
            val pageH = 842
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = page.canvas
            var y = 48f

            val titlePaint = Paint().apply {
                textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.BLACK; isAntiAlias = true
            }
            val subPaint = Paint().apply {
                textSize = 12f; typeface = Typeface.MONOSPACE
                color = 0xFF555555.toInt(); isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                textSize = 12f; typeface = Typeface.DEFAULT
                color = android.graphics.Color.BLACK; isAntiAlias = true
            }
            val monoPaint = Paint().apply {
                textSize = 10f; typeface = Typeface.MONOSPACE
                color = android.graphics.Color.BLACK; isAntiAlias = true
            }
            val headPaint = Paint().apply {
                textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.BLACK; isAntiAlias = true
            }

            val title = plot.title.ifEmpty { "Simulation Waveforms" }
            canvas.drawText("NGDroid Lab Report", 40f, y, titlePaint); y += 24f
            canvas.drawText(title, 40f, y, bodyPaint); y += 16f
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(plot.timestampMillis))
            canvas.drawText(
                "Plot: ${plot.plotName.ifEmpty { "—" }}  •  Type: ${plot.plotType.ifEmpty { "—" }}  •  $stamp",
                40f, y, subPaint
            ); y += 14f
            val scaleValues = plot.scaleVector?.values.orEmpty()
            canvas.drawText(
                "Circuit: ${bundle.circuitTitle.ifEmpty { "Unsaved draft" }}  •  ${bundle.engineLabel}  •  ${scaleValues.size} pts",
                40f, y, subPaint
            ); y += 22f

            val bmp = renderPlotBitmap(plot, activeVectors, settings, viewState)
            if (bmp != null) {
                val imgW = (pageW - 80).toFloat()
                val imgH = imgW * bmp.height / bmp.width.toFloat()
                val dst = android.graphics.RectF(40f, y, 40f + imgW, y + imgH)
                canvas.drawBitmap(bmp, null, dst, Paint().apply { isFilterBitmap = true })
                y += imgH + 18f
            }

            canvas.drawText("Measurements", 40f, y, headPaint); y += 18f
            val rows = plot.dataVectors.filter { activeVectors.contains(it.name) }.map { vec ->
                WaveformAnalyzer.calculateMeasurements(plot.scaleVector, vec)
            }
            val colX = floatArrayOf(40f, 170f, 280f, 380f, 480f)
            val header = listOf("Trace", "Min", "Max", "RMS", "Freq")
            header.forEachIndexed { i, h -> canvas.drawText(h, colX[i], y, monoPaint) }
            y += 14f
            canvas.drawLine(40f, y, (pageW - 40).toFloat(), y, Paint().apply { strokeWidth = 1f })
            y += 14f
            rows.forEach { m ->
                if (y > pageH - 120) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    y = 48f
                }
                val freq = m.estimatedFreqHz?.let { formatEng(it, "Hz") } ?: "—"
                val cells = listOf(
                    m.netName.take(14),
                    formatEng(m.minVal, "V"),
                    formatEng(m.maxVal, "V"),
                    formatEng(m.rmsVal, "V"),
                    freq
                )
                cells.forEachIndexed { i, c -> canvas.drawText(c, colX[i], y, monoPaint) }
                y += 15f
            }
            y += 10f

            canvas.drawText("Netlist", 40f, y, headPaint); y += 18f
            val bgPaint = Paint().apply { color = 0xFFF2F0F7.toInt() }
            bundle.netlistText.lines().forEach { line ->
                if (y > pageH - 60) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    y = 48f
                }
                canvas.drawRect(40f, y - 11f, (pageW - 40).toFloat(), y + 4f, bgPaint)
                canvas.drawText(line.take(90), 46f, y, monoPaint)
                y += 14f
            }
            y += 8f
            canvas.drawText("Generated by NGDroid — SPICE Simulator", 40f, (pageH - 32).toFloat(), subPaint)
            doc.finishPage(page) // y unused after this; page done

            val dir = exportsDir(context, "pdf_exports")
            cleanOldFiles(dir, "pdf")
            val file = File(dir, pdfFileName(plot))
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            return file
        } catch (e: Exception) {
            Log.e(TAG, "buildReportPdf failed", e)
            return null
        } finally {
            doc.close()
        }
    }

    fun sharePdf(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "NGDroid lab report: $title")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "report.pdf", uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share lab report PDF via"))
    }

    fun savePdfToDownloads(context: Context, file: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/NGDroid")
                }
                val collection = android.provider.MediaStore.Downloads.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                val uri = context.contentResolver.insert(collection, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: return null
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(downloads, "NGDroid").apply { mkdirs() }
                val dest = File(dir, file.name)
                file.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        } catch (e: Exception) {
            Log.e(TAG, "savePdfToDownloads failed", e)
            null
        }
    }
}
