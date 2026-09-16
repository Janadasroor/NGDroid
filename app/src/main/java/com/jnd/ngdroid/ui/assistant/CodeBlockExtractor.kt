package com.jnd.ngdroid.ui.assistant

import com.jnd.ngdroid.agent.SpiceValidator

/** Extract triple-backtick fenced code blocks (any language tag). */
fun extractCodeBlocks(markdown: String): List<String> {
    if (markdown.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    var idx = 0
    while (true) {
        val start = markdown.indexOf("```", idx)
        if (start < 0) break
        val end = markdown.indexOf("```", start + 3)
        if (end < 0) break
        var body = markdown.substring(start + 3, end)
        // Strip optional language tag on first line (e.g. ```spice / ```cir / ```text).
        val nl = body.indexOf('\n')
        if (nl >= 0) {
            val first = body.substring(0, nl).trim()
            if (first.isNotEmpty() && !first.contains(' ') && !first.contains('\n') &&
                (first.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '+' || it == '.' })
            ) {
                // Heuristic: a single-token first line is a language tag, not
                // SPICE ("R1 in out 1k" lines contain spaces) — drop it.
                body = body.substring(nl + 1)
            }
        } else {
            // Single-line fence content passes through untouched.
        }
        out.add(body.trim('\n'))
        idx = end + 3
    }
    return out
}

/**
 * First fenced block that [SpiceValidator.validate] accepts; else first block; else null.
 */
fun extractFirstNetlist(markdown: String): String? {
    val blocks = extractCodeBlocks(markdown)
    if (blocks.isEmpty()) return null
    for (b in blocks) {
        if (b.isBlank()) continue
        if (runCatching { SpiceValidator.validate(b).isValid }.getOrDefault(false)) return b
    }
    return blocks.firstOrNull { it.isNotBlank() }
}

/**
 * True when a fenced block validates as a SPICE netlist. Used by the chat UI
 * to decide whether a block gets Apply/Run actions or is copy-only.
 */
fun isNetlistBlock(block: String): Boolean =
    runCatching { SpiceValidator.validate(block).isValid }.getOrDefault(false)
