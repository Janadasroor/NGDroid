package com.jnd.ngdroid.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jnd.ngdroid.engine.OperatingPoint
import com.jnd.ngdroid.ui.plot.formatEng

/**
 * DC operating-point table: node voltages (V) and branch currents (A)
 * from the last run's console output. Read-only.
 */
@Composable
fun OperatingPointDialog(
    op: OperatingPoint,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Operating point") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OpRow("Node", "Value", header = true)
                HorizontalDivider()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(op.rows, key = { it.name }) { row ->
                        OpRow(
                            row.name,
                            formatEng(row.value, if (row.isCurrent) "A" else "V")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun OpRow(name: String, value: String, header: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            color = if (header) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            color = if (header) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary
        )
    }
}
