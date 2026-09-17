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

package com.jnd.ngdroid.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

data class SpiceColors(
    val base: Color,
    val comment: Color,
    val directive: Color,
    val component: Color,
    val number: Color
)

private val DirectiveRegex = Regex("""\.[A-Za-z]+\b""")
private val NumberRegex = Regex("""\b\d+(\.\d+)?([eE][+-]?\d+)?(Meg|mil|[kKuUnNpPfFmMgGtT])?\b""")
private val ComponentLineRegex = Regex("""^([A-Za-z][\w.]*)\s+""")

private val ComponentLeadChars = setOf(
    'R', 'C', 'L', 'V', 'I', 'D', 'Q', 'M', 'X', 'B',
    'E', 'F', 'G', 'H', 'J', 'K', 'O', 'S', 'T', 'U', 'W', 'Y', 'Z', 'A'
)

fun highlightNetlist(
    text: String,
    colors: SpiceColors
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        append(text)
        val lines = text.lines()
        var offset = 0
        lines.forEachIndexed { _, line ->
            val lineStart = offset
            val lineEnd = offset + line.length
            val trimmed = line.trimStart()

            if (trimmed.startsWith("*") || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                addStyle(
                    SpanStyle(color = colors.comment, fontWeight = FontWeight.Normal),
                    lineStart, lineEnd
                )
            } else {
                val semiIdx = line.indexOf(';')
                val codeEnd = if (semiIdx >= 0) lineStart + semiIdx else lineEnd
                if (semiIdx >= 0) {
                    addStyle(
                        SpanStyle(color = colors.comment),
                        lineStart + semiIdx, lineEnd
                    )
                }

                DirectiveRegex.findAll(line).forEach { match ->
                    val s = lineStart + match.range.first
                    val e = lineStart + match.range.last + 1
                    if (e <= codeEnd) {
                        addStyle(
                            SpanStyle(
                                color = colors.directive,
                                fontWeight = FontWeight.Bold
                            ),
                            s, e
                        )
                    }
                }

                val compMatch = ComponentLineRegex.find(line)
                if (compMatch != null) {
                    val token = compMatch.groupValues[1]
                    if (token.isNotEmpty() && token[0].uppercaseChar() in ComponentLeadChars) {
                        val s = lineStart + compMatch.range.first +
                            (line.substring(compMatch.range).indexOf(token))
                        addStyle(
                            SpanStyle(
                                color = colors.component,
                                fontWeight = FontWeight.SemiBold
                            ),
                            s, s + token.length
                        )
                    }
                }

                NumberRegex.findAll(line).forEach { match ->
                    val s = lineStart + match.range.first
                    val e = lineStart + match.range.last + 1
                    if (s >= codeEnd) return@forEach
                    // Don't override directive dot-numbers; skip if inside directive span is complex —
                    // numbers get their own color, directives stay bold via overlap order (number first, directive re-applied).
                    addStyle(SpanStyle(color = colors.number), s, minOf(e, codeEnd))
                }

                // Re-apply directive bold on top so it wins over number coloring inside e.g. ".tran".
                DirectiveRegex.findAll(line).forEach { match ->
                    val s = lineStart + match.range.first
                    val e = lineStart + match.range.last + 1
                    if (e <= codeEnd) {
                        addStyle(
                            SpanStyle(
                                color = colors.directive,
                                fontWeight = FontWeight.Bold
                            ),
                            s, e
                        )
                    }
                }
            }

            offset = lineEnd + 1 // +1 for '\n'
        }
        // Base color for everything not otherwise styled is supplied via textStyle,
        // spans only override where meaningful.
    }
}
