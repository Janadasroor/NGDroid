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

package com.jnd.ngdroid.ui.assistant

/**
 * Splits a markdown document into prose and display-math segments.
 *
 * Display math delimiters: `$$…$$` and `\[…\]`. Inline math (`$…$`, `\(…\)`)
 * stays inside [DocSegment.Prose] and is prettified later by
 * [prettifyInlineMath], so markdown structure (lists, tables) is preserved.
 *
 * Fenced code blocks (```…```) and inline code (`…`) are never treated as math.
 */
sealed interface DocSegment {
    data class Prose(val text: String) : DocSegment
    data class DisplayMath(val latex: String) : DocSegment
}

/** True when [markdown] contains a display-math block outside code. */
fun hasDisplayMath(markdown: String): Boolean =
    parseDocSegments(markdown).any { it is DocSegment.DisplayMath }

/** True when [markdown] contains inline or display math outside code. */
fun hasAnyMath(markdown: String): Boolean {
    if (hasDisplayMath(markdown)) return true
    // Inline \(…\) outside code is always math.
    val code = codeRanges(markdown)
    var idx = 0
    while (true) {
        val open = markdown.indexOf("\\(", idx)
        if (open < 0) break
        if (code.none { open in it }) return true
        idx = open + 2
    }
    return findInlineMath(markdown, code) != null
}

fun parseDocSegments(markdown: String): List<DocSegment> {
    if (markdown.isEmpty()) return emptyList()
    val code = codeRanges(markdown)
    val out = mutableListOf<DocSegment>()
    val prose = StringBuilder()
    fun flushProse() {
        if (prose.isNotEmpty()) {
            out.add(DocSegment.Prose(prose.toString()))
            prose.clear()
        }
    }
    var i = 0
    val n = markdown.length
    while (i < n) {
        val codeHit = code.firstOrNull { i in it }
        if (codeHit != null) {
            prose.append(markdown, codeHit.first, codeHit.last + 1)
            i = codeHit.last + 1
            continue
        }
        when {
            markdown.startsWith("$$", i) -> {
                val close = markdown.indexOf("$$", i + 2)
                if (close < 0) {
                    prose.append(markdown.substring(i))
                    i = n
                } else {
                    flushProse()
                    out.add(DocSegment.DisplayMath(markdown.substring(i + 2, close)))
                    i = close + 2
                }
            }
            markdown.startsWith("\\[", i) -> {
                val close = markdown.indexOf("\\]", i + 2)
                if (close < 0) {
                    prose.append(markdown.substring(i))
                    i = n
                } else {
                    flushProse()
                    out.add(DocSegment.DisplayMath(markdown.substring(i + 2, close)))
                    i = close + 2
                }
            }
            else -> {
                prose.append(markdown[i])
                i++
            }
        }
    }
    flushProse()
    return out
}

/** Ranges (inclusive) of fenced code blocks and inline code spans. */
internal fun codeRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    // Fenced blocks first — inline backticks inside them are literal.
    var idx = 0
    while (true) {
        val start = text.indexOf("```", idx)
        if (start < 0) break
        val end = text.indexOf("```", start + 3)
        if (end < 0) {
            ranges.add(start..text.lastIndex)
            break
        }
        ranges.add(start..end + 2)
        idx = end + 3
    }
    // Inline code spans outside fences.
    var j = 0
    while (j < text.length) {
        if (text[j] == '`' && ranges.none { j in it }) {
            // Skip fence ticks already covered; handle single/double backticks.
            var k = j
            while (k < text.length && text[k] == '`') k++
            val ticks = text.substring(j, k)
            val close = text.indexOf(ticks, k)
            if (close < 0) break
            ranges.add(j..close + ticks.length - 1)
            j = close + ticks.length
        } else {
            j++
        }
    }
    return ranges
}

/** Position of the first inline-math span, or null. Code ranges must be precomputed. */
internal fun findInlineMath(text: String, code: List<IntRange>): IntRange? {
    var i = 0
    // \(…\) form.
    while (true) {
        val open = text.indexOf("\\(", i)
        if (open < 0) break
        if (code.none { open in it }) {
            val close = text.indexOf("\\)", open + 2)
            if (close >= 0 && close - open <= 402) return open..close + 1
            return null
        }
        i = open + 2
    }
    return null
}
