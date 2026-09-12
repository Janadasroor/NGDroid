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
    val estimatedFreqHz: Double? = null
)

object WaveformAnalyzer {
    fun calculateMeasurements(
        scaleVector: VectorSeries?,
        dataVector: VectorSeries
    ): NetMeasurements {
        val vals = dataVector.values
        if (vals.isEmpty()) {
            return NetMeasurements(
                netName = dataVector.name,
                count = 0,
                minVal = 0.0,
                maxVal = 0.0,
                peakToPeak = 0.0,
                avgVal = 0.0,
                rmsVal = 0.0
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

        if (scaleVector != null && scaleVector.values.size == count && count > 4) {
            val times = scaleVector.values
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
            netName = dataVector.name,
            count = count,
            minVal = minV,
            maxVal = maxV,
            peakToPeak = p2p,
            avgVal = avgV,
            rmsVal = rmsV,
            estimatedPeriod = period,
            estimatedFreqHz = freq
        )
    }
}
