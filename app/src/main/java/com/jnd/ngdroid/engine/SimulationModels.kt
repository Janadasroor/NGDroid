package com.jnd.ngdroid.engine

data class VectorSeries(
    val name: String,
    val isScale: Boolean = false,
    val values: List<Double> = emptyList()
) {
    val isCurrent: Boolean
        get() = name.endsWith("#branch", ignoreCase = true) ||
                name.startsWith("i(", ignoreCase = true) ||
                name.startsWith("I(", ignoreCase = true)
}

data class SimulationPlot(
    val title: String = "",
    val plotName: String = "",
    val plotType: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val scaleVector: VectorSeries? = null,
    val dataVectors: List<VectorSeries> = emptyList()
)

data class SimulationState(
    val isSimulating: Boolean = false,
    val isPaused: Boolean = false,
    val statusText: String = "Idle",
    val progressFraction: Float? = null,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val currentPlot: SimulationPlot? = null,
    val activeVectors: Set<String> = emptySet(),
    val logs: List<String> = emptyList(),
    val totalPointCount: Int = 0,
    val plotHistory: List<SimulationPlot> = emptyList()
)

data class PresetNetlist(
    val id: String,
    val title: String,
    val description: String,
    val netlist: String,
    val category: String = "General"
)
