package com.jnd.ngdroid.ui.console

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jnd.ngdroid.engine.OperatingPoint
import com.jnd.ngdroid.engine.SimulationRepository
import com.jnd.ngdroid.engine.parseOperatingPoint

/**
 * Shared operating-point entry: parses the last Node/Source table on click
 * (never on every log append) and shows [OperatingPointDialog].
 * Toasts when no table is present yet.
 */
@Composable
fun OpTableButton(
    repository: SimulationRepository,
    modifier: Modifier = Modifier
) {
    val state by repository.state.collectAsState()
    val context = LocalContext.current
    var showOp by remember { mutableStateOf(false) }
    var opPoint by remember { mutableStateOf<OperatingPoint?>(null) }
    IconButton(
        modifier = modifier,
        onClick = {
            opPoint = parseOperatingPoint(state.logs)
            showOp = opPoint != null
            if (opPoint == null) {
                Toast.makeText(context, "No operating point yet", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Icon(
            Icons.Default.TableChart,
            contentDescription = "Operating point",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showOp && opPoint != null) {
        OperatingPointDialog(op = opPoint!!, onDismiss = { showOp = false })
    }
}
