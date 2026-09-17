package com.jnd.ngdroid.engine

import kotlin.math.abs
import kotlin.math.sqrt

data class NetMeasurements(
    val netName: String,
    val count: Int,
    val minVal: Double,
    val maxVal: Double,
    val peakToPeak: Double,
    val avgVal: Double,
    val rmsVal: Double,
    val estimatedPeriod: Double? = null,
    val estimatedFreqHz: Double? = null,
    /** Set when stats cover a cursor window rather than the whole trace. */
    val rangeLabel: String? = null
)

object WaveformAnalyzer {
    fun calculateMeasurements(
        scaleVector: VectorSeries?,
        dataVector: VectorSeries
    ): NetMeasurements = calculateCore(
        netName = dataVector.name,
        xs = scaleVector?.values?.takeIf { it.size == dataVector.values.size },
        ys = dataVector.values,
        rangeLabel = null
    )

    /**
     * Stats over samples whose scale value falls in [xFrom, xTo] (e.g. a
     * cursor window). Empty window yields a zero-count result; a missing or
     * mismatched scale falls back to the full trace. Pure.
     */
    fun calculateRangeMeasurements(
        scaleVector: VectorSeries?,
        dataVector: VectorSeries,
        xFrom: Double,
        xTo: Double,
        rangeLabel: String? = null
    ): NetMeasurements {
        val xs = scaleVector?.values
        val ys = dataVector.values
        if (xs == null || xs.size != ys.size || ys.isEmpty()) {
            return calculateCore(dataVector.name, null, ys, rangeLabel)
        }
        val lo = minOf(xFrom, xTo)
        val hi = maxOf(xFrom, xTo)
        val fxs = mutableListOf<Double>()
        val fys = mutableListOf<Double>()
        for (i in xs.indices) {
            if (xs[i] in lo..hi) {
                fxs.add(xs[i])
                fys.add(ys[i])
            }
        }
        if (fys.isEmpty()) {
            return NetMeasurements(
                netName = dataVector.name,
                count = 0,
                minVal = 0.0,
                maxVal = 0.0,
                peakToPeak = 0.0,
                avgVal = 0.0,
                rmsVal = 0.0,
                rangeLabel = rangeLabel
            )
        }
        return calculateCore(dataVector.name, fxs, fys, rangeLabel)
    }

    private fun calculateCore(
        netName: String,
        xs: List<Double>?,
        ys: List<Double>,
        rangeLabel: String?
    ): NetMeasurements {
        val vals = ys
        if (vals.isEmpty()) {
            return NetMeasurements(
                netName = netName,
                count = 0,
                minVal = 0.0,
                maxVal = 0.0,
                peakToPeak = 0.0,
                avgVal = 0.0,
                rmsVal = 0.0,
                rangeLabel = rangeLabel
            )
        }

        val count = vals.size
        var minV = Double.MAX_VALUE
        var maxV = -Double.MAX_VALUE
        var sum = 0.0
        var sumSq = 0.0

        for (v in vals) {
            if (v < minV) minV = v
            if (v > maxV) maxV = v
            sum += v
            sumSq += v * v
        }

        val avgV = sum / count
        val rmsV = sqrt(sumSq / count)
        val p2p = if (maxV > minV) maxV - minV else 0.0

        // Estimate frequency / period via mean crossings if scale vector is time
        var period: Double? = null
        var freq: Double? = null

        if (xs != null && xs.size == count && count > 4) {
            val times = xs
            val crossings = mutableListOf<Double>()

            for (i in 0 until count - 1) {
                val y1 = vals[i] - avgV
                val y2 = vals[i + 1] - avgV
                if (y1 < 0 && y2 >= 0) { // Positive mean crossing
                    val t1 = times[i]
                    val t2 = times[i + 1]
                    // Linear interpolation for exact crossing time
                    val tCross = if (y2 != y1) t1 + (0 - y1) * (t2 - t1) / (y2 - y1) else t1
                    crossings.add(tCross)
                }
            }

            if (crossings.size >= 2) {
                val periods = mutableListOf<Double>()
                for (i in 0 until crossings.size - 1) {
                    periods.add(crossings[i + 1] - crossings[i])
                }
                val avgPeriod = periods.average()
                if (avgPeriod > 0) {
                    period = avgPeriod
                    freq = 1.0 / avgPeriod
                }
            }
        }

        return NetMeasurements(
            netName = netName,
            count = count,
            minVal = minV,
            maxVal = maxV,
            peakToPeak = p2p,
            avgVal = avgV,
            rmsVal = rmsV,
            estimatedPeriod = period,
            estimatedFreqHz = freq,
            rangeLabel = rangeLabel
        )
    }
}
