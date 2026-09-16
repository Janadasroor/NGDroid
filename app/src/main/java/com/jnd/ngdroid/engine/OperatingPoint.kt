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
    val t = line.trim()
    return when {
        t.startsWith("stdout ") -> t.removePrefix("stdout ").trim()
        t.startsWith("stderr ") -> t.removePrefix("stderr ").trim()
        else -> t
    }
}

private fun isCurrentName(name: String): Boolean =
    name.endsWith("#branch", ignoreCase = true) ||
        name.startsWith("i(", ignoreCase = true)

/**
 * Parse the LAST complete `Node Voltage` table in [logs].
 * Null when no table (or no data rows) is present. Pure.
 */
fun parseOperatingPoint(logs: List<String>): OperatingPoint? {
    var last: OperatingPoint? = null
    var i = 0
    while (i < logs.size) {
        val head = stripLogTag(logs[i]).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (head.size == 2 && head[0].equals("Node", ignoreCase = true) &&
            head[1].equals("Voltage", ignoreCase = true)
        ) {
            i++
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
                rows.add(OpPointRow(parts.first(), value, isCurrentName(parts.first())))
                i++
            }
            if (rows.isNotEmpty()) last = OperatingPoint(rows)
        } else {
            i++
        }
    }
    return last
}
