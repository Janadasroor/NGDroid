package com.jnd.ngdroid.domain

import com.jnd.ngdroid.engine.NetMeasurements
import com.jnd.ngdroid.engine.VectorSeries
import com.jnd.ngdroid.engine.WaveformAnalyzer

class CalculateMeasurementsUseCase {

    operator fun invoke(
        scaleVector: VectorSeries?,
        dataVector: VectorSeries
    ): NetMeasurements {
        return WaveformAnalyzer.calculateMeasurements(scaleVector, dataVector)
    }

    /** Stats over the cursor window [xFrom, xTo] instead of the whole trace. */
    fun invokeRange(
        scaleVector: VectorSeries?,
        dataVector: VectorSeries,
        xFrom: Double,
        xTo: Double,
        rangeLabel: String? = null
    ): NetMeasurements {
        return WaveformAnalyzer.calculateRangeMeasurements(
            scaleVector, dataVector, xFrom, xTo, rangeLabel
        )
    }
}
