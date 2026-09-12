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
}
