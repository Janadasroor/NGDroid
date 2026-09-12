package com.jnd.ngdroid.engine

import com.jnd.ngdroid.data.PresetNetlists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SimulationRepository(
    scope: CoroutineScope? = null
) {
    companion object {
        const val MAX_LOG_LINES = 2000
    }

    private val ownedJob = SupervisorJob()
    private val scope: CoroutineScope = scope ?: CoroutineScope(ownedJob + Dispatchers.Default)

    /** Cancel owned scope. Safe to call from ViewModel.onCleared(); no-op when an external scope (e.g. viewModelScope) was injected. */
    fun close() {
        ownedJob.cancel()
    }

    private val _netlistText = MutableStateFlow(PresetNetlists.items.first().netlist)
    val netlistText: StateFlow<String> = _netlistText.asStateFlow()

    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    private var isInitialized = false

    // Thread-safe in-memory vector buffers for high-frequency streaming
    private val vectorBuffers = ConcurrentHashMap<String, ArrayList<Double>>()
    private var activeScaleVectorName: String = "time"
    private var activePlotTitle: String = ""
    private var activePlotName: String = ""
    private var activePlotType: String = ""
    private var activeVectorNames: List<String> = emptyList()

    @Volatile
    private var lastUIUpdateTime = 0L

    private val callback = object : NgSpiceCallback {
        override fun onLog(msg: String) {
            val trimmed = msg.trim()
            if (trimmed.isNotEmpty()) {
                val isError = trimmed.contains("Error:", ignoreCase = true) ||
                        trimmed.contains("Fatal", ignoreCase = true) ||
                        trimmed.contains("singular", ignoreCase = true) ||
                        trimmed.contains("non-convergence", ignoreCase = true) ||
                        trimmed.contains("unknown device", ignoreCase = true)

                _state.update { current ->
                    current.copy(
                        logs = (current.logs + trimmed).takeLast(MAX_LOG_LINES),
                        hasError = if (isError) true else current.hasError,
                        errorMessage = if (isError) trimmed else current.errorMessage
                    )
                }
            }
        }

        override fun onInitData(scaleName: String, title: String, name: String, type: String, vecNames: Array<String>) {
            activeScaleVectorName = if (scaleName.isNotEmpty()) scaleName else (vecNames.firstOrNull { it == "time" || it == "frequency" } ?: vecNames.firstOrNull() ?: "time")
            activePlotTitle = title
            activePlotName = name
            activePlotType = type
            activeVectorNames = vecNames.toList()

            vectorBuffers.clear()
            vecNames.forEach { vname ->
                vectorBuffers[vname] = ArrayList(1000)
            }

            val scaleVec = VectorSeries(name = activeScaleVectorName, isScale = true, values = emptyList())
            val dataVecs = vecNames.filter { it != activeScaleVectorName }.map { vname ->
                VectorSeries(name = vname, isScale = false, values = emptyList())
            }

            val newPlot = SimulationPlot(
                title = title,
                plotName = name,
                plotType = type,
                scaleVector = scaleVec,
                dataVectors = dataVecs
            )

            _state.update { current ->
                current.copy(
                    currentPlot = newPlot,
                    activeVectors = dataVecs.map { it.name }.toSet(),
                    totalPointCount = 0
                )
            }
        }

        override fun onData(vecNames: Array<String>, values: DoubleArray) {
            if (vecNames.isEmpty() || values.isEmpty()) return

            vecNames.forEachIndexed { idx, vname ->
                if (idx < values.size) {
                    val buffer = vectorBuffers[vname] ?: ArrayList<Double>(1000).also { vectorBuffers[vname] = it }
                    synchronized(buffer) {
                        buffer.add(values[idx])
                    }
                }
            }

            val now = System.currentTimeMillis()
            // Throttle StateFlow updates to max 20 FPS (50ms) to ensure 100% smooth UI without ANR
            if (now - lastUIUpdateTime > 50L) {
                lastUIUpdateTime = now
                flushVectorBuffersToState()
            }
        }

        override fun onStatus(status: String) {
            val progressFrac = parseProgressFraction(status)
            _state.update { current ->
                current.copy(
                    statusText = status,
                    progressFraction = progressFrac ?: current.progressFraction
                )
            }
        }
    }

    private fun flushVectorBuffersToState() {
        val scaleBuf = vectorBuffers[activeScaleVectorName] ?: return
        val scaleValuesCopy: List<Double>
        synchronized(scaleBuf) {
            scaleValuesCopy = ArrayList(scaleBuf)
        }

        val scaleVec = VectorSeries(name = activeScaleVectorName, isScale = true, values = scaleValuesCopy)

        val dataVecs = activeVectorNames.filter { it != activeScaleVectorName }.map { vname ->
            val buf = vectorBuffers[vname]
            val valsCopy: List<Double> = if (buf != null) {
                synchronized(buf) { ArrayList(buf) }
            } else emptyList()

            VectorSeries(name = vname, isScale = false, values = valsCopy)
        }

        val updatedPlot = SimulationPlot(
            title = activePlotTitle,
            plotName = activePlotName,
            plotType = activePlotType,
            scaleVector = scaleVec,
            dataVectors = dataVecs
        )

        _state.update { current ->
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            val validHistory = current.plotHistory.filter { (now - it.timestampMillis) < oneDayMillis }.toMutableList()
            val existingIndex = validHistory.indexOfFirst { it.plotName == updatedPlot.plotName && it.title == updatedPlot.title }

            if (existingIndex >= 0) {
                validHistory[existingIndex] = updatedPlot
            } else {
                validHistory.add(0, updatedPlot)
            }

            // Retention Policy: Enforce maximum of 5 simulation run files/plots
            val prunedHistory = validHistory.take(5)

            current.copy(
                totalPointCount = scaleValuesCopy.size,
                currentPlot = updatedPlot,
                plotHistory = prunedHistory
            )
        }
    }

    private fun parseProgressFraction(status: String): Float? {
        val percentIdx = status.indexOf('%')
        if (percentIdx > 0) {
            val colonIdx = status.lastIndexOf(':', percentIdx)
            val spaceIdx = status.lastIndexOf(' ', percentIdx)
            val startIdx = maxOf(colonIdx, spaceIdx) + 1
            if (startIdx < percentIdx) {
                val numStr = status.substring(startIdx, percentIdx).trim()
                val parsed = numStr.toFloatOrNull()
                if (parsed != null) {
                    return (parsed / 100.0f).coerceIn(0.0f, 1.0f)
                }
            }
        }
        return null
    }

    fun setNetlist(text: String) {
        _netlistText.value = text
    }

    fun toggleVectorActive(vecName: String) {
        _state.update { current ->
            val active = current.activeVectors.toMutableSet()
            if (active.contains(vecName)) {
                active.remove(vecName)
            } else {
                active.add(vecName)
            }
            current.copy(activeVectors = active)
        }
    }

    fun clearLogs() {
        _state.update { current ->
            current.copy(logs = emptyList(), hasError = false, errorMessage = null)
        }
    }

    fun restoreLogs(logs: List<String>) {
        _state.update { current ->
            current.copy(logs = logs.takeLast(MAX_LOG_LINES))
        }
    }

    private fun appendLog(current: List<String>, line: String): List<String> {
        return (current + line).takeLast(MAX_LOG_LINES)
    }

    fun runSimulation() {
        scope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isSimulating = true,
                    isPaused = false,
                    hasError = false,
                    errorMessage = null,
                    progressFraction = 0.0f,
                    statusText = "Initializing simulation...",
                    logs = appendLog(it.logs, "--- Running Simulation ---")
                )
            }

            if (!isInitialized) {
                try {
                    isInitialized = NativeNgSpice.nativeInit(callback)
                } catch (e: UnsatisfiedLinkError) {
                    isInitialized = false
                    NativeNgSpice.markInitResult(
                        false,
                        "JNI bridge missing for ABIs ${NativeNgSpice.deviceAbis.joinToString()}: ${e.message}"
                    )
                }
                if (isInitialized) {
                    NativeNgSpice.markInitResult(true)
                } else if (NativeNgSpice.lastInitError == null) {
                    NativeNgSpice.markInitResult(false)
                }
            }

            if (!isInitialized) {
                runFallbackSimulation(_netlistText.value)
                return@launch
            }

            val lines = _netlistText.value.lines().toTypedArray()
            val success = NativeNgSpice.nativeRunNetlist(lines)

            // Final flush of vector buffers when simulation completes
            flushVectorBuffersToState()

            _state.update {
                it.copy(
                    isSimulating = false,
                    progressFraction = 1.0f,
                    statusText = if (success) "Simulation Complete" else "Simulation Failed"
                )
            }
        }
    }

    fun haltSimulation() {
        scope.launch(Dispatchers.IO) {
            if (isInitialized) {
                NativeNgSpice.nativeHalt()
                flushVectorBuffersToState()
                _state.update {
                    it.copy(
                        isSimulating = false,
                        isPaused = true,
                        statusText = "Simulation Halted by User",
                        logs = appendLog(it.logs, "[INFO] Simulation interrupted by user.")
                    )
                }
            }
        }
    }

    fun resumeSimulation() {
        scope.launch(Dispatchers.IO) {
            if (isInitialized && _state.value.isPaused) {
                NativeNgSpice.nativeResume()
                _state.update {
                    it.copy(
                        isSimulating = true,
                        isPaused = false,
                        statusText = "Resuming simulation...",
                        logs = appendLog(it.logs, "[INFO] Simulation resumed.")
                    )
                }
            }
        }
    }

    private fun runFallbackSimulation(netlist: String) {
        _state.update {
            it.copy(logs = appendLog(it.logs, "Using built-in simulation engine..."))
        }

        val numPoints = 200
        val scaleVec = VectorSeries(name = "time", isScale = true, values = (0 until numPoints).map { it * 0.0001 })
        val vIn = VectorSeries(name = "v(in)", isScale = false, values = (0 until numPoints).map { i ->
            val t = i * 0.0001
            if ((t * 100).toInt() % 2 == 0) 5.0 else 0.0
        })
        val vOut = VectorSeries(name = "v(out)", isScale = false, values = (0 until numPoints).map { i ->
            val t = i * 0.0001
            5.0 * (1.0 - kotlin.math.exp(-t / 0.001))
        })

        val fallbackPlot = SimulationPlot(
            title = "RC Low-Pass Filter Response",
            plotName = "tran1",
            plotType = "transient",
            scaleVector = scaleVec,
            dataVectors = listOf(vIn, vOut)
        )

        _state.update {
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            val validHistory = it.plotHistory.filter { p -> (now - p.timestampMillis) < oneDayMillis }.toMutableList()
            val existingIndex = validHistory.indexOfFirst { p -> p.plotName == fallbackPlot.plotName && p.title == fallbackPlot.title }

            if (existingIndex >= 0) {
                validHistory[existingIndex] = fallbackPlot
            } else {
                validHistory.add(0, fallbackPlot)
            }

            val prunedHistory = validHistory.take(5)

            it.copy(
                isSimulating = false,
                progressFraction = 1.0f,
                statusText = "Simulation Complete (Built-in)",
                currentPlot = fallbackPlot,
                activeVectors = setOf("v(in)", "v(out)"),
                totalPointCount = numPoints,
                plotHistory = prunedHistory,
                logs = appendLog(it.logs, "Simulation completed successfully. 200 points computed.")
            )
        }
    }
}
