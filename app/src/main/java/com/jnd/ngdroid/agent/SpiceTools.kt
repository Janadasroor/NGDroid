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
