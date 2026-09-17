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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * Chat-specific markdown styling: compact headings + light/dark variants.
 *
 * The renderer's defaults target full-page docs (H1 = displayLarge ≈ 57sp),
 * which blow out a narrow chat bubble. Chat keeps a tight 22→12sp bold
 * ladder so H1–H6 stay distinct without dominating the response.
 */

// Pure spec so unit tests can verify the ladder without composition.
data class ChatHeadingSpec(val level: Int, val fontSizeSp: Float)

val CHAT_HEADING_SPECS: List<ChatHeadingSpec> = listOf(
    ChatHeadingSpec(1, 22f),
    ChatHeadingSpec(2, 20f),
    ChatHeadingSpec(3, 18f),
    ChatHeadingSpec(4, 16f),
    ChatHeadingSpec(5, 14f),
    ChatHeadingSpec(6, 12f)
)

fun chatHeadingFontSizeSp(level: Int): Float =
    CHAT_HEADING_SPECS.firstOrNull { it.level == level }?.fontSizeSp ?: 14f

data class ChatHeading(val level: Int, val title: String)

/**
 * ATX heading level for a single line, or 0 when not a heading.
 * Accepts `#Title` (no space) since models often omit it; normalization
 * inserts the space before render. Up to 3 leading spaces per CommonMark.
 */
fun headingLevelOf(line: String): Int {
    var i = 0
    while (i < line.length && line[i] == ' ' && i < 4) i++
    if (i >= 4) return 0 // indented code, not a heading
    val hashes = line.drop(i).takeWhile { it == '#' }.length
    if (hashes !in 1..6) return 0
    val rest = line.drop(i + hashes)
    if (rest.isEmpty()) return hashes
    if (rest[0] == ' ' || rest[0] == '\t' || rest[0] == '#') return hashes
    // `#Title` form: still a heading, fixed up by [normalizeMarkdownForChat].
    return hashes
}

/** Headings outside fenced code blocks, in document order. */
fun extractChatHeadings(markdown: String): List<ChatHeading> {
    if (markdown.isEmpty()) return emptyList()
    val out = mutableListOf<ChatHeading>()
    var inFence = false
    for (raw in markdown.lines()) {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("```")) {
            inFence = !inFence
            continue
        }
        if (inFence) continue
        val level = headingLevelOf(raw)
        if (level > 0) {
            val title = raw.trimStart().dropWhile { it == '#' || it == ' ' }.trim()
                .removeSuffix("#").trim()
            out.add(ChatHeading(level, title))
        }
    }
    return out
}

/**
 * Normalizes model markdown for chat bubbles without touching code or math
 * semantics: fixes `#Title` → `# Title`, trims trailing spaces, collapses
 * 3+ blank lines to two. Fenced blocks pass through byte-identical.
 */
fun normalizeMarkdownForChat(markdown: String): String {
    if (markdown.isEmpty()) return markdown
    val lines = markdown.split('\n')
    val out = ArrayList<String>(lines.size)
    var inFence = false
    var blanks = 0
    for (raw in lines) {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("```")) {
            inFence = !inFence
            blanks = 0
            out.add(raw.trimEnd())
            continue
        }
        if (inFence) {
            out.add(raw)
            continue
        }
        if (raw.isBlank()) {
            blanks++
            if (blanks <= 2) out.add("")
            continue
        }
        blanks = 0
        var line = raw.trimEnd()
        // Fix `#Title` → `# Title` (but leave `#######` run alone).
        val level = headingLevelOf(line)
        if (level in 1..6) {
            val indent = line.takeWhile { it == ' ' }
            var rest = line.drop(indent.length + level)
            if (rest.isNotEmpty() && rest[0] != ' ' && rest[0] != '\t' && rest[0] != '#') {
                rest = " " + rest
            }
            line = indent + "#".repeat(level) + rest
        }
        out.add(line)
    }
    return out.joinToString("\n").trimEnd()
}

/**
 * Compact chat typography: tight bold heading ladder over the app's body
 * text, monospace code, italic quotes, primary-colored links.
 */
@Composable
fun chatMarkdownTypography(): MarkdownTypography {
    val base = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    return markdownTypography(
        h1 = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        h2 = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
        h3 = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
        h4 = base.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
        h5 = base.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
        h6 = base.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
        text = base.bodyLarge,
        paragraph = base.bodyLarge,
        ordered = base.bodyLarge,
        bullet = base.bodyLarge,
        list = base.bodyLarge,
        table = base.bodyLarge,
        link = base.bodyLarge.copy(
            color = scheme.primary,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        ),
        quote = base.bodyMedium.copy(fontStyle = FontStyle.Italic)
    )
}

/**
 * Light/dark-aware chat colors drawn from the app [MaterialTheme] so code,
 * dividers and tables stay readable in both variants. The scheme itself
 * switches with the system theme, so both variants stay themed with no
 * extra wiring.
 */
@Composable
fun chatMarkdownColors(): MarkdownColors {
    val scheme = MaterialTheme.colorScheme
    return markdownColor(
        text = scheme.onSurface,
        codeBackground = scheme.surfaceContainerHigh,
        inlineCodeBackground = scheme.surfaceContainerHighest,
        dividerColor = scheme.outlineVariant,
        tableBackground = scheme.surfaceContainerLow
    )
}

/**
 * Error-tone chat colors: same surfaces as [chatMarkdownColors] but the
 * prose renders in `scheme.error` so failed turns read as failures.
 */
@Composable
fun chatMarkdownErrorColors(): MarkdownColors {
    val scheme = MaterialTheme.colorScheme
    return markdownColor(
        text = scheme.error,
        codeBackground = scheme.surfaceContainerHigh,
        inlineCodeBackground = scheme.surfaceContainerHighest,
        dividerColor = scheme.outlineVariant,
        tableBackground = scheme.surfaceContainerLow
    )
}

/** Fill-width modifier shared by every prose Markdown block in chat. */
fun chatMarkdownModifier(): Modifier = Modifier.fillMaxWidth()
