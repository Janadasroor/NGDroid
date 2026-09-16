package com.jnd.ngdroid.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Third modality for the agent: render the current plot as a small JPEG
 * thumbnail the model can "see" as pixels (shape check: clipping, ringing,
 * distortion, phase). Numbers must still come from samples:/vectors:.
 * Pure arg parsing is JVM-testable; bitmap work lives in the host lambda.
 */
class RenderPlotTool(
    private val render: suspend (requested: List<String>?) -> ToolResult
) : AgentTool {
    override val name: String = "render_plot"
    override val description: String =
        "Render the current simulation plot as a small image for shape checking " +
            "(clipping, ringing, distortion, phase). Input JSON: {\"vectors\": [\"v(out)\"] optional}. " +
            "Use only when text samples are ambiguous — numbers always come from samples:/vectors:."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"vectors":{"type":"array","items":{"type":"string"}}},"required":[]}"""

    override suspend fun execute(argsJson: String): String =
        executeEx(argsJson).text

    override suspend fun executeEx(argsJson: String): ToolResult {
        val requested = parseRenderPlotArgs(argsJson)
        return try {
            render(requested)
        } catch (e: Exception) {
            ToolResult("ERROR: ${e.message}")
        }
    }
}

/** Parses render_plot args: optional vector-name allowlist. Pure; JVM-testable. */
fun parseRenderPlotArgs(argsJson: String): List<String>? {
    if (argsJson.isBlank()) return null
    val root = runCatching {
        Json { ignoreUnknownKeys = true }.parseToJsonElement(argsJson).jsonObject
    }.getOrNull() ?: return null
    val el = root["vectors"] ?: return null
    return runCatching {
        el.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }.take(6).ifEmpty { null }
    }.getOrNull()
}
