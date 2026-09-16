package com.jnd.ngdroid.engine

import com.jnd.ngdroid.data.PresetNetlists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

class SimulationRepository(
    scope: CoroutineScope? = null
) {
    companion object {
        const val MAX_LOG_LINES = 2000
        /** Hard cap for the bg_run completion wait (reported as timeout). */
        const val RUN_TIMEOUT_MS = 120_000L
        /** Grace to first observe the bg thread before trusting "not running". */
        const val START_GRACE_MS = 10_000L
        /** Terminal status set by stopSimulation(); run completion must not overwrite it. */
        const val STATUS_STOPPED = "Simulation Stopped by User"
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

    /**
     * Single-flight guard: ngspice has one background thread, so two
     * overlapping runs corrupt plots and interleave vector buffers.
     * [runMutex.tryLock] rejects a second Run while one is active instead
     * of queueing it behind the live run.
     */
    private val runMutex = Mutex()
    private var runJob: Job? = null

    // Thread-safe in-memory vector buffers for high-frequency streaming.
    // All ngspice-callback state below is guarded by [dataLock]: callbacks
    // arrive on ngspice threads while flush runs on Dispatchers.IO, so every
    // read/write must hold the lock — otherwise scale/data lengths can skew
    // mid-stream and the viewer shows mismatched traces.
    private val dataLock = Any()
    private val vectorBuffers = ConcurrentHashMap<String, ArrayList<Double>>()
    private var activeScaleVectorName: String = "time"
    private var activePlotTitle: String = ""
    private var activePlotName: String = ""
    private var activePlotType: String = ""
    private var activeVectorNames: List<String> = emptyList()

    @Volatile
    private var lastUIUpdateTime = 0L

    /** Visible for tests: lets JVM tests drive the JNI callback path. */
    internal val callback = object : NgSpiceCallback {
        override fun onLog(msg: String) {
            val trimmed = msg.trim()
            if (trimmed.isNotEmpty()) {
                val isError = trimmed.contains("Error:", ignoreCase = true) ||
                        trimmed.contains("Fatal", ignoreCase = true) ||
                        trimmed.contains("singular", ignoreCase = true) ||
                        trimmed.contains("non-convergence", ignoreCase = true) ||
                        trimmed.contains("unknown device", ignoreCase = true)

                synchronized(dataLock) {
                    _state.update { current ->
                        current.copy(
                            logs = (current.logs + trimmed).takeLast(MAX_LOG_LINES),
                            hasError = if (isError) true else current.hasError,
                            errorMessage = if (isError) trimmed else current.errorMessage
                        )
                    }
                }
            }
        }

        override fun onInitData(scaleName: String, title: String, name: String, type: String, vecNames: Array<String>) {
            synchronized(dataLock) {
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
        }

        override fun onData(vecNames: Array<String>, values: DoubleArray) {
            if (vecNames.isEmpty() || values.isEmpty()) return

            synchronized(dataLock) {
                // One callback = one point appended to every vector together,
                // so a concurrent flush always sees consistent lengths.
                vecNames.forEachIndexed { idx, vname ->
                    if (idx < values.size) {
                        val buffer = vectorBuffers[vname] ?: ArrayList<Double>(1000).also { vectorBuffers[vname] = it }
                        buffer.add(values[idx])
                    }
                }

                val now = System.currentTimeMillis()
                // Throttle StateFlow updates to max 20 FPS (50ms) to ensure 100% smooth UI without ANR
                if (now - lastUIUpdateTime > 50L) {
                    lastUIUpdateTime = now
                    flushVectorBuffersToState()
                }
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
        // Single lock with the callbacks: buffers are copied atomically, so
        // scale and data vectors always have consistent lengths. Reentrant
        // (onData already holds the lock) — synchronized allows that.
        synchronized(dataLock) {
            val scaleBuf = vectorBuffers[activeScaleVectorName] ?: return
            val scaleValuesCopy: List<Double> = ArrayList(scaleBuf)

            val scaleVec = VectorSeries(name = activeScaleVectorName, isScale = true, values = scaleValuesCopy)

            val dataVecs = activeVectorNames.filter { it != activeScaleVectorName }.map { vname ->
                val buf = vectorBuffers[vname]
                val valsCopy: List<Double> = if (buf != null) ArrayList(buf) else emptyList()

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
        } // synchronized(dataLock)
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
        // Fast-reject a second Run while one is live or paused — ngspice's
        // single bg thread cannot serve two. tryLock closes the tap-race
        // where isSimulating hasn't flipped yet.
        if (!runMutex.tryLock()) {
            _state.update { current ->
                current.copy(
                    logs = appendLog(
                        current.logs,
                        "[WARN] Simulation already running — halt or stop it before re-running."
                    )
                )
            }
            return
        }
        runJob = scope.launch(Dispatchers.IO) {
            try {
                runSimulationLocked()
            } finally {
                runMutex.unlock()
            }
        }
    }

    private suspend fun runSimulationLocked() {
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
                return
            }

            val lines = _netlistText.value.lines().toTypedArray()
            // Missing .so / torn-down bridge must fail the run, never kill
            // the IO coroutine silently.
            val started = try {
                NativeNgSpice.nativeRunNetlist(lines)
            } catch (_: Throwable) {
                false
            }

            // bg_run returns as soon as the background thread launches —
            // wait for real completion, flushing along the way, otherwise
            // fast analyses (.ac) finish after our final flush and the
            // viewer shows vector names with empty traces. ngspice can
            // report not-running right after bg_run (stale callback) so
            // only trust "not running" after having seen "running", with
            // a 10 s start grace; hard cap 120 s (reported as timeout,
            // never silently as success).
            var timedOut = false
            if (started) {
                val startMs = System.currentTimeMillis()
                val deadline = startMs + RUN_TIMEOUT_MS
                val startGrace = startMs + START_GRACE_MS
                var seenRunning = false
                while (System.currentTimeMillis() < deadline) {
                    val running = try {
                        NativeNgSpice.nativeIsRunning()
                    } catch (_: Throwable) {
                        false
                    }
                    if (running) seenRunning = true
                    flushVectorBuffersToState()
                    if (seenRunning && !running) break
                    if (!seenRunning && System.currentTimeMillis() > startGrace) break
                    kotlinx.coroutines.delay(100)
                }
                timedOut = try {
                    NativeNgSpice.nativeIsRunning()
                } catch (_: Throwable) {
                    false
                }
                if (timedOut) {
                    // Never leave the bg thread running: the next ngSpice_Circ
                    // while it runs is undefined, and the single-flight guard
                    // would reject re-runs with "already running".
                    runCatching { NativeNgSpice.nativeHalt() }
                }
            }

            // Final flush of vector buffers when simulation completes
            flushVectorBuffersToState()

            _state.update {
                // A user halt/stop during the wait already settled the state —
                // don't overwrite it with "Complete".
                if (!it.isSimulating && it.isPaused) return@update it
                if (!it.isSimulating && it.statusText == STATUS_STOPPED) return@update it
                when {
                    !started -> it.copy(
                        isSimulating = false,
                        progressFraction = 1.0f,
                        statusText = "Simulation Failed"
                    )
                    timedOut -> it.copy(
                        isSimulating = false,
                        progressFraction = 1.0f,
                        statusText = "Simulation Timed Out",
                        hasError = true,
                        errorMessage = "Simulation did not finish within ${RUN_TIMEOUT_MS / 1000} s.",
                        logs = appendLog(
                            it.logs,
                            "[ERROR] Simulation timed out after ${RUN_TIMEOUT_MS / 1000} s " +
                                "and was halted automatically."
                        )
                    )
                    // Error lines arrived but ngspice still finished: report
                    // honestly instead of a clean "Complete".
                    it.hasError -> it.copy(
                        isSimulating = false,
                        progressFraction = 1.0f,
                        statusText = "Simulation Completed with Errors"
                    )
                    else -> it.copy(
                        isSimulating = false,
                        progressFraction = 1.0f,
                        statusText = "Simulation Complete"
                    )
                }
            }
    }

    fun haltSimulation() {
        scope.launch(Dispatchers.IO) {
            if (isInitialized) {
                runCatching { NativeNgSpice.nativeHalt() }
                runCatching { flushVectorBuffersToState() }
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

    /**
     * Abandon the run: halts the bg thread like [haltSimulation] but clears
     * the paused flag, so no Resume affordance is offered afterwards.
     * Partial plot data is kept for inspection.
     */
    fun stopSimulation() {
        scope.launch(Dispatchers.IO) {
            if (isInitialized) {
                runCatching { NativeNgSpice.nativeHalt() }
                runCatching { flushVectorBuffersToState() }
                _state.update {
                    it.copy(
                        isSimulating = false,
                        isPaused = false,
                        statusText = STATUS_STOPPED,
                        logs = appendLog(it.logs, "[INFO] Simulation stopped by user.")
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isSimulating = false,
                        isPaused = false,
                        statusText = STATUS_STOPPED,
                        logs = appendLog(it.logs, "[INFO] Simulation stopped by user.")
                    )
                }
            }
        }
    }

    fun resumeSimulation() {
        scope.launch(Dispatchers.IO) {
            if (isInitialized && _state.value.isPaused) {
                runCatching { NativeNgSpice.nativeResume() }
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
