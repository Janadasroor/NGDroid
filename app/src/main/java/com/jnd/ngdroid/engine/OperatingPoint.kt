package com.jnd.ngdroid.engine

/**
 * DC operating point: the `Node … Voltage` table ngspice prints
 * (initial transient solution / .op) and streams through SendChar.
 * Parsed from console logs — no extra simulation needed. Pure; JVM-testable.
 */
data class OpPointRow(
    val name: String,
    val value: Double,
    val isCurrent: Boolean
)

data class OperatingPoint(val rows: List<OpPointRow>)

/** Log line with the optional ngspice `stdout`/`stderr` source tag removed. */
internal fun stripLogTag(line: String): String {
    var t = line.trim()
    // Bracket form: "[stdout] ...", "[stderr] ...".
    if (t.startsWith("[", ignoreCase = true)) {
        val close = t.indexOf(']')
        if (close > 0 && t.substring(1, close).trim().equals("stdout", ignoreCase = true) ||
            close > 0 && t.substring(1, close).trim().equals("stderr", ignoreCase = true)
        ) {
            return t.substring(close + 1).trim()
        }
    }
    // Plain form: "stdout ", "stdout\t", "stdout: ...", any case.
    for (tag in arrayOf("stdout", "stderr")) {
        if (t.length > tag.length && t.substring(0, tag.length).equals(tag, ignoreCase = true)) {
            val next = t[tag.length]
            if (next.isWhitespace() || next == ':') {
                return t.substring(tag.length + 1).trim()
            }
        }
    }
    return t
}

private fun isCurrentName(name: String): Boolean =
    name.endsWith("#branch", ignoreCase = true) ||
        name.startsWith("i(", ignoreCase = true)

/**
 * Parse the LAST `Node Voltage` table plus the LAST `Source Current`
 * table in [logs] (ngspice `.op` prints both). Null when neither
 * table (or no data rows) is present. Pure.
 */
fun parseOperatingPoint(logs: List<String>): OperatingPoint? {
    var lastNode: List<OpPointRow>? = null
    var lastSource: List<OpPointRow>? = null
    var i = 0
    while (i < logs.size) {
        val head = stripLogTag(logs[i]).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val isNode = head.size == 2 && head[0].equals("Node", ignoreCase = true) &&
            head[1].equals("Voltage", ignoreCase = true)
        val isSource = head.size == 2 && head[0].equals("Source", ignoreCase = true) &&
            head[1].equals("Current", ignoreCase = true)
        if (!isNode && !isSource) {
            i++
            continue
        }
        val (rows, next) = parseTableRows(logs, i + 1, forceCurrent = isSource)
        i = next
        if (rows.isNotEmpty()) {
            if (isNode) lastNode = rows else lastSource = rows
        }
    }
    val combined = (lastNode.orEmpty() + lastSource.orEmpty())
    return combined.takeIf { it.isNotEmpty() }?.let { OperatingPoint(it) }
}

/** Parse separator + data rows starting at [start]; returns rows + next index. */
private fun parseTableRows(
    logs: List<String>,
    start: Int,
    forceCurrent: Boolean
): Pair<List<OpPointRow>, Int> {
    var i = start
    // Skip the ---- ---- separator line(s).
    while (i < logs.size) {
        val sep = stripLogTag(logs[i])
        if (sep.isNotEmpty() && sep.all { it == '-' || it.isWhitespace() }) i++ else break
    }
    val rows = mutableListOf<OpPointRow>()
    while (i < logs.size) {
        val line = stripLogTag(logs[i])
        if (line.isEmpty()) break
        // Info lines ("Reference value : …", "No. of Data Rows : …")
        // always contain ':' — node rows never do.
        if (':' in line) break
        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 2) break
        val value = parts.last().toDoubleOrNull() ?: break
        val name = parts.first()
        rows.add(OpPointRow(name, value, forceCurrent || isCurrentName(name)))
        i++
    }
    return rows to i
}
