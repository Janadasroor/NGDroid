package com.jnd.ngdroid.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jnd.ngdroid.data.AutosaveStatus
import com.jnd.ngdroid.data.AutosaveUiState
import com.jnd.ngdroid.data.MAX_FILE_CHARS
import com.jnd.ngdroid.data.NetlistDataStore
import com.jnd.ngdroid.data.NetlistRepository
import com.jnd.ngdroid.data.RecentFile
import com.jnd.ngdroid.data.SavedNetlist
import com.jnd.ngdroid.domain.RunSimulationUseCase
import com.jnd.ngdroid.engine.SimulationRepository
import com.jnd.ngdroid.agent.formatSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SimulationViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // Inject viewModelScope so coroutines are cancelled on cleared (no leak).
    val repository = SimulationRepository(viewModelScope)
    val netlistRepository = NetlistRepository()
    val undoRedoManager = com.jnd.ngdroid.ui.editor.UndoRedoManager()
    private val netlistStore = NetlistDataStore(application)

    val canUndo: StateFlow<Boolean> = undoRedoManager.canUndo
    val canRedo: StateFlow<Boolean> = undoRedoManager.canRedo

    private val runSimulationUseCase = RunSimulationUseCase(repository)

    val netlistText: StateFlow<String> = repository.netlistText
    val state = repository.state

    private var debounceJob: Job? = null
    private var pendingDraft: String? = null
    private var serviceWatcherStarted = false

    init {
        // Direct ngspice's implicit temp files (new*.plt/.data, cider.log,
        // dc-sweep.out) into an app-private dir instead of the process CWD.
        runCatching {
            val dir = java.io.File(getApplication<Application>().cacheDir, "spice_work")
            if (!dir.exists()) dir.mkdirs()
            repository.setWorkDir(dir.absolutePath)
        }.onFailure { Log.w("SimulationViewModel", "spice_work setup failed", it) }
        viewModelScope.launch {
            try {
                val draft = netlistStore.draftTextFlow.first()
                val library = netlistStore.libraryFlow.first()
                val activeId = netlistStore.activeIdFlow.first()
                val recents = netlistStore.recentFilesFlow.first()
                netlistRepository.setLibrary(library)
                netlistRepository.setActiveId(activeId)
                netlistRepository.setRecentFiles(recents)

                if (!draft.isNullOrEmpty()) {
                    repository.setNetlist(draft)
                    netlistRepository.setDraft(draft)
                    savedStateHandle["saved_netlist"] = draft
                    netlistRepository.setAutosave(
                        AutosaveUiState(AutosaveStatus.SAVED, System.currentTimeMillis())
                    )
                } else {
                    // One-time migration of legacy SavedStateHandle draft.
                    val savedText = savedStateHandle.get<String>("saved_netlist")
                    if (!savedText.isNullOrEmpty()) {
                        repository.setNetlist(savedText)
                        netlistRepository.setDraft(savedText)
                        persistDraftNow(savedText)
                    } else {
                        netlistRepository.setDraft(repository.netlistText.value)
                        persistDraftNow(repository.netlistText.value)
                    }
                }
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to load netlist draft/library", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        debounceJob?.cancel()
        // Best-effort synchronous flush so the latest keystrokes survive process death.
        val pending = pendingDraft
        if (pending != null) {
            try {
                kotlinx.coroutines.runBlocking {
                    netlistStore.saveDraft(pending)
                }
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to flush draft on cleared", e)
            }
        }
        repository.close()
    }

    fun updateNetlist(text: String, isDiscreteAction: Boolean = false) {
        undoRedoManager.onTextChanged(text, isDiscreteAction)
        updateNetlistInternal(text)
    }

    private fun updateNetlistInternal(text: String) {
        repository.setNetlist(text)
        savedStateHandle["saved_netlist"] = text
        netlistRepository.setDraft(text)
        netlistRepository.markDirty()
        pendingDraft = text
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(750)
            netlistRepository.setAutosave(AutosaveUiState(AutosaveStatus.SAVING))
            try {
                netlistStore.saveDraft(text)
                pendingDraft = null
                netlistRepository.setAutosave(
                    AutosaveUiState(AutosaveStatus.SAVED, System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to autosave draft", e)
                netlistRepository.setAutosave(AutosaveUiState(AutosaveStatus.DIRTY))
            }
        }
    }

    fun undo() {
        val current = repository.netlistText.value
        val previous = undoRedoManager.undo(current)
        if (previous != null) {
            updateNetlistInternal(previous)
        }
    }

    fun redo() {
        val current = repository.netlistText.value
        val next = undoRedoManager.redo(current)
        if (next != null) {
            updateNetlistInternal(next)
        }
    }

    private suspend fun persistDraftNow(text: String) {
        try {
            netlistStore.saveDraft(text)
            pendingDraft = null
            netlistRepository.setAutosave(
                AutosaveUiState(AutosaveStatus.SAVED, System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e("SimulationViewModel", "Failed to persist draft", e)
        }
    }

    // ---- Library API ----

    fun saveAsNew(title: String): SavedNetlist? {
        val clean = title.trim().ifEmpty { return null }
        val item = SavedNetlist(title = clean, netlist = repository.netlistText.value)
        val updated = listOf(item) + netlistRepository.library.value
        netlistRepository.setLibrary(updated)
        netlistRepository.setActiveId(item.id)
        viewModelScope.launch {
            try {
                netlistStore.saveLibrary(netlistRepository.library.value)
                netlistStore.setActiveId(item.id)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to save circuit", e)
            }
        }
        return item
    }

    fun saveActive(): Boolean {
        val id = netlistRepository.activeId.value ?: return false
        val current = netlistRepository.library.value
        val existing = current.firstOrNull { it.id == id } ?: return false
        val updatedItem = existing.copy(
            netlist = repository.netlistText.value,
            updatedAtMillis = System.currentTimeMillis()
        )
        val updated = current.map { if (it.id == id) updatedItem else it }
        netlistRepository.setLibrary(updated)
        viewModelScope.launch {
            try {
                netlistStore.saveLibrary(netlistRepository.library.value)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to overwrite circuit", e)
            }
        }
        return true
    }

    fun loadCircuit(id: String): Boolean {
        val item = netlistRepository.library.value.firstOrNull { it.id == id } ?: return false
        updateNetlist(item.netlist, isDiscreteAction = true)
        netlistRepository.setActiveId(id)
        viewModelScope.launch {
            try {
                netlistStore.setActiveId(id)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to set active circuit", e)
            }
        }
        return true
    }

    fun renameCircuit(id: String, newTitle: String): Boolean {
        val clean = newTitle.trim().ifEmpty { return false }
        val current = netlistRepository.library.value
        if (current.none { it.id == id }) return false
        netlistRepository.setLibrary(
            current.map {
                if (it.id == id) it.copy(title = clean, updatedAtMillis = System.currentTimeMillis())
                else it
            }
        )
        viewModelScope.launch {
            try {
                netlistStore.saveLibrary(netlistRepository.library.value)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to rename circuit", e)
            }
        }
        return true
    }

    fun deleteCircuit(id: String): SavedNetlist? {
        val current = netlistRepository.library.value
        val removed = current.firstOrNull { it.id == id } ?: return null
        netlistRepository.setLibrary(current.filterNot { it.id == id })
        if (netlistRepository.activeId.value == id) {
            netlistRepository.setActiveId(null)
        }
        viewModelScope.launch {
            try {
                netlistStore.saveLibrary(netlistRepository.library.value)
                if (netlistRepository.activeId.value == null) netlistStore.setActiveId(null)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to delete circuit", e)
            }
        }
        return removed
    }

    fun restoreCircuit(item: SavedNetlist) {
        netlistRepository.setLibrary(listOf(item) + netlistRepository.library.value)
        viewModelScope.launch {
            try {
                netlistStore.saveLibrary(netlistRepository.library.value)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to restore circuit", e)
            }
        }
    }

    fun newBlank() {
        updateNetlist("* New Circuit\n\n.end\n", isDiscreteAction = true)
        netlistRepository.setActiveId(null)
        viewModelScope.launch {
            try {
                netlistStore.setActiveId(null)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to clear active circuit", e)
            }
        }
    }

    // ---- File open + recents (max 5) ----

    /** Load netlist text from a document Uri. Returns display name or null on failure. */
    suspend fun openNetlistFile(uri: android.net.Uri): String? {
        return try {
            val context = getApplication<Application>()
            val name = queryDisplayName(context, uri) ?: "netlist.cir"
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader().use { reader ->
                    val sb = StringBuilder()
                    val buf = CharArray(8192)
                    var total = 0
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FILE_CHARS) break
                        sb.append(buf, 0, n)
                    }
                    sb.toString()
                }
                if (text.isBlank()) return null
                updateNetlist(text, isDiscreteAction = true)
                netlistRepository.setActiveId(null)
                // Oreo fallback: some providers ignore persistable grants, so keep a
                // cache copy so recents survive reboot even without persist permission.
                cacheImportCopy(context, uri, name, text)
                trackRecentFile(uri.toString(), name)
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { /* SAF picker without persist flag — ignore */ }
                name
            }
        } catch (e: Exception) {
            Log.e("SimulationViewModel", "Failed to open netlist file", e)
            null
        }
    }

    /** Re-open a recent file Uri. Returns display name or null (e.g. file moved). */
    suspend fun openRecentFile(uriString: String): String? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            val opened = openNetlistFile(uri)
            if (opened == null) {
                // Uri grant gone (common on API 26-27) — try the cache copy.
                val cached = readImportCopy(getApplication(), uriString)
                if (cached != null) {
                    updateNetlist(cached.first)
                    netlistRepository.setActiveId(null)
                    trackRecentFile(uriString, cached.second)
                    return cached.second
                }
                // Drop stale entry so the menu doesn't fill with dead files.
                val pruned = netlistRepository.recentFiles.value.filterNot { it.uri == uriString }
                netlistRepository.setRecentFiles(pruned)
                try {
                    netlistStore.saveRecentFiles(pruned)
                } catch (e: Exception) {
                    Log.e("SimulationViewModel", "Failed to prune stale recent", e)
                }
            }
            opened
        } catch (e: Exception) {
            Log.e("SimulationViewModel", "Failed to open recent file", e)
            null
        }
    }

    fun clearRecentFiles() {
        netlistRepository.setRecentFiles(emptyList())
        viewModelScope.launch {
            try {
                netlistStore.saveRecentFiles(emptyList())
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to clear recents", e)
            }
        }
    }

    private fun trackRecentFile(uriString: String, name: String) {
        val entry = RecentFile(uri = uriString, name = name)
        val updated = listOf(entry) + netlistRepository.recentFiles.value.filterNot { it.uri == uriString }
        netlistRepository.setRecentFiles(updated)
        viewModelScope.launch {
            try {
                netlistStore.saveRecentFiles(netlistRepository.recentFiles.value)
            } catch (e: Exception) {
                Log.e("SimulationViewModel", "Failed to save recents", e)
            }
        }
    }

    private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheImportCopy(context: android.content.Context, uri: android.net.Uri, name: String, text: String) {
        try {
            val dir = java.io.File(context.cacheDir, "imports").apply { mkdirs() }
            val safe = uri.toString().hashCode().toString().replace("-", "m")
            java.io.File(dir, "$safe.cir").writeText(text)
            java.io.File(dir, "$safe.name").writeText(name)
        } catch (e: Exception) {
            Log.e("SimulationViewModel", "Failed to cache import", e)
        }
    }

    private fun readImportCopy(context: android.content.Context, uriString: String): Pair<String, String>? {
        return try {
            val dir = java.io.File(context.cacheDir, "imports")
            val safe = uriString.hashCode().toString().replace("-", "m")
            val textFile = java.io.File(dir, "$safe.cir")
            val nameFile = java.io.File(dir, "$safe.name")
            if (!textFile.exists()) return null
            val text = textFile.readText()
            if (text.isBlank()) return null
            val name = if (nameFile.exists()) nameFile.readText().ifBlank { "netlist.cir" } else "netlist.cir"
            text to name
        } catch (_: Exception) {
            null
        }
    }

    fun runSimulation() {
        val app = getApplication<Application>()
        // Foreground priority so Oreo doesn't kill long runs when backgrounded.
        com.jnd.ngdroid.engine.SimulationService.ProgressHook.current = {
            val s = repository.state.value
            if (s.isSimulating || s.isPaused) s.statusText else null
        }
        try {
            com.jnd.ngdroid.engine.SimulationService.start(app, activeTitle())
        } catch (e: Exception) {
            Log.e("SimulationViewModel", "Failed to start simulation service", e)
        }
        runSimulationUseCase()
        // Stop the service when the run settles (complete/halt/error).
        // Single collector for the VM lifetime; guarded so repeat runs don't stack collectors.
        if (!serviceWatcherStarted) {
            serviceWatcherStarted = true
            viewModelScope.launch {
                var wasActive = false
                repository.state.collect { s ->
                    val active = s.isSimulating || s.isPaused
                    if (active) wasActive = true
                    if (wasActive && !active) {
                        wasActive = false
                        try {
                            com.jnd.ngdroid.engine.SimulationService.stop(app)
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    private fun activeTitle(): String {
        val id = netlistRepository.activeId.value
        return netlistRepository.library.value.firstOrNull { it.id == id }?.title.orEmpty()
    }

    fun haltSimulation() {
        repository.haltSimulation()
        try {
            com.jnd.ngdroid.engine.SimulationService.stop(getApplication())
        } catch (_: Exception) { }
    }

    /** Abandon the run (no Resume offered); also stops the service. */
    fun stopSimulation() {
        repository.stopSimulation()
        try {
            com.jnd.ngdroid.engine.SimulationService.stop(getApplication())
        } catch (_: Exception) { }
    }
    fun resumeSimulation() = repository.resumeSimulation()
    fun toggleVectorActive(vecName: String) = repository.toggleVectorActive(vecName)
    fun clearLogs() = repository.clearLogs()
    fun restoreLogs(logs: List<String>) = repository.restoreLogs(logs)

    // ---- Math channel (fx): expression only (VM-scoped, survives tabs);
    // PlotScreen evaluates lazily per plot; toggling visibility keeps it.
    var mathExpr: String? by mutableStateOf(null)
    var mathVisible: Boolean by mutableStateOf(true)

    fun applyMathExpr(expr: String?) {
        mathExpr = expr?.trim()?.takeIf { it.isNotEmpty() }
        mathVisible = true
    }

    fun toggleMathVisibility() {
        mathVisible = !mathVisible
    }

    fun clearMath() {
        mathExpr = null
        mathVisible = true
    }

    /**
     * Agent path: optionally apply [netlist], run, wait until settled
     * (or [timeoutMs]), then return status/logs/vector summary via
     * [com.jnd.ngdroid.agent.formatSimulationReport] for explain/debug.
     * Must be called from a background dispatcher (tool worker thread).
     */
    suspend fun runAndReport(netlist: String?, timeoutMs: Long = 30_000): String {
        return try {
            if (!netlist.isNullOrBlank()) updateNetlist(netlist)
            runSimulation()
            val timeout = timeoutMs.coerceIn(1_000, 120_000)
            val deadline = android.os.SystemClock.elapsedRealtime() + timeout
            // Give runSimulation()'s IO coroutine a moment to flip isSimulating on.
            kotlinx.coroutines.delay(300)
            var waited = 0L
            // A paused run is still live: waiting on isSimulating alone
            // returns a partial report the moment the user pauses.
            while ((repository.state.value.isSimulating || repository.state.value.isPaused) &&
                android.os.SystemClock.elapsedRealtime() < deadline
            ) {
                kotlinx.coroutines.delay(200)
                waited += 200
                // Cap log spam: state polling is cheap (StateFlow read).
                if (waited > timeout) break
            }
            snapshotReport()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /** Sync snapshot of current sim state (status/logs/vectors+samples). Pure read. */
    fun snapshotReport(): String {
        val s = repository.state.value
        val plot = s.currentPlot
        val vectors = mutableListOf<String>()
        val series = linkedMapOf<String, List<Double>>()
        // Scale (time/freq ramp) stays summary-only; sampling it wastes tokens.
        plot?.scaleVector?.let { vectors.add(summarizeVector(it.name, it.values)) }
        plot?.dataVectors?.forEach {
            vectors.add(summarizeVector(it.name, it.values))
            if (it.values.isNotEmpty()) series[it.name] = it.values
        }
        return com.jnd.ngdroid.agent.formatSimulationReport(
            statusText = s.statusText,
            hasError = s.hasError,
            errorMessage = s.errorMessage,
            logs = s.logs,
            vectors = vectors,
            series = series
        )
    }

    fun currentNetlistText(): String = repository.netlistText.value

    /**
     * Agent vision path: render current plot as a small JPEG thumbnail
     * (≤768px wide, q70) for shape checking. Numbers still come from
     * snapshotReport(); this image is pixels-only backup for ambiguous shapes.
     * Must be called from a background dispatcher (tool worker thread).
     */
    suspend fun renderPlotThumbnail(requested: List<String>?): com.jnd.ngdroid.agent.ToolResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val s = repository.state.value
            val plot = s.currentPlot
                ?: return@withContext com.jnd.ngdroid.agent.ToolResult("ERROR: no plot yet — run simulation first")
            val available = plot.dataVectors.map { it.name }.toSet()
            val active = when {
                requested.isNullOrEmpty() -> s.activeVectors.filter { it in available }.toSet()
                else -> requested.filter { it in available }.take(6).toSet()
            }.ifEmpty { s.activeVectors.filter { it in available }.toSet() }
            if (active.isEmpty()) {
                return@withContext com.jnd.ngdroid.agent.ToolResult(
                    "ERROR: no active vectors (available: ${available.take(8).joinToString(", ")})"
                )
            }
            val bmp = try {
                com.jnd.ngdroid.engine.PlotExporter.renderPlotBitmap(
                    plot, active, com.jnd.ngdroid.data.AppSettings()
                )
            } catch (e: Exception) {
                return@withContext com.jnd.ngdroid.agent.ToolResult("ERROR: ${e.message}")
            } ?: return@withContext com.jnd.ngdroid.agent.ToolResult("ERROR: plot render failed")
            try {
                val scaled = if (bmp.width > 768) {
                    val h = (bmp.height * 768f / bmp.width).toInt().coerceAtLeast(1)
                    android.graphics.Bitmap.createScaledBitmap(bmp, 768, h, true)
                } else bmp
                val out = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                if (scaled !== bmp) {
                    scaled.recycle()
                    bmp.recycle()
                } else {
                    bmp.recycle()
                }
                val bytes = out.toByteArray()
                if (bytes.isEmpty() || bytes.size > 400_000) {
                    return@withContext com.jnd.ngdroid.agent.ToolResult("ERROR: thumbnail encode failed")
                }
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                com.jnd.ngdroid.agent.ToolResult(
                    "plot: ${active.sorted().joinToString(", ")} (${bytes.size / 1024}KB jpeg)",
                    listOf(com.jnd.ngdroid.agent.LlmImage("image/jpeg", b64, "plot.jpg"))
                )
            } catch (e: Exception) {
                com.jnd.ngdroid.agent.ToolResult("ERROR: ${e.message}")
            }
        }
}

/** One-line per-vector summary: name + points + min/max/last. Pure. */
internal fun summarizeVector(name: String, values: List<Double>): String {
    if (values.isEmpty()) return "$name: 0 points"
    var min = Double.POSITIVE_INFINITY
    var max = Double.NEGATIVE_INFINITY
    for (v in values) {
        if (v.isNaN()) continue
        if (v < min) min = v
        if (v > max) max = v
    }
    if (min == Double.POSITIVE_INFINITY) min = Double.NaN
    if (max == Double.NEGATIVE_INFINITY) max = Double.NaN
    return "$name: ${values.size} pts min=${formatSample(min)} max=${formatSample(max)} last=${formatSample(values.last())}"
}
