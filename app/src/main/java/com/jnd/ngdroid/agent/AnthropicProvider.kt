package com.jnd.ngdroid.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Direct Anthropic provider (`https://api.anthropic.com/v1`).
 *
 * Uses the `messages` API: `x-api-key` + `anthropic-version` header auth,
 * top-level `system` prompt (no system role), vision as `image` blocks with
 * base64 sources, tools as `name/description/input_schema` with
 * `tool_choice: {type: auto}`, tool results as `tool_result` blocks.
 * Listing models is free (`GET /v1/models`); chatting needs credits.
 * Requires the user's own key — no free tier.
 */
class AnthropicProvider(
    private val http: HttpPost,
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-5",
    /** Override for Anthropic-compatible sibling gateways (OpenCode Go /messages). */
    private val baseUrl: String = ANTHROPIC_BASE
) : LlmProvider {

    override val id: String = "anthropic"
    override val displayName: String = "Anthropic"
    override val defaultModel: String = "claude-sonnet-4-5"

    private val json = Json { ignoreUnknownKeys = true }

    private fun authHeaders(key: String): Map<String, String> = mapOf(
        "x-api-key" to key,
        "anthropic-version" to ANTHROPIC_VERSION,
        "Content-Type" to "application/json"
    )

    override suspend fun chat(req: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/messages"
        val body = buildRequestJson(model, req.systemPrompt, req.messages, req.tools, req.temperature, req.maxTokens)
        parseChatResponse(http(url, authHeaders(apiKey), body))
    }

    override suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$baseUrl/models?limit=1000")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${body.take(500)}")
            parseListModelsResponse(body)
        }
    }

    /** Pure, testable `messages` request builder (vision-capable). */
    fun buildRequestJson(
        model: String,
        system: String,
        messages: List<ChatMessage>,
        tools: List<LlmTool>,
        temp: Double,
        maxTokens: Int
    ): String {
        val systemTexts = mutableListOf<String>()
        if (system.isNotBlank()) systemTexts.add(system)
        val msgs = mutableListOf<JsonObject>()
        for (m in messages) {
            when (m.role) {
                // No system role: fold into the top-level system prompt.
                ChatRole.SYSTEM -> {
                    if (m.content.isNotBlank()) systemTexts.add("SYSTEM: ${m.content}")
                }
                // Observations return as user-turn tool_result blocks.
                ChatRole.TOOL -> msgs.add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonArray(listOf(buildJsonObject {
                        put("type", JsonPrimitive("tool_result"))
                        put("tool_use_id", JsonPrimitive(m.toolCallId ?: ""))
                        put("content", JsonPrimitive(m.content))
                    })))
                })
                else -> msgs.add(buildAnthropicMessage(m))
            }
        }
        return buildJsonObject {
            put("model", JsonPrimitive(model.trim()))
            put("max_tokens", JsonPrimitive(maxTokens.coerceAtLeast(1)))
            val sys = systemTexts.joinToString("\n\n").trim()
            if (sys.isNotEmpty()) put("system", JsonPrimitive(sys))
            put("messages", JsonArray(msgs))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools.map { buildAnthropicTool(it) }))
                put("tool_choice", buildJsonObject {
                    put("type", JsonPrimitive("auto"))
                })
            }
            put("temperature", JsonPrimitive(temp))
        }.toString()
    }

    private fun buildAnthropicMessage(m: ChatMessage): JsonObject {
        val role = if (m.role == ChatRole.ASSISTANT) "assistant" else "user"
        return buildJsonObject {
            put("role", JsonPrimitive(role))
            val blocks = mutableListOf<JsonObject>()
            if (m.content.isNotEmpty()) {
                blocks.add(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(m.content))
                })
            }
            if (role == "user") {
                for (img in m.images.take(MAX_VISION_IMAGES)) {
                    blocks.add(buildJsonObject {
                        put("type", JsonPrimitive("image"))
                        put("source", buildJsonObject {
                            put("type", JsonPrimitive("base64"))
                            put("media_type", JsonPrimitive(img.mimeType.ifBlank { "image/jpeg" }))
                            put("data", JsonPrimitive(img.base64))
                        })
                    })
                }
            } else {
                for (tc in m.toolCalls) {
                    val inputEl =
                        runCatching { json.parseToJsonElement(tc.argumentsJson.ifBlank { "{}" }) }
                            .getOrElse { buildJsonObject { } }
                    blocks.add(buildJsonObject {
                        put("type", JsonPrimitive("tool_use"))
                        put("id", JsonPrimitive(tc.id))
                        put("name", JsonPrimitive(tc.name))
                        put("input", inputEl)
                    })
                }
            }
            if (blocks.isEmpty()) {
                blocks.add(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(""))
                })
            }
            put("content", JsonArray(blocks))
        }
    }

    /** `messages` tool shape: `{"name", "description", "input_schema"}`. */
    private fun buildAnthropicTool(t: LlmTool): JsonObject {
        return buildJsonObject {
            put("name", JsonPrimitive(t.name))
            put("description", JsonPrimitive(t.description))
            val schemaEl =
                runCatching { json.parseToJsonElement(t.parametersJsonSchema) }.getOrElse {
                    buildJsonObject { put("type", JsonPrimitive("object")) }
                }
            put("input_schema", schemaEl)
        }
    }

    fun parseChatResponse(bodyJson: String): LlmResponse {
        val root = json.parseToJsonElement(bodyJson).jsonObject
        // Surface API errors (e.g. authentication_error, rate_limit_error) as readable text.
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
            return LlmResponse("Anthropic error: $msg", emptyList())
        }
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        for (el in root["content"]?.jsonArray ?: JsonArray(emptyList())) {
            val obj = el.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> text.append(obj["text"]?.let {
                    if (it is JsonNull) "" else it.jsonPrimitive.contentOrNull ?: ""
                } ?: "")
                "tool_use" -> toolCalls.add(
                    ToolCall(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        argumentsJson = obj["input"]?.toString() ?: "{}"
                    )
                )
            }
        }
        return LlmResponse(text.toString(), toolCalls)
    }

    companion object {
        /** Cap vision payloads: matches MAX_ATTACHMENTS_PER_MESSAGE. */
        const val MAX_VISION_IMAGES = 4

        const val ANTHROPIC_BASE = "https://api.anthropic.com/v1"

        /** Pinned API version header (2023-06-01 is the current stable). */
        const val ANTHROPIC_VERSION = "2023-06-01"

        fun parseListModelsResponse(bodyJson: String): List<String> {
            val json = Json { ignoreUnknownKeys = true }
            val root = runCatching { json.parseToJsonElement(bodyJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val data = root["data"]?.jsonArray ?: return emptyList()
            return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedBy { it.lowercase() }
        }
    }
}
