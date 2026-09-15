package com.jnd.ngdroid.ui.plot

import com.jnd.ngdroid.engine.VectorSeries
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Waveform math expressions, e.g. `v(out)-v(in)`, `v(n)*5`, `db(v(out))`.
 * Syntax mirrors ngspice `let` expressions on purpose (a future native
 * path stays compatible), but evaluation is client-side: a math trace is
 * only ever an expression string, evaluated on demand over the existing
 * vectors — no computed arrays are stored (phone-memory friendly).
 *
 * Grammar: expr := term (('+'|'-') term)* ; term := power (('*'|'/') power)*
 * power := unary ('^' unary)? ; unary := '-' unary | primary
 * primary := number | vector | func '(' expr ')' | '(' expr ')'
 * A vector is any identifier, optionally with a balanced (...) suffix, so
 * `v(out)`, `v1#branch`, `time` all work. Known function names take
 * precedence: abs mag db sqrt ln log10 exp sin cos.
 * Numbers accept SPICE suffixes (M=mega, m=milli, k, u, n, p, f, Meg…).
 * Pure; JVM-testable.
 */
sealed interface Mexpr {
    data class Num(val v: Double) : Mexpr
    data class Vec(val name: String) : Mexpr
    data class Bin(val op: Char, val l: Mexpr, val r: Mexpr) : Mexpr
    data class Neg(val e: Mexpr) : Mexpr
    data class Fn(val name: String, val arg: Mexpr) : Mexpr
}

val MATH_FUNCTIONS: Set<String> = setOf("abs", "mag", "db", "sqrt", "ln", "log10", "exp", "sin", "cos")

private class MathParser(private val src: String) {
    private var pos = 0

    fun parse(): Mexpr {
        val e = parseExpr()
        skipWs()
        if (pos < src.length) fail("unexpected '${src[pos]}'")
        return e
    }

    private fun parseExpr(): Mexpr {
        var e = parseTerm()
        while (true) {
            skipWs()
            val c = peek()
            if (c == '+' || c == '-') {
                pos++
                e = Mexpr.Bin(c, e, parseTerm())
            } else return e
        }
    }

    private fun parseTerm(): Mexpr {
        var e = parsePower()
        while (true) {
            skipWs()
            val c = peek()
            if (c == '*' || c == '/') {
                pos++
                e = Mexpr.Bin(c, e, parsePower())
            } else return e
        }
    }

    private fun parsePower(): Mexpr {
        val base = parseUnary()
        skipWs()
        if (peek() == '^') {
            pos++
            return Mexpr.Bin('^', base, parseUnary())
        }
        return base
    }

    private fun parseUnary(): Mexpr {
        skipWs()
        if (peek() == '-') {
            pos++
            return Mexpr.Neg(parseUnary())
        }
        if (peek() == '+') {
            pos++
            return parseUnary()
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Mexpr {
        skipWs()
        val c = peek() ?: fail("unexpected end")
        if (c == '(') {
            pos++
            val e = parseExpr()
            skipWs()
            if (peek() != ')') fail("expected ')'")
            pos++
            return e
        }
        if (c.isDigit() || c == '.') return Mexpr.Num(parseNumber())
        if (c.isLetter() || c == '_' || c == '#') {
            val ident = parseIdent()
            skipWs()
            if (peek() == '(') {
                if (ident.lowercase() in MATH_FUNCTIONS) {
                    pos++
                    val arg = parseExpr()
                    skipWs()
                    if (peek() != ')') fail("expected ')' after ${ident.lowercase()}(…)")
                    pos++
                    return Mexpr.Fn(ident.lowercase(), arg)
                }
                // Otherwise it's a vector name with a (...) suffix: v(out).
                return Mexpr.Vec(ident + consumeBalancedParens())
            }
            return Mexpr.Vec(ident)
        }
        fail("unexpected '$c'")
    }

    private fun parseIdent(): String {
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] in "_#.:")) pos++
        if (start == pos) fail("expected a name")
        return src.substring(start, pos)
    }

    private fun consumeBalancedParens(): String {
        // pos is ON '('.
        val start = pos
        var depth = 0
        while (pos < src.length) {
            if (src[pos] == '(') depth++
            if (src[pos] == ')') {
                depth--
                pos++
                if (depth == 0) return src.substring(start, pos)
                continue
            }
            pos++
        }
        fail("unbalanced '(' in vector name")
    }

    private fun parseNumber(): Double {
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' ||
                    src[pos] == 'e' || src[pos] == 'E' ||
                    ((src[pos] == '+' || src[pos] == '-') && pos > start &&
                            (src[pos - 1] == 'e' || src[pos - 1] == 'E')))
        ) pos++
        val numStr = src.substring(start, pos)
        val mantissa = numStr.toDoubleOrNull() ?: fail("bad number '$numStr'")
        // SPICE suffix: Meg (any case) or one letter (M=mega, m=milli).
        var mult = 1.0
        if (pos < src.length && src[pos].isLetter()) {
            val sStart = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            val suffix = src.substring(sStart, pos)
            mult = when {
                suffix.equals("meg", ignoreCase = true) -> 1e6
                suffix.length == 1 -> when (suffix[0]) {
                    'T' -> 1e12
                    'G', 'g' -> 1e9
                    'M' -> 1e6
                    'K', 'k' -> 1e3
                    'm' -> 1e-3
                    'U', 'u' -> 1e-6
                    'N', 'n' -> 1e-9
                    'P', 'p' -> 1e-12
                    'F', 'f' -> 1e-15
                    else -> fail("unknown suffix '$suffix'")
                }
                else -> fail("unknown suffix '$suffix'")
            }
        }
        return mantissa * mult
    }

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun peek(): Char? = if (pos < src.length) src[pos] else null

    private fun fail(msg: String): Nothing =
        throw IllegalArgumentException("Bad expression: $msg")
}

/** Parse [src] or throw IllegalArgumentException with a readable message. */
fun parseMathExpr(src: String): Mexpr {
    if (src.isBlank()) throw IllegalArgumentException("Empty expression")
    return MathParser(src.trim()).parse()
}

/** Vector names referenced by [e] (as typed). Pure. */
fun mathExprVectors(e: Mexpr): Set<String> = when (e) {
    is Mexpr.Num -> emptySet()
    is Mexpr.Vec -> setOf(e.name)
    is Mexpr.Bin -> mathExprVectors(e.l) + mathExprVectors(e.r)
    is Mexpr.Neg -> mathExprVectors(e.e)
    is Mexpr.Fn -> mathExprVectors(e.arg)
}

private fun applyFn(name: String, x: Double): Double = when (name) {
    "abs", "mag" -> abs(x)
    "db" -> 20.0 * log10(max(abs(x), 1e-30)) // floor: never -Inf
    "sqrt" -> sqrt(x) // negative -> NaN (renderer breaks the path)
    "ln" -> ln(x)
    "log10" -> log10(x)
    "exp" -> exp(x)
    "sin" -> sin(x)
    "cos" -> cos(x)
    else -> Double.NaN
}

/** Validation outcome for the dialog: ok + preview, or a readable error. */
data class MathValidation(val ok: Boolean, val message: String)

/**
 * Validate [src] against [vectors] (matched exact, then case-insensitive):
 * syntax, at least one vector, every name resolves. Pure.
 */
fun validateMathExpr(src: String, vectors: Map<String, VectorSeries>): MathValidation {
    if (src.isBlank()) return MathValidation(false, "Type an expression, e.g. v(out)-v(in)")
    val expr = try {
        parseMathExpr(src)
    } catch (e: IllegalArgumentException) {
        return MathValidation(false, e.message ?: "Bad expression")
    }
    val names = mathExprVectors(expr)
    if (names.isEmpty()) return MathValidation(false, "Reference at least one trace, e.g. v(n)*5")
    val byLower = vectors.keys.associateBy { it.lowercase() }
    val missing = names.filter { it !in vectors && it.lowercase() !in byLower }
    if (missing.isNotEmpty()) return MathValidation(false, "Unknown trace: ${missing.first()}")
    val trace = evalParsedMath(expr, vectors) ?: return MathValidation(false, "No data")
    val finite = trace.values.filter { it.isFinite() }
    if (finite.isEmpty()) return MathValidation(false, "Result has no finite points")
    return MathValidation(
        true,
        "OK · ${trace.values.size} pts · ${formatTick(finite.min())} … ${formatTick(finite.max())}"
    )
}

/** Bind names (exact, then case-insensitive) and evaluate. Null when impossible. */
fun evalParsedMath(e: Mexpr, vectors: Map<String, VectorSeries>): VectorSeries? {
    val names = mathExprVectors(e)
    if (names.isEmpty()) return null
    val byLower = vectors.keys.associateBy { it.lowercase() }
    val resolved = names.associateWith { n ->
        vectors[n] ?: vectors[byLower[n.lowercase()]]
    }
    if (resolved.values.any { it == null || it.values.isEmpty() }) return null
    val n = resolved.values.map { it!!.values.size }.min()
    if (n <= 0) return null
    val cols = resolved.mapValues { (_, v) -> v!!.values }
    // Scalars (pure numbers) broadcast; vectors index pointwise.
    fun at(node: Mexpr, i: Int): Double = when (node) {
        is Mexpr.Num -> node.v
        is Mexpr.Vec -> cols[node.name]!![i]
        is Mexpr.Bin -> {
            val l = at(node.l, i)
            val r = at(node.r, i)
            when (node.op) {
                '+' -> l + r
                '-' -> l - r
                '*' -> l * r
                '/' -> if (r == 0.0) Double.NaN else l / r
                '^' -> l.pow(r)
                else -> Double.NaN
            }
        }
        is Mexpr.Neg -> -at(node.e, i)
        is Mexpr.Fn -> applyFn(node.name, at(node.arg, i))
    }
    val out = DoubleArray(n, { i -> at(e, i) })
    return VectorSeries(name = "", isScale = false, values = out.toList())
}

/**
 * Full pipeline for the plot: parse [src], evaluate against [vectors],
 * wrap as a named trace. Null on blank/invalid/unresolvable input.
 * dB results always go left; otherwise currents stay currents only if
 * every referenced vector is a current.
 */
fun evalMathExpr(src: String, vectors: List<VectorSeries>): VectorSeries? {
    if (src.isBlank()) return null
    val expr = try {
        parseMathExpr(src)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val map = vectors.associateBy { it.name }
    val trace = evalParsedMath(expr, map) ?: return null
    val names = mathExprVectors(expr)
    val byLower = vectors.associateBy { it.name.lowercase() }
    val refs = names.mapNotNull { map[it] ?: byLower[it.lowercase()] }
    if (refs.isEmpty()) return null
    val hasDb = containsFn(expr, "db")
    return trace.copy(
        // Trace name is the equation exactly as typed (ends trimmed).
        name = src.trim(),
        forceCurrent = if (hasDb) false else refs.all { it.isCurrent }
    )
}

private fun containsFn(e: Mexpr, fn: String): Boolean = when (e) {
    is Mexpr.Num, is Mexpr.Vec -> false
    is Mexpr.Bin -> containsFn(e.l, fn) || containsFn(e.r, fn)
    is Mexpr.Neg -> containsFn(e.e, fn)
    is Mexpr.Fn -> e.name == fn || containsFn(e.arg, fn)
}
