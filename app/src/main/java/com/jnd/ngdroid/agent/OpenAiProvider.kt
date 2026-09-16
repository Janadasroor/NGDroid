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
import okhttp3.Request

/**
 * Direct OpenAI provider (`https://api.openai.com/v1`).
 *
 * Uses the plain `chat/completions` API: Bearer key auth, nested
 * `tools[].function` tool shape, vision as `image_url` data-URI parts.
 * Listing models is free; chatting needs paid credits (a key without
 * credits fails with `insufficient_quota / credit_balance_exhausted`,
 * verified live). Requires the user's own key — no free tier.
 */
open class OpenAiProvider(
    private val http: HttpPost,
    private val apiKey: String,
    private val model: String = "gpt-4.1-mini",
    /** Override for OpenAI-compatible sibling gateways (OpenRouter). */
    private val baseUrl: String = OPENAI_BASE,
    /** Extra static headers (OpenRouter attribution). */
    private val extraHeaders: Map<String, String> = emptyMap(),
    override val id: String = "openai",
    override val displayName: String = "OpenAI",
    override val defaultModel: String = "gpt-4.1-mini",
    /** SSE transport; null = non-streaming chat() with a single partial. */
    private val streamHttp: HttpStream? = null
) : LlmProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(req: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/chat/completions"
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        ) + extraHeaders
        val body = buildRequestJson(model, req.systemPrompt, req.messages, req.tools, req.temperature, req.maxTokens)
        parseChatResponse(http(url, headers, body))
    }

    override suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$baseUrl/models")
            .get()
        // Some gateways (OpenRouter) serve /models publicly: only send auth with a key.
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        for ((k, v) in extraHeaders) builder.header(k, v)
        val client = HttpClients.listModelsClient()
        val request = builder.build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${body.take(500)}")
            parseListModelsResponse(body)
        }
    }

    override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse =
        withContext(Dispatchers.IO) {
            val stream = streamHttp ?: return@withContext super.streamChat(req, onPartial)
            val url = "$baseUrl/chat/completions"
            val headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ) + extraHeaders
            val body = buildRequestJson(
                model, req.systemPrompt, req.messages, req.tools,
                req.temperature, req.maxTokens, stream = true
            )
            val acc = ChatStreamAccumulator(onPartial)
            stream(url, headers, body) { acc.accept(it) }
            acc.response()
        }

    /** Pure, testable `chat/completions` request builder (vision-capable). */
    fun buildRequestJson(
        model: String,
        system: String,
        messages: List<ChatMessage>,
        tools: List<LlmTool>,
        temp: Double,
        maxTokens: Int,
        stream: Boolean = false
    ): String {
        val msgs = mutableListOf<JsonObject>()
        if (system.isNotBlank()) {
            msgs.add(buildJsonObject {
                put("role", JsonPrimitive("system"))
                put("content", JsonPrimitive(system))
            })
        }
        for (m in messages) {
            msgs.add(buildChatMessage(m))
        }
        val root = buildJsonObject {
            put("model", JsonPrimitive(model.trim()))
            put("messages", JsonArray(msgs))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools.map { buildFunctionTool(it) }))
                put("tool_choice", JsonPrimitive("auto"))
            }
            put("temperature", JsonPrimitive(temp))
            put("max_tokens", JsonPrimitive(maxTokens))
            if (stream) put("stream", JsonPrimitive(true))
        }
        return root.toString()
    }

    private fun buildChatMessage(m: ChatMessage, includeToolCalls: Boolean = true): JsonObject {
        val role = when (m.role) {
            ChatRole.SYSTEM -> "system"
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
            ChatRole.TOOL -> "tool"
        }
        return buildJsonObject {
            put("role", JsonPrimitive(role))
            if (m.images.isNotEmpty() && (m.role == ChatRole.USER || m.role == ChatRole.SYSTEM)) {
                val parts = mutableListOf<JsonObject>()
                if (m.content.isNotEmpty()) {
                    parts.add(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(m.content))
                    })
                }
                for (img in m.images.take(MAX_VISION_IMAGES)) {
                    val url = "data:${img.mimeType.ifBlank { "image/jpeg" }};base64,${img.base64}"
                    parts.add(buildJsonObject {
                        put("type", JsonPrimitive("image_url"))
                        put("image_url", buildJsonObject {
                            put("url", JsonPrimitive(url))
                        })
                    })
                }
                put("content", JsonArray(parts))
            } else {
                if (m.content.isNotEmpty()) put("content", JsonPrimitive(m.content))
                else put("content", JsonNull)
            }
            if (includeToolCalls && m.toolCalls.isNotEmpty()) {
                put("tool_calls", JsonArray(m.toolCalls.map { tc ->
                    buildJsonObject {
                        put("id", JsonPrimitive(tc.id))
                        put("type", JsonPrimitive("function"))
                        put("function", buildJsonObject {
                            put("name", JsonPrimitive(tc.name))
                            put("arguments", JsonPrimitive(tc.argumentsJson))
                        })
                    }
                }))
            }
            if (m.toolCallId != null) put("tool_call_id", JsonPrimitive(m.toolCallId))
        }
    }

    /** `chat/completions` tool shape: `{"type":"function","function":{...}}`. */
    private fun buildFunctionTool(t: LlmTool): JsonObject {
        return buildJsonObject {
            put("type", JsonPrimitive("function"))
            put("function", buildJsonObject {
                put("name", JsonPrimitive(t.name))
                put("description", JsonPrimitive(t.description))
                val paramsEl =
                    runCatching { json.parseToJsonElement(t.parametersJsonSchema) }.getOrElse {
                        buildJsonObject { put("type", JsonPrimitive("object")) }
                    }
                put("parameters", paramsEl)
            })
        }
    }

    fun parseChatResponse(bodyJson: String): LlmResponse {
        val root = json.parseToJsonElement(bodyJson).jsonObject
        // Surface API errors (e.g. insufficient_quota) as readable text.
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
            return LlmResponse("OpenAI error: $msg", emptyList())
        }
        val choices = root["choices"]?.jsonArray ?: return LlmResponse("", emptyList())
        if (choices.isEmpty()) return LlmResponse("", emptyList())
        val message = choices[0].jsonObject["message"]?.jsonObject ?: return LlmResponse("", emptyList())
        val content = message["content"]?.let {
            if (it is JsonNull) "" else it.jsonPrimitive.contentOrNull ?: ""
        } ?: ""
        val toolCalls = mutableListOf<ToolCall>()
        val rawCalls = message["tool_calls"]?.jsonArray
        if (rawCalls != null) {
            for (el in rawCalls) {
                val obj = el.jsonObject
                val fn = obj["function"]?.jsonObject ?: continue
                toolCalls.add(
                    ToolCall(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        name = fn["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        argumentsJson = fn["arguments"]?.let {
                            try {
                                it.jsonPrimitive.content
                            } catch (_: Exception) {
                                it.toString()
                            }
                        } ?: "{}"
                    )
                )
            }
        }
        return LlmResponse(content, toolCalls)
    }

    companion object {
        /** Cap vision payloads: matches MAX_ATTACHMENTS_PER_MESSAGE. */
        const val MAX_VISION_IMAGES = 4

        const val OPENAI_BASE = "https://api.openai.com/v1"

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
