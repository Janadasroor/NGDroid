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
 * Dependency-free LaTeX → readable-Unicode prettifier for chat bubbles.
 *
 * Other mobile chat apps render math either with a per-message WebView
 * (KaTeX/MathJax: 50–150 MB RAM, scroll jank, needs vendored JS for offline)
 * or natively. We render natively: no WebView, works offline, follows the
 * Material theme, and stays accessible to screen readers.
 *
 * Never throws: on unexpected input the trimmed original is returned.
 */
fun prettyMath(latex: String): String {
    return runCatching { prettyMathOrThrow(latex) }.getOrDefault(latex.trim())
}

private fun prettyMathOrThrow(latex: String): String {
    var s = latex.trim()
    if (s.isEmpty()) return s
    // Strip surrounding delimiters if the caller passed them through.
    s = s.removePrefix("$$").removeSuffix("$$").trim()
    s = s.removePrefix("\\[").removeSuffix("\\]").trim()
    s = s.removePrefix("\\(").removeSuffix("\\)").trim()
    s = s.removePrefix("$").removeSuffix("$").trim()

    s = s.replace("^{\\circ}", "°").replace("^\\circ", "°")
    s = s.replace("\\cfrac", "\\frac").replace("\\dfrac", "\\frac").replace("\\tfrac", "\\frac")

    // Matrices: keep cells readable in one flowing line.
    s = replaceEnvironments(s)
    // Models often double-escape backslashes (`2\\pi`); collapse to one so
    // commands below still match. (Row breaks were consumed above.)
    s = s.replace("\\\\", "\\")
    // \frac{a}{b} → a⁄b (or (a)/(b) when complex).
    s = replaceFractions(s)
    // \sqrt[n]{x} → ⁿ√(x).
    s = replaceRoots(s)
    // \text{…} / \mathrm{…} → … (unwrap, braces resolved inside).
    s = unwrapTextCommands(s)
    s = replaceSymbols(s)
    s = replaceScripts(s)
    // \left( \right. → ( (empty).
    s = s.replace("\\left", "").replace("\\right", "")
    // Stray braces around single tokens: {x} → x; keep {multi word} as (…).
    s = collapseBraces(s)
    s = s.replace("\\,", " ").replace("\\;", " ").replace("\\:", " ")
        .replace("\\!", "").replace("\\quad", " ").replace("\\qquad", " ")
        .replace("\\ ", " ").replace("\\$", "$").replace("\\%", "%")
        .replace("\\&", "&").replace("\\#", "#").replace("\\_", "_")
    return s.replace(Regex("[ \t]{2,}"), " ")
        .replace(Regex(" *\n *"), " ")
        .trim()
}

/** `\begin{bmatrix}1&0\\0&1\end{bmatrix}` → `[1  0 | 0  1]`. */
private fun replaceEnvironments(s: String): String {
    var out = s
    val env = Regex("\\\\begin\\{[a-zA-Z*]+\\}(.*?)\\\\end\\{[a-zA-Z*]+\\}", RegexOption.DOT_MATCHES_ALL)
    out = env.replace(out) { m ->
        val body = m.groupValues[1].trim()
        val rows = body.split(Regex("\\\\\\\\"))
            .map { row -> row.split("&").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  ") }
            .filter { it.isNotEmpty() }
        "[" + rows.joinToString(" ￨ ") + "]"
    }
    return out
}

/** Finds `\frac{A}{B}` at [start] (index of backslash). Returns replacement + next index. */
private fun parseFrac(s: String, start: Int): Pair<String, Int>? {
    val cmdEnd = s.indexOf('{', start)
    if (cmdEnd < 0) return null
    val num = readBrace(s, cmdEnd) ?: return null
    val den = readBrace(s, num.second) ?: return null
    val prettyNum = prettyMathOrThrow(num.first)
    val prettyDen = prettyMathOrThrow(den.first)
    val simple: (String) -> Boolean = { t -> t.length <= 3 && !t.any { it in "()[] " } }
    val frac = if (simple(prettyNum) && simple(prettyDen)) {
        "$prettyNum⁄$prettyDen"
    } else {
        "(${prettyNum})/(${prettyDen})"
    }
    return frac to den.second
}

private fun replaceFractions(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s.startsWith("\\frac", i)) {
            val parsed = parseFrac(s, i)
            if (parsed != null) {
                out.append(parsed.first)
                i = parsed.second
                continue
            }
        }
        out.append(s[i])
        i++
    }
    return out.toString()
}

private fun replaceRoots(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s.startsWith("\\sqrt", i)) {
            var j = i + "\\sqrt".length
            var degree = ""
            if (j < s.length && s[j] == '[') {
                val close = s.indexOf(']', j)
                if (close > 0) {
                    degree = s.substring(j + 1, close)
                    j = close + 1
                }
            }
            if (j < s.length && s[j] == '{') {
                val body = readBrace(s, j)
                if (body != null) {
                    val inner = prettyMathOrThrow(body.first)
                    val wrapped = if (inner.length == 1) inner else "($inner)"
                    out.append(toSup(degree)).append("√").append(wrapped)
                    i = body.second
                    continue
                }
            }
        }
        out.append(s[i])
        i++
    }
    return out.toString()
}

/** `\text{hi}` → `hi` (recursively prettified inside). */
private fun unwrapTextCommands(s: String): String {
    var out = s
    val names = listOf("text", "mathrm", "mathbf", "mathit", "boldsymbol", "operatorname", "mbox", "overline", "underline")
    for (name in names) {
        val pattern = Regex("\\\\$name\\{([^{}]*(?:\\{[^{}]*\\}[^{}]*)*)\\}")
        out = pattern.replace(out) { m -> prettyMathOrThrow(m.groupValues[1]) }
    }
    // Accents: keep the base readable.
    for (name in listOf("hat", "bar", "vec", "dot", "ddot", "tilde", "widehat")) {
        val pattern = Regex("\\\\$name\\{([^{}]*)\\}")
        out = pattern.replace(out) { m -> m.groupValues[1] }
    }
    out = out.replace("\\prime", "′")
    return out
}

private val SYMBOLS: Map<String, String> = mapOf(
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
    "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
    "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
    "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
    "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ", "\\sigma" to "σ",
    "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ", "\\varphi" to "φ",
    "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
    "\\Alpha" to "Α", "\\Beta" to "Β", "\\Gamma" to "Γ", "\\Delta" to "Δ",
    "\\Epsilon" to "Ε", "\\Theta" to "Θ", "\\Lambda" to "Λ", "\\Mu" to "Μ",
    "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Phi" to "Φ",
    "\\Psi" to "Ψ", "\\Omega" to "Ω",
    "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇",
    "\\cdot" to "·", "\\times" to "×", "\\div" to "÷", "\\pm" to "±",
    "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
    "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
    "\\sim" to "∼", "\\simeq" to "≃", "\\propto" to "∝",
    "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←",
    "\\Rightarrow" to "⇒", "\\Leftrightarrow" to "⇔", "\\leftrightarrow" to "↔",
    "\\uparrow" to "↑", "\\downarrow" to "↓",
    "\\sum" to "∑", "\\int" to "∫", "\\prod" to "∏", "\\coprod" to "∐",
    "\\forall" to "∀", "\\exists" to "∃", "\\in" to "∈", "\\notin" to "∉",
    "\\subset" to "⊂", "\\subseteq" to "⊆", "\\cup" to "∪", "\\cap" to "∩",
    "\\odot" to "⊙", "\\otimes" to "⊗", "\\circ" to "∘", "\\bullet" to "•",
    "\\dots" to "…", "\\cdots" to "⋯", "\\ldots" to "…", "\\vdots" to "⋮",
    "\\hbar" to "ℏ", "\\ell" to "ℓ", "\\Re" to "ℜ", "\\Im" to "ℑ",
    "\\aleph" to "ℵ", "\\emptyset" to "∅", "\\angle" to "∠",
    "\\|" to "‖", "\\mid" to "|", "\\vert" to "|", "\\parallel" to "∥",
    "\\log" to "log", "\\ln" to "ln", "\\exp" to "exp",
    "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
    "\\min" to "min", "\\max" to "max", "\\sup" to "sup", "\\inf" to "inf",
    "\\det" to "det", "\\deg" to "deg", "\\arg" to "arg"
)

private fun replaceSymbols(s: String): String {
    var out = s
    // Longest command first so \varepsilon wins over \epsilon-style prefixes.
    SYMBOLS.keys.sortedByDescending { it.length }.forEach { cmd ->
        out = out.replace(cmd, SYMBOLS.getValue(cmd))
    }
    return out
}

private val SUP: Map<Char, Char> = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ', 'j' to 'ʲ', 'x' to 'ˣ', 'y' to 'ʸ',
    'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
    'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'k' to 'ᵏ', 'l' to 'ˡ',
    'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ',
    't' to 'ᵗ', 'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'z' to 'ᶻ',
    'T' to 'ᵀ', 'A' to 'ᴬ', 'B' to 'ᴮ', 'D' to 'ᴰ', 'E' to 'ᴱ',
    'G' to 'ᴳ', 'H' to 'ᴴ', 'I' to 'ᴵ', 'J' to 'ᴶ', 'K' to 'ᴷ',
    'L' to 'ᴸ', 'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ', 'P' to 'ᴾ',
    'R' to 'ᴿ', 'U' to 'ᵁ', 'V' to 'ⱽ', 'W' to 'ᵂ'
)

private val SUB: Map<Char, Char> = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
    'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
    'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
    'v' to 'ᵥ', 'x' to 'ₓ'
)

private fun toSup(text: String): String =
    if (text.all { SUP.containsKey(it) } && text.isNotEmpty()) {
        text.map { SUP.getValue(it) }.joinToString("")
    } else if (text.isEmpty()) {
        ""
    } else if (text.length == 1) {
        // Single unknown glyph (e.g. ∞): keep it attached without parens.
        "^$text"
    } else {
        "^($text)"
    }

private fun toSub(text: String): String =
    if (text.all { SUB.containsKey(it) } && text.isNotEmpty()) {
        text.map { SUB.getValue(it) }.joinToString("")
    } else if (text.isEmpty()) {
        ""
    } else if (text.length == 1) {
        "_$text"
    } else {
        "_($text)"
    }

/** `x^{2}`, `x^2`, `V_{out}`, `a_i` → unicode where possible. */
private fun replaceScripts(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if ((c == '^' || c == '_') && i + 1 < s.length) {
            val isSup = c == '^'
            val body: String
            val next: Int
            if (s[i + 1] == '{') {
                val parsed = readBrace(s, i + 1) ?: run {
                    out.append(c)
                    i++
                    continue
                }
                // Recurse: V_{in,max} keeps commas readable.
                body = prettyMathOrThrow(parsed.first)
                next = parsed.second
            } else {
                body = s[i + 1].toString()
                next = i + 2
            }
            // A degree sign or prime attaches directly.
            if (body == "°" || body == "′") {
                out.append(body)
            } else {
                out.append(if (isSup) toSup(body) else toSub(body))
            }
            i = next
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/** `{x}` → `x`, `{two words}` → `(two words)`. Innermost-first. */
private fun collapseBraces(s: String): String {
    var out = s
    var guard = 0
    while (guard++ < 20) {
        val m = Regex("\\{([^{}]*)\\}").find(out) ?: break
        val inner = m.groupValues[1]
        val replacement = if (inner.length <= 1 || !inner.any { it.isWhitespace() }) inner else "($inner)"
        out = out.replaceRange(m.range, replacement)
    }
    return out
}

/** Reads `{…}` starting at [open] (index of `{`). Returns content + index after `}`. */
private fun readBrace(s: String, open: Int): Pair<String, Int>? {
    if (open >= s.length || s[open] != '{') return null
    var depth = 0
    var i = open
    while (i < s.length) {
        when (s[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return s.substring(open + 1, i) to i + 1
            }
        }
        i++
    }
    return null
}

/**
 * Replaces inline math (`\(…\)` and `$…$`) in prose with readable Unicode.
 * Code spans (fenced + inline) are left untouched.
 */
fun prettifyInlineMath(prose: String): String {
    if (prose.isEmpty() || (!prose.contains("$") && !prose.contains("\\("))) return prose
    val code = codeRanges(prose)
    val out = StringBuilder()
    var i = 0
    val n = prose.length
    while (i < n) {
        if (code.any { i in it }) {
            val hit = code.first { i in it }
            out.append(prose, hit.first, hit.last + 1)
            i = hit.last + 1
            continue
        }
        if (prose.startsWith("\\(", i)) {
            val close = prose.indexOf("\\)", i + 2)
            if (close < 0 || close - i > 402) {
                out.append(prose[i])
                i++
            } else {
                out.append(prettyMath(prose.substring(i + 2, close)))
                i = close + 2
            }
            continue
        }
        if (prose[i] == '$' && !prose.startsWith("$$", i) && isOpenDollar(prose, i)) {
            val close = findCloseDollar(prose, i + 1, code)
            if (close < 0) {
                out.append(prose[i])
                i++
            } else {
                val inner = prose.substring(i + 1, close)
                if (isPlausibleInlineMath(inner)) {
                    out.append(prettyMath(inner))
                    i = close + 1
                } else {
                    // Currency or plain dollars — keep literally.
                    out.append(prose[i])
                    i++
                }
            }
            continue
        }
        out.append(prose[i])
        i++
    }
    return out.toString()
}

private fun isOpenDollar(text: String, at: Int): Boolean {
    if (at > 0 && text[at - 1].isLetterOrDigit()) return false
    val next = at + 1
    if (next >= text.length || text[next].isWhitespace()) return false
    return true
}

private fun findCloseDollar(text: String, from: Int, code: List<IntRange>): Int {
    var i = from
    while (i < text.length) {
        if (code.any { i in it }) {
            i = code.first { i in it }.last + 1
            continue
        }
        if (text[i] == '$' && !text.startsWith("$$", i)) {
            // A closer must not be preceded by whitespace; a `$` followed by a
            // letter/digit is likely another opener, not our closer.
            val after = i + 1
            if (!text[i - 1].isWhitespace() &&
                (after >= text.length || !text[after].isLetterOrDigit())
            ) {
                if (i - from in 1..200 && !text.substring(from, i).contains('\n')) return i
                return -1
            }
            // A `$` followed by a letter/digit mid-run is likely an opener, not our closer.
            return -1
        }
        if (text[i] == '\n') {
            // Inline math stays on one line; give up after 200 chars anyway.
            if (i - from > 200) return -1
        }
        if (i - from > 200) return -1
        i++
    }
    return -1
}

/** Guards against turning prices ("$5 and $10") into equations. */
private fun isPlausibleInlineMath(inner: String): Boolean {
    if (inner.isBlank() || inner.length > 200) return false
    if (inner.first().isWhitespace() || inner.last().isWhitespace()) return false
    if (!inner.any { it in "=\\^_{}→∑∫√∞·×±≤≥≠≈" }) return false
    // Pure number with optional commas/decimals is currency, not math.
    if (inner.trim().matches(Regex("[0-9][0-9,.]*"))) return false
    return true
}
