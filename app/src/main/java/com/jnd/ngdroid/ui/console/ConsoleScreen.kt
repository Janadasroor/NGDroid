package com.jnd.ngdroid.ui.console

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnd.ngdroid.engine.SimulationRepository
import kotlinx.coroutines.launch

@Composable
fun ConsoleScreen(repository: SimulationRepository) {
    val state by repository.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Console",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "ngspice Output Logs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val engineLabel = if (com.jnd.ngdroid.engine.NativeNgSpice.isNativeAvailable) {
                            "Engine: ngspice native"
                        } else if (com.jnd.ngdroid.engine.NativeNgSpice.lastInitError != null) {
                            "Engine: built-in (native unavailable)"
                        } else {
                            "Engine: auto (native if available)"
                        }
                        Text(
                            text = engineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    // Operating-point table parsed on click only — not on every
                    // log append — to avoid O(n^2) rescans during a live run.
                    var showOp by remember { mutableStateOf(false) }
                    var opPoint by remember {
                        mutableStateOf<com.jnd.ngdroid.engine.OperatingPoint?>(null)
                    }
                    IconButton(onClick = {
                        opPoint = com.jnd.ngdroid.engine.parseOperatingPoint(state.logs)
                        showOp = opPoint != null
                        if (opPoint == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "No operating point found",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = "Operating point",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showOp && opPoint != null) {
                        OperatingPointDialog(op = opPoint!!, onDismiss = { showOp = false })
                    }

                    // Share Logs Button
                    if (state.logs.isNotEmpty()) {
                        IconButton(onClick = {
                            val logText = state.logs.joinToString("\n")
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NGDroid SPICE Console Logs")
                                putExtra(Intent.EXTRA_TEXT, logText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Engine Logs via"))
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Logs",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Clear Logs Button
                    IconButton(onClick = {
                        val previousLogs = state.logs
                        repository.clearLogs()

                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Cleared engine logs",
                                actionLabel = "UNDO",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                repository.restoreLogs(previousLogs)
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E)
            ) {
                if (state.logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No simulation logs yet. Run a simulation from the Netlist tab.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.logs) { logLine ->
                            val isError = logLine.contains("Error", ignoreCase = true) || logLine.contains("Fatal", ignoreCase = true)
                            val isWarning = logLine.contains("Warning", ignoreCase = true)

                            val textColor = when {
                                isError -> Color(0xFFFF6B6B)
                                isWarning -> Color(0xFFFFD166)
                                logLine.startsWith("---") -> Color(0xFF4EADF8)
                                else -> Color(0xFFE0E0E0)
                            }

                            Text(
                                text = logLine,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}
