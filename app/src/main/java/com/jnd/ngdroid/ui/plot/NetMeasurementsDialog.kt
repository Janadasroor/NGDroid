/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid.ui.plot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.jnd.ngdroid.engine.NetMeasurements
import com.jnd.ngdroid.ui.theme.LocalButtonShape
import com.jnd.ngdroid.ui.theme.LocalDialogShape
import com.jnd.ngdroid.ui.util.LockOrientationWhileShown
import com.jnd.ngdroid.ui.util.LockOrientationWhileShown
import java.util.Locale
import kotlin.math.abs

@Composable
fun NetMeasurementsDialog(
    measurements: NetMeasurements,
    onDismiss: () -> Unit
) {
    LockOrientationWhileShown()
    val isCurrent = measurements.netName.endsWith("#branch") || measurements.netName.startsWith("i(") || measurements.netName.startsWith("I(")
    val unitStr = if (isCurrent) "A" else "V"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = LocalDialogShape.current,
        title = {
            Column {
                Text(
                    text = "Waveform Measurements",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Net: ${measurements.netName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (measurements.rangeLabel != null) {
                    Text(
                        text = measurements.rangeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider()

                MeasurementRow("RMS Value:", formatEng(measurements.rmsVal, unitStr))
                MeasurementRow("Average (DC):", formatEng(measurements.avgVal, unitStr))
                MeasurementRow("Peak-to-Peak:", formatEng(measurements.peakToPeak, unitStr))
                MeasurementRow("Minimum:", formatEng(measurements.minVal, unitStr))
                MeasurementRow("Maximum:", formatEng(measurements.maxVal, unitStr))

                if (measurements.estimatedFreqHz != null && measurements.estimatedPeriod != null) {
                    HorizontalDivider()
                    MeasurementRow("Frequency:", formatEng(measurements.estimatedFreqHz, "Hz"))
                    MeasurementRow("Period:", formatEng(measurements.estimatedPeriod, "s"))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Total Samples: ${measurements.count}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = LocalButtonShape.current
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun MeasurementRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatEng(valVal: Double, unit: String): String {
    val absV = abs(valVal)
    return when {
        absV == 0.0 -> String.format(Locale.US, "0.000 %s", unit)
        absV >= 1e6 -> String.format(Locale.US, "%.3f M%s", valVal / 1e6, unit)
        absV >= 1e3 -> String.format(Locale.US, "%.3f k%s", valVal / 1e3, unit)
        absV >= 1.0 -> String.format(Locale.US, "%.3f %s", valVal, unit)
        absV >= 1e-3 -> String.format(Locale.US, "%.3f m%s", valVal * 1e3, unit)
        absV >= 1e-6 -> String.format(Locale.US, "%.3f µ%s", valVal * 1e6, unit)
        absV >= 1e-9 -> String.format(Locale.US, "%.3f n%s", valVal * 1e9, unit)
        else -> String.format(Locale.US, "%.3e %s", valVal, unit)
    }
}
