package com.jnd.ngdroid.ui.plot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnd.ngdroid.engine.VectorSeries

/**
 * Math dialog: expression field + net dropdown (inserts at cursor) +
 * function chips + live validation. Apply returns the expression string;
 * evaluation stays lazy in PlotScreen (nothing stored but the string).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MathExprDialog(
    initial: String?,
    vectors: List<VectorSeries>,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var field by remember(initial) { mutableStateOf(TextFieldValue(initial ?: "")) }
    var dropExpanded by remember { mutableStateOf(false) }
    val vecMap = remember(vectors) { vectors.associateBy { it.name } }
    val validation = remember(field.text, vectors) {
        validateMathExpr(field.text, vecMap)
    }

    fun insert(text: String) {
        val sel = field.selection
        val start = sel.start.coerceIn(0, field.text.length)
        val end = sel.end.coerceIn(0, field.text.length)
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        val next = field.text.substring(0, lo) + text + field.text.substring(hi)
        field = TextFieldValue(next, TextRange(lo + text.length))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Math channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Expression") },
                    placeholder = { Text("v(out)-v(in)  ·  v(n)*5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
                // Net picker: inserts the trace name at the cursor.
                ExposedDropdownMenuBox(
                    expanded = dropExpanded,
                    onExpandedChange = { if (vectors.isNotEmpty()) dropExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "Insert trace… (${vectors.size})",
                        onValueChange = {},
                        readOnly = true,
                        enabled = vectors.isNotEmpty(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = dropExpanded,
                        onDismissRequest = { dropExpanded = false }
                    ) {
                        vectors.forEach { v ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        v.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    insert(v.name)
                                    dropExpanded = false
                                }
                            )
                        }
                    }
                }
                // Function chips: insert fn(…) with cursor inside.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("db", "abs", "sqrt", "mag").forEach { fn ->
                        AssistChip(
                            onClick = { insert("$fn()") ; field = moveCursorBack(field, 1) },
                            label = {
                                Text(
                                    "$fn(…)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                    AssistChip(
                        onClick = { insert("*") },
                        label = { Text("×k", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    )
                }
                Text(
                    text = if (field.text.isBlank()) {
                        "Combine traces with + − * / ^ and scalars, e.g. v(n)*5. " +
                            "Suffixes: k M m u n p. Functions: db abs mag sqrt ln log10 exp sin cos."
                    } else {
                        validation.message
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (field.text.isBlank() || validation.ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(field.text.trim()) },
                enabled = validation.ok
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun moveCursorBack(field: TextFieldValue, n: Int): TextFieldValue {
    val pos = (field.selection.end - n).coerceAtLeast(0)
    return field.copy(selection = TextRange(pos))
}
