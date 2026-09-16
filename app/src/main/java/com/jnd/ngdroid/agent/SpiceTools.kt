package com.jnd.ngdroid.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Host-app bridge. NGDroid implements this — no dependency from agent to app. */
interface SpiceAppBridge {
    fun applyNetlist(text: String): String
    fun currentNetlist(): String
    fun runSimulation(): String
}

class ValidateNetlistTool : AgentTool {
    override val name: String = "validate_netlist"
    override val description: String =
        "Validate a SPICE netlist. Input JSON: {\"netlist\": \"...\"}. Returns VALID or errors."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"netlist":{"type":"string"}},"required":["netlist"]}"""

    override suspend fun execute(argsJson: String): String {
        val netlist = extractNetlist(argsJson)
        val result = SpiceValidator.validate(netlist)
        return if (result.isValid) "VALID"
        else "INVALID:\n" + result.errors.joinToString("\n") { "- $it" }
    }

    private fun extractNetlist(argsJson: String): String {
        return runCatching {
            val json = Json { ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(argsJson).jsonObject
            obj["netlist"]?.jsonPrimitive?.contentOrNull ?: argsJson
        }.getOrElse { argsJson }
    }
}

class GenerateNetlistTemplateTool : AgentTool {
    override val name: String = "netlist_template"
    override val description: String =
        "Return a preset SPICE netlist template. Input JSON: {\"kind\": \"rc|rlc|diode|bjt|opamp\"}."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"kind":{"type":"string","enum":["rc","rlc","diode","bjt","opamp"]}},"required":["kind"]}"""

    override suspend fun execute(argsJson: String): String {
        val kind = runCatching {
            val json = Json { ignoreUnknownKeys = true }
            json.parseToJsonElement(argsJson).jsonObject["kind"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.lowercase() ?: "rc"
        return templateFor(kind)
    }

    companion object {
        fun templateFor(kind: String): String = when (kind.lowercase()) {
            "rlc" -> """
                * RLC series circuit
                V1 in 0 DC 0 AC 1 SIN(0 1 1k)
                R1 in mid 1k
                L1 mid out 10m
                C1 out 0 100n
                .tran 0.1m 10m
                .end
            """.trimIndent()
            "diode" -> """
                * Diode clipper circuit
                V1 in 0 SIN(0 5 1k)
                R1 in out 1k
                D1 out 0 1N4148
                .model 1N4148 D(IS=2.52n RS=0.568 N=1.752 BV=100 IBV=100u)
                .tran 0.05m 5m
                .end
            """.trimIndent()
            "bjt" -> """
                * BJT common-emitter amplifier
                Vcc vcc 0 12
                Vin in 0 SIN(0 10m 1k)
                Cin in b 10u
                Rb1 vcc b 47k
                Rb2 b 0 10k
                Rc vcc c 2.2k
                Re e 0 1k
                Ce e 0 100u
                Q1 c b e 0 q2n2222
                .model q2n2222 NPN(IS=1e-14 BF=200)
                Cout c out 10u
                Rl out 0 10k
                .tran 1u 5m
                .end
            """.trimIndent()
            "opamp" -> """
                * Opamp non-inverting amplifier (behavioral ideal opamp, gain = 1+R2/R1 = 11)
                Vin in 0 SIN(0 100m 1k)
                R1 0 inv 1k
                R2 inv out 10k
                Bop out 0 V=100k*(V(in)-V(inv))
                .tran 1u 5m
                .end
            """.trimIndent()
            else -> """
                * RC low-pass circuit
                V1 in 0 AC 1 SIN(0 1 1k)
                R1 in out 1k
                C1 out 0 100n
                .tran 0.1m 10m
                .end
            """.trimIndent()
        }
    }
}

class ApplyNetlistTool(private val bridge: SpiceAppBridge) : AgentTool {
    override val name: String = "apply_netlist"
    override val description: String =
        "Apply a netlist to the host app for display/simulation. Input JSON: {\"netlist\": \"...\"}."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"netlist":{"type":"string"}},"required":["netlist"]}"""

    override suspend fun execute(argsJson: String): String {
        val netlist = runCatching {
            val json = Json { ignoreUnknownKeys = true }
            json.parseToJsonElement(argsJson).jsonObject["netlist"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: argsJson
        return try {
            bridge.applyNetlist(netlist ?: "")
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}

/**
 * Runs the simulation and returns a text report (status + logs + vector
 * summary) the agent can explain and debug from. Accepts an optional
 * netlist to apply first, so the model never has to ask-first:
 * validate -> apply -> run_simulation -> explain, all in one turn.
 * Pure arg parsing ([parseRunSimulationArgs]) is JVM-testable.
 */
class RunSimulationTool(
    private val runReport: suspend (netlist: String?, timeoutMs: Long) -> String
) : AgentTool {
    override val name: String = "run_simulation"
    override val description: String =
        "Apply an optional netlist, run the simulation, and return status/logs/vector summary. " +
            "Input JSON: {\"netlist\": \"...optional...\", \"timeout_ms\": 30000}. " +
            "Call it directly after validate/apply when the user asks to run, simulate, " +
            "explain results, or debug — never ask-first."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"netlist":{"type":"string"},"timeout_ms":{"type":"integer"}},"required":[]}"""

    override suspend fun execute(argsJson: String): String {
        val (netlist, timeoutMs) = parseRunSimulationArgs(argsJson)
        return try {
            runReport(netlist, timeoutMs)
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}

/** Parses run_simulation args. Pure; JVM-testable. */
fun parseRunSimulationArgs(argsJson: String): Pair<String?, Long> {
    val root = runCatching {
        Json { ignoreUnknownKeys = true }.parseToJsonElement(argsJson).jsonObject
    }.getOrNull() ?: return null to DEFAULT_RUN_TIMEOUT_MS
    val netlist = root["netlist"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
    val timeoutMs = root["timeout_ms"]?.jsonPrimitive?.contentOrNull
        ?.toLongOrNull()?.coerceIn(1_000, 120_000) ?: DEFAULT_RUN_TIMEOUT_MS
    return netlist to timeoutMs
}

const val DEFAULT_RUN_TIMEOUT_MS: Long = 30_000

/**
 * Formats a simulation report for the agent: status, error, recent logs,
 * per-vector summaries (name + points + min/max/last) and, when [series]
 * is given, a downsampled numeric sample per vector (<= [maxPointsPerTrace]
 * points each) so the model can "see" waveform shape — resonance, ringing,
 * clipping — as tokens instead of pixels. Pure; JVM-testable.
 */
fun formatSimulationReport(
    statusText: String,
    hasError: Boolean,
    errorMessage: String?,
    logs: List<String>,
    vectors: List<String>,
    maxLogLines: Int = 40,
    series: Map<String, List<Double>> = emptyMap(),
    maxPointsPerTrace: Int = 64
): String = buildString {
    append("status: ").append(statusText.ifBlank { "unknown" })
    if (hasError) append(" [ERROR]")
    append("\n")
    errorMessage?.takeIf { it.isNotBlank() }?.let { append("error: ").append(it.trim()).append("\n") }
    val tail = logs.takeLast(maxLogLines)
    if (tail.isNotEmpty()) {
        append("logs:\n")
        tail.forEach { append("- ").append(it.trim()).append("\n") }
    }
    if (vectors.isNotEmpty()) {
        append("vectors:\n")
        vectors.forEach { append("- ").append(it.trim()).append("\n") }
    }
    if (series.isNotEmpty()) {
        append("samples:\n")
        series.forEach { (name, values) ->
            append("- ").append(name.trim()).append(": ")
            append(downsampleSeries(values, maxPointsPerTrace).joinToString(", ", "[", "]") { formatSample(it) })
            append("\n")
        }
    }
}

/** Even stride downsampling to at most [maxPoints] points. Pure. */
fun downsampleSeries(values: List<Double>, maxPoints: Int): List<Double> {
    if (values.size <= maxPoints || maxPoints <= 1) return values.take(maxOf(maxPoints, 1))
    val stride = values.size / maxPoints
    return values.filterIndexed { i, _ -> i % stride == 0 }.take(maxPoints)
}

/** Compact numeric sample for agent context (<=4 significant digits). Pure. */
fun formatSample(v: Double): String = when {
    !v.isFinite() -> "NaN"
    v == 0.0 -> "0"
    else -> compactG("%.4g".format(java.util.Locale.US, v))
}

/** Trim %g padding: "1.500" -> "1.5", "2.500e+06" -> "2.5e6". Pure. */
fun compactG(s: String): String {
    val eIdx = s.indexOfAny(charArrayOf('e', 'E'))
    if (eIdx < 0) {
        return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
    }
    val mant = s.substring(0, eIdx).trimEnd('0').trimEnd('.')
    var exp = s.substring(eIdx + 1).trimStart('+')
    exp = exp.trimStart('0').ifEmpty { "0" }
    if (exp.startsWith("-")) {
        val digits = exp.drop(1).trimStart('0').ifEmpty { "0" }
        exp = "-$digits"
    }
    return "${mant}e$exp"
}
