package com.jnd.ngdroid.ui.editor

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnd.ngdroid.data.AutosaveStatus
import com.jnd.ngdroid.data.PresetNetlists
import com.jnd.ngdroid.data.SavedNetlist
import com.jnd.ngdroid.data.SettingsRepository
import com.jnd.ngdroid.ui.SimulationViewModel
import com.jnd.ngdroid.ui.theme.LocalButtonShape
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetlistEditorScreen(
    viewModel: SimulationViewModel,
    settingsRepository: SettingsRepository,
    onNavigateToPlot: () -> Unit
) {
    val repository = viewModel.repository
    val netlist by repository.netlistText.collectAsState()
    val state by repository.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val library by viewModel.netlistRepository.library.collectAsState()
    val activeId by viewModel.netlistRepository.activeId.collectAsState()
    val autosave by viewModel.netlistRepository.autosave.collectAsState()
    val recentFiles by viewModel.netlistRepository.recentFiles.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // System file picker for .cir/.net/.txt netlists.
    val openFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    val name = viewModel.openNetlistFile(uri)
                    if (name != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        snackbarHostState.showSnackbar("Opened $name")
                    } else {
                        snackbarHostState.showSnackbar("Could not open file (empty or unreadable)")
                    }
                }
            }
        }
    )

    var showPresetMenu by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveIsOverwrite by remember { mutableStateOf(true) }
    var renameTarget by remember { mutableStateOf<SavedNetlist?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedNetlist?>(null) }
    var pendingLoad by remember { mutableStateOf<SavedNetlist?>(null) }
    var wrapText by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    fun doPaste() {
        val clipText = clipboardManager.getText()?.text
        if (!clipText.isNullOrEmpty()) {
            val previous = repository.netlistText.value
            viewModel.updateNetlist(clipText)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Pasted netlist from clipboard",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.updateNetlist(previous)
                }
            }
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    val activeCircuit = library.firstOrNull { it.id == activeId }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val quickDirectives = listOf(
        ".tran 10u 10m" to "\n.tran 10u 10m\n",
        ".ac dec 20 10 1Meg" to "\n.ac dec 20 10 1Meg\n",
        ".op" to "\n.op\n",
        "Resistor R" to "\nR1 in out 1k",
        "Capacitor C" to "\nC1 out 0 1u",
        "Inductor L" to "\nL1 out 0 10m",
        "Voltage V" to "\nV1 in 0 5V",
        "Pulse V" to "\nV1 in 0 PULSE(0 5 0 1u 1u 5m 10m)",
        "Sine V" to "\nV1 in 0 SIN(0 10 50)"
    )

    fun loadWithDirtyCheck(item: SavedNetlist) {
        if (autosave.status == AutosaveStatus.DIRTY) {
            pendingLoad = item
        } else {
            viewModel.loadCircuit(item.id)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { snackbarHostState.showSnackbar("Loaded ${item.title}") }
        }
    }

    if (showSaveDialog) {
        SaveCircuitDialog(
            initialTitle = activeCircuit?.title ?: "",
            isOverwrite = saveIsOverwrite && activeCircuit != null,
            onDismiss = { showSaveDialog = false },
            onConfirm = { title ->
                showSaveDialog = false
                if (saveIsOverwrite && activeCircuit != null) {
                    viewModel.saveActive()
                    if (title != activeCircuit.title) viewModel.renameCircuit(activeCircuit.id, title)
                    scope.launch { snackbarHostState.showSnackbar("Saved ${title.ifEmpty { activeCircuit.title }}") }
                } else {
                    val created = viewModel.saveAsNew(title)
                    scope.launch {
                        snackbarHostState.showSnackbar("Saved ${created?.title ?: title}")
                    }
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )
    }

    renameTarget?.let { target ->
        SaveCircuitDialog(
            initialTitle = target.title,
            isOverwrite = true,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                renameTarget = null
                viewModel.renameCircuit(target.id, title)
            }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete circuit?",
            message = "Delete \"${target.title}\"? You can undo right after.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                val removed = viewModel.deleteCircuit(target.id)
                if (removed != null) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Deleted ${removed.title}",
                            actionLabel = "UNDO",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreCircuit(removed)
                        }
                    }
                }
            }
        )
    }

    pendingLoad?.let { target ->
        ConfirmDialog(
            title = "Load circuit?",
            message = "You have unsaved edits. Loading \"${target.title}\" replaces the current draft (autosave will catch up after load).",
            confirmLabel = "Load",
            onDismiss = { pendingLoad = null },
            onConfirm = {
                pendingLoad = null
                viewModel.loadCircuit(target.id)
            }
        )
    }

    if (showPresetMenu) {
        ExamplesPickerDialog(
            presets = PresetNetlists.items,
            onDismiss = { showPresetMenu = false },
            onSelectPreset = { preset ->
                val previous = netlist
                viewModel.updateNetlist(preset.netlist)
                showPresetMenu = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Loaded ${preset.title}",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.updateNetlist(previous)
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isSimulating || state.isPaused) {
                // Live run: both controls side by side — Pause keeps Resume
                // available, Stop abandons the run entirely.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isPaused) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.resumeSimulation()
                            },
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Resume") },
                            text = { Text("Resume") }
                        )
                    } else {
                        ExtendedFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.haltSimulation()
                            },
                            icon = { Icon(Icons.Default.Pause, contentDescription = "Pause") },
                            text = { Text("Pause") }
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.stopSimulation()
                        },
                        icon = { Icon(Icons.Default.Stop, contentDescription = "Stop") },
                        text = { Text("Stop") },
                        containerColor = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Via the ViewModel so the foreground service keeps
                        // long runs alive when the app is backgrounded.
                        viewModel.runSimulation()
                        onNavigateToPlot()
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Run") },
                    text = { Text("Run SPICE") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: title row (title + save + more) + actions row (library + presets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Netlist Editor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeCircuit?.title ?: "Unsaved draft",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Undo button
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo edit",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Redo button
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = canRedo,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo edit",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Save button (overwrite if active, else save-as)
                IconButton(
                    onClick = {
                        if (activeCircuit != null) {
                            viewModel.saveActive()
                            scope.launch {
                                snackbarHostState.showSnackbar("Saved ${activeCircuit.title}")
                            }
                        } else {
                            saveIsOverwrite = false
                            showSaveDialog = true
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save circuit")
                }

                com.jnd.ngdroid.ui.console.OpTableButton(
                    repository = repository,
                    modifier = Modifier.size(48.dp)
                )

                // More menu: open file, recents, wrap, paste, history, new, save-as
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More editor actions")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open file…") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                try {
                                    openFileLauncher.launch(
                                        arrayOf("text/*", "application/octet-stream", "*/*")
                                    )
                                } catch (_: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No file picker found on this device")
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open Examples & Templates") },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                            onClick = { showMoreMenu = false; showPresetMenu = true }
                        )
                        if (recentFiles.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = "Recent files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            recentFiles.take(5).forEach { recent ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                recent.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = timeFmt.format(Date(recent.openedAtMillis)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch {
                                            val opened = viewModel.openRecentFile(recent.uri)
                                            if (opened != null) {
                                                snackbarHostState.showSnackbar("Opened $opened")
                                            } else {
                                                snackbarHostState.showSnackbar("File no longer available")
                                            }
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Clear recent files") },
                                onClick = { showMoreMenu = false; viewModel.clearRecentFiles() }
                            )
                            HorizontalDivider()
                        } else {
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(if (wrapText) "Disable line wrap" else "Enable line wrap") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = null) },
                            onClick = { wrapText = !wrapText; showMoreMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Paste from clipboard") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            onClick = { showMoreMenu = false; doPaste() }
                        )
                        DropdownMenuItem(
                            text = { Text("Simulation history") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "History lives on the Data tab • ${state.plotHistory.size} runs kept"
                                    )
                                }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("New blank circuit") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = { showMoreMenu = false; viewModel.newBlank() }
                        )
                        DropdownMenuItem(
                            text = { Text("Save current as…") },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            onClick = { showMoreMenu = false; saveIsOverwrite = false; showSaveDialog = true }
                        )
                    }
                }
            }

            // Actions row: library (expands) + presets (fixed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Library Dropdown
                Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showLibraryMenu = true },
                            shape = LocalButtonShape.current,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.LibraryBooks,
                                contentDescription = "My Circuits",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "My Circuits (${library.size})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        DropdownMenu(
                            expanded = showLibraryMenu,
                            onDismissRequest = { showLibraryMenu = false },
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("New blank circuit") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showLibraryMenu = false
                                    viewModel.newBlank()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Examples & Templates") },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                                onClick = {
                                    showLibraryMenu = false
                                    showPresetMenu = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save current as…") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = {
                                    showLibraryMenu = false
                                    saveIsOverwrite = false
                                    showSaveDialog = true
                                }
                            )
                            if (library.isNotEmpty()) {
                                HorizontalDivider()
                                library.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    item.title,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "${item.netlist.lines().size} lines • " +
                                                        timeFmt.format(Date(item.updatedAtMillis)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Row {
                                                IconButton(
                                                    onClick = { renameTarget = item },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DriveFileRenameOutline,
                                                        contentDescription = "Rename ${item.title}"
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { deleteTarget = item },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete ${item.title}"
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            showLibraryMenu = false
                                            loadWithDirtyCheck(item)
                                        }
                                    )
                                }
                            } else {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("No saved circuits yet") },
                                    enabled = false,
                                    onClick = {}
                                )
                            }
                        }
                    }

                    // Presets / Examples Button
                    OutlinedButton(
                        onClick = { showPresetMenu = true },
                        shape = LocalButtonShape.current
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = "Examples and Templates",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Examples")
                    }
            }

            // Quick Insertion Directive Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickDirectives.forEach { (label, snippet) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            viewModel.updateNetlist(netlist + snippet)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Error Alert Banner
            if (state.hasError && !state.errorMessage.isNullOrEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = "Error: ${state.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Status Banner & Progress Indicator
            // Elapsed ticker for the live run (120 s engine timeout).
            var runElapsedSec by remember { mutableLongStateOf(0L) }
            androidx.compose.runtime.LaunchedEffect(state.isSimulating) {
                if (state.isSimulating) {
                    val start = android.os.SystemClock.elapsedRealtime()
                    runElapsedSec = 0L
                    while (repository.state.value.isSimulating) {
                        kotlinx.coroutines.delay(1000)
                        runElapsedSec = (android.os.SystemClock.elapsedRealtime() - start) / 1000
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusLine = if (state.isPaused) {
                            "Status: ${state.statusText} — Resume available"
                        } else {
                            "Status: ${state.statusText}"
                        }
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isPaused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val autosaveLabel = when (autosave.status) {
                            AutosaveStatus.SAVING -> "Saving…"
                            AutosaveStatus.DIRTY -> "Unsaved changes"
                            AutosaveStatus.SAVED -> autosave.lastSavedAtMillis?.let {
                                "Autosaved • ${timeFmt.format(Date(it))}"
                            } ?: "Autosaved"
                        }
                        val timeoutSec = com.jnd.ngdroid.engine.SimulationRepository.RUN_TIMEOUT_MS / 1000
                        if (state.isSimulating) {
                            Text(
                                text = "${runElapsedSec}s / ${timeoutSec}s • $autosaveLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (state.totalPointCount > 0) {
                            Text(
                                text = "${state.totalPointCount} pts • $autosaveLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = autosaveLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (state.isSimulating) {
                    if (state.progressFraction != null) {
                        LinearProgressIndicator(
                            progress = { state.progressFraction!! },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else if (state.isPaused) {
                    LinearProgressIndicator(
                        progress = { state.progressFraction ?: 0.5f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Code Editor Box with highlighting, line numbers, wrap toggle, paste
            val spiceColors = SpiceColors(
                base = MaterialTheme.colorScheme.onSurface,
                comment = MaterialTheme.colorScheme.onSurfaceVariant,
                directive = MaterialTheme.colorScheme.primary,
                component = MaterialTheme.colorScheme.tertiary,
                number = MaterialTheme.colorScheme.secondary
            )
            // Debounce the 4-pass regex highlight: typing updates the plain
            // text immediately, the expensive annotate runs 250 ms after the
            // last keystroke instead of on every one.
            var debouncedNetlist by remember { mutableStateOf(netlist) }
            androidx.compose.runtime.LaunchedEffect(netlist) {
                kotlinx.coroutines.delay(250)
                debouncedNetlist = netlist
            }
            val highlighted = remember(debouncedNetlist, spiceColors) {
                highlightNetlist(debouncedNetlist, spiceColors)
            }
            // Keep editing state independent so the cursor doesn't jump to the end
            // on every keystroke; only resync when the underlying netlist text
            // actually changed externally (typing, preset/library load, paste).
            var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
            androidx.compose.runtime.LaunchedEffect(netlist, highlighted, debouncedNetlist) {
                if (fieldValue.text != netlist) {
                    val sel = fieldValue.selection
                    val newSel = if (fieldValue.text.isEmpty()) {
                        TextRange(netlist.length)
                    } else {
                        TextRange(
                            sel.start.coerceIn(0, netlist.length),
                            sel.end.coerceIn(0, netlist.length)
                        )
                    }
                    // External load/paste: highlight synchronously so the fresh
                    // text never flashes stale spans; typing path keeps the
                    // plain typed text until the debounce catches up.
                    val fresh = if (debouncedNetlist == netlist) highlighted
                    else highlightNetlist(netlist, spiceColors)
                    fieldValue = TextFieldValue(fresh, newSel)
                } else if (debouncedNetlist == netlist && fieldValue.annotatedString != highlighted) {
                    fieldValue = fieldValue.copy(annotatedString = highlighted)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Shared vertical scroll state: editor text and line-number
                // gutter scroll together so lines stay aligned.
                val vScroll = rememberScrollState()
                if (settings.showLineNumbers) {
                    val lineCount = maxOf(1, netlist.lines().size)
                    val lineNumbersText = (1..lineCount).joinToString("\n")

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = lineNumbersText,
                            style = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = settings.editorFontSizeSp.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 16.dp)
                                .verticalScroll(vScroll)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = null,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val scroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vScroll)
                                .let { m ->
                                    if (wrapText) m
                                    else m.horizontalScroll(scroll)
                                }
                                .padding(16.dp)
                        ) {
                            BasicTextField(
                                value = fieldValue,
                                onValueChange = { next ->
                                    fieldValue = next
                                    if (next.text != netlist) {
                                        viewModel.updateNetlist(next.text)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = settings.editorFontSizeSp.sp,
                                    color = spiceColors.base
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                            )
                            if (netlist.isEmpty()) {
                                Text(
                                    text = "* Enter SPICE netlist here...",
                                    style = LocalTextStyle.current.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = settings.editorFontSizeSp.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.6f
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
