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
 * OpenCode Zen provider, cloud-direct (`https://opencode.ai/zen/v1`).
 *
 * Live gateway behavior (verified with curl, see /tmp/fix.md):
 * - Free-tier prompt requests are gated on the presence of
 *   `x-opencode-session: <any value>` (MissingSessionID without it).
 * - No user key needed for free models: `Bearer public` routes the same as
 *   keyless (FreeUsageLimitError rather than AuthError).
 * - Real API key required for non-free models (else AuthError "Missing API key").
 * - `muse-spark-*-free` only serve `/responses` (HTTP 500 on /chat/completions,
 *   works fine on /responses) — so this provider routes per model family:
 *   spark → `/responses` (native tool_calls parsed back), everything else →
 *   `/chat/completions`.
 */
class ZenProvider(
    private val http: HttpPost,
    private val apiKey: String,
    private val model: String = "big-pickle",
    private val sessionId: String? = DEFAULT_SESSION_ID,
    /** Override for sibling gateways (OpenCode Go shares this protocol). */
    private val baseUrl: String = ZEN_BASE,
    /** SSE transport; null = non-streaming chat() with a single partial. */
    private val streamHttp: HttpStream? = null
) : LlmProvider {

    override val id: String = "opencode-zen"
    override val displayName: String = "OpenCode Zen"
    override val defaultModel: String = "big-pickle"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(req: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val requested = normalizeModelId(model)
        // Free tier without a user key: gateway accepts `public` as the bearer.
        val bearer = apiKey.ifBlank { PUBLIC_BEARER }
        val headers = mutableMapOf(
            "Authorization" to "Bearer $bearer",
            "Content-Type" to "application/json"
        )
        if (!sessionId.isNullOrBlank()) {
            headers["x-opencode-session"] = sessionId
        }
        if (isResponsesOnlyModel(requested)) {
            val url = "$baseUrl/responses"
            val body = buildResponsesJson(requested, req.systemPrompt, req.messages, req.tools, req.temperature, req.maxTokens)
            parseResponsesResponse(http(url, headers, body))
        } else {
            val url = "$baseUrl/chat/completions"
            val body = buildRequestJson(requested, req.systemPrompt, req.messages, req.tools, req.temperature, req.maxTokens)
            parseChatResponse(http(url, headers, body))
        }
    }

    override suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$baseUrl/models")
            .get()
        // /models is public on Zen: only send auth when we have a key.
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        } else if (this@ZenProvider.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${this@ZenProvider.apiKey}")
        }
        if (!sessionId.isNullOrBlank()) {
            builder.header("x-opencode-session", sessionId)
        }
        val client = OkHttpClient()
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${body.take(500)}")
            parseListModelsResponse(body)
        }
    }

    override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse =
        withContext(Dispatchers.IO) {
            val stream = streamHttp ?: return@withContext super.streamChat(req, onPartial)
            val requested = normalizeModelId(model)
            val bearer = apiKey.ifBlank { PUBLIC_BEARER }
            val headers = mutableMapOf(
                "Authorization" to "Bearer $bearer",
                "Content-Type" to "application/json"
            )
            if (!sessionId.isNullOrBlank()) {
                headers["x-opencode-session"] = sessionId
            }
            if (isResponsesOnlyModel(requested)) {
                val url = "$baseUrl/responses"
                val body = buildResponsesJson(
                    requested, req.systemPrompt, req.messages, req.tools,
                    req.temperature, req.maxTokens, stream = true
                )
                streamResponses(url, headers, body, onPartial)
            } else {
                val url = "$baseUrl/chat/completions"
                val body = buildRequestJson(
                    requested, req.systemPrompt, req.messages, req.tools,
                    req.temperature, req.maxTokens, stream = true
                )
                val acc = ChatStreamAccumulator(onPartial)
                stream(url, headers, body) { acc.accept(it) }
                acc.response()
            }
        }

    /**
     * Streams a `/responses` turn: `output_text.delta` fragments for live
     * text, `output_item.added` + `function_call_arguments.delta` for tool
     * calls. Throws on failure events so the orchestrator formats them.
     */
    private fun streamResponses(
        url: String,
        headers: Map<String, String>,
        body: String,
        onPartial: (String) -> Unit
    ): LlmResponse {
        val json = Json { ignoreUnknownKeys = true }
        val text = StringBuilder()
        val ids = mutableMapOf<Int, String>()
        val names = mutableMapOf<Int, String>()
        val args = mutableMapOf<Int, StringBuilder>()
        val stream = streamHttp!!
        stream(url, headers, body) { ev ->
            if (ev.event == "error") {
                val msg = runCatching { json.parseToJsonElement(ev.data).jsonObject }
                    .getOrNull()?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: "stream error"
                throw IllegalStateException(msg)
            }
            val root = runCatching { json.parseToJsonElement(ev.data).jsonObject }
                .getOrElse { return@stream }
            if (root["type"]?.jsonPrimitive?.contentOrNull == "error") {
                val msg = root["error"]?.jsonObject
                    ?.get("message")?.jsonPrimitive?.contentOrNull ?: "stream error"
                throw IllegalStateException(msg)
            }
            val index = root["output_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            when (ev.event) {
                "response.output_text.delta" -> {
                    root["delta"]?.jsonPrimitive?.contentOrNull?.let {
                        if (it.isNotEmpty()) {
                            text.append(it)
                            onPartial(text.toString())
                        }
                    }
                }
                "response.output_item.added" -> {
                    root["item"]?.jsonObject?.let { item ->
                        if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                            (item["call_id"]?.jsonPrimitive?.contentOrNull
                                ?: item["id"]?.jsonPrimitive?.contentOrNull)?.let {
                                if (it.isNotEmpty()) ids[index] = it
                            }
                            item["name"]?.jsonPrimitive?.contentOrNull?.let {
                                if (it.isNotEmpty()) names[index] = it
                            }
                        }
                    }
                }
                "response.function_call_arguments.delta" -> {
                    root["delta"]?.jsonPrimitive?.contentOrNull?.let {
                        args.getOrPut(index) { StringBuilder() }.append(it)
                    }
                }
            }
        }
        return LlmResponse(
            text.toString(),
            (ids.keys + names.keys + args.keys).sorted().map {
                ToolCall(ids[it].orEmpty(), names[it].orEmpty(), args[it]?.toString() ?: "{}")
            }
        )
    }

    /** Pure, testable OpenAI-compatible chat/completions request builder. */
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
            put("model", JsonPrimitive(normalizeModelId(model)))
            put("messages", JsonArray(msgs))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools.map { buildChatFunctionTool(it) }))
                put("tool_choice", JsonPrimitive("auto"))
            }
            put("temperature", JsonPrimitive(temp))
            put("max_tokens", JsonPrimitive(maxTokens))
            if (stream) put("stream", JsonPrimitive(true))
        }
        return root.toString()
    }

    /**
     * Pure, testable `/responses` request builder.
     * Verified live: `input` as role/content list + `tools` with
     * `{type:function,name,description,parameters}` works (status completed).
     */
    fun buildResponsesJson(
        model: String,
        system: String,
        messages: List<ChatMessage>,
        tools: List<LlmTool>,
        temp: Double,
        maxTokens: Int,
        stream: Boolean = false
    ): String {
        val input = mutableListOf<JsonObject>()
        if (system.isNotBlank()) {
            input.add(buildJsonObject {
                put("role", JsonPrimitive("system"))
                put("content", JsonPrimitive(system))
            })
        }
        for (m in messages) {
            when (m.role) {
                ChatRole.TOOL -> {
                    // /responses has no tool role: feed observations back as user text.
                    input.add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive("Tool result: ${m.content}"))
                    })
                }
                else -> input.add(buildResponsesMessage(m))
            }
        }
        val root = buildJsonObject {
            put("model", JsonPrimitive(normalizeModelId(model)))
            put("input", JsonArray(input))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools.map { buildResponsesFunctionTool(it) }))
                put("tool_choice", JsonPrimitive("auto"))
            }
            put("temperature", JsonPrimitive(temp))
            put("max_output_tokens", JsonPrimitive(maxTokens))
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
                // OpenAI vision shape: content parts with text + image_url data URIs.
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

    /**
     * `/responses` message shape. Text-only stays a plain string (old behavior);
     * vision turns content into input_text + input_image parts.
     */
    fun buildResponsesMessage(m: ChatMessage): JsonObject {
        val role = when (m.role) {
            ChatRole.SYSTEM -> "system"
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
            ChatRole.TOOL -> "user"
        }
        return buildJsonObject {
            put("role", JsonPrimitive(role))
            if (m.images.isNotEmpty() && (m.role == ChatRole.USER || m.role == ChatRole.SYSTEM)) {
                val parts = mutableListOf<JsonObject>()
                if (m.content.isNotEmpty()) {
                    parts.add(buildJsonObject {
                        put("type", JsonPrimitive("input_text"))
                        put("text", JsonPrimitive(m.content))
                    })
                }
                for (img in m.images.take(MAX_VISION_IMAGES)) {
                    val url = "data:${img.mimeType.ifBlank { "image/jpeg" }};base64,${img.base64}"
                    parts.add(buildJsonObject {
                        put("type", JsonPrimitive("input_image"))
                        put("image_url", JsonPrimitive(url))
                    })
                }
                put("content", JsonArray(parts))
            } else {
                if (m.content.isNotEmpty()) put("content", JsonPrimitive(m.content))
                else put("content", JsonNull)
            }
        }
    }

    /** Strip vision payloads for a text-only retry when a model rejects images. Pure. */
    fun stripImages(messages: List<ChatMessage>): List<ChatMessage> =
        messages.map { if (it.images.isEmpty()) it else it.copy(images = emptyList()) }

    /** True when a provider failure looks like a vision rejection. Pure. */
    fun isVisionRejection(message: String): Boolean {
        val low = message.lowercase()
        return ("image" in low || "vision" in low || "input_image" in low || "image_url" in low) &&
            ("support" in low || "unsupported" in low || "invalid" in low ||
                "reject" in low || "400" in low || "422" in low)
    }

    /**
     * OpenAI `chat/completions` tool shape: `{"type":"function","function":{...}}`.
     * The gateway rejects the flat `/responses` shape here with
     * HTTP 400 `Upstream request failed: [400] Provider returned error`
     * (verified live on nemotron-3-ultra-free).
     */
    private fun buildChatFunctionTool(t: LlmTool): JsonObject {
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

    /**
     * OpenAI `/responses` tool shape: flat `{type:function,name,description,parameters}`.
     * Verified live on muse-spark-1.3-contributor-free (HTTP 200).
     */
    private fun buildResponsesFunctionTool(t: LlmTool): JsonObject {
        return buildJsonObject {
            put("type", JsonPrimitive("function"))
            put("name", JsonPrimitive(t.name))
            put("description", JsonPrimitive(t.description))
            val paramsEl = runCatching { json.parseToJsonElement(t.parametersJsonSchema) }.getOrElse {
                buildJsonObject { put("type", JsonPrimitive("object")) }
            }
            put("parameters", paramsEl)
        }
    }

    fun parseChatResponse(bodyJson: String): LlmResponse {
        val root = json.parseToJsonElement(bodyJson).jsonObject
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

    /**
     * Parse a `/responses` payload: concatenate `output[].content[].text`
     * (type `output_text`) and translate native `function_call` items back to
     * [ToolCall] so the ReAct loop works unchanged.
     */
    fun parseResponsesResponse(bodyJson: String): LlmResponse {
        val root = runCatching { json.parseToJsonElement(bodyJson).jsonObject }.getOrNull()
            ?: return LlmResponse("", emptyList())
        // Gateway errors come back as {"type":"error","error":{...}} — surface text.
        val type = root["type"]?.jsonPrimitive?.contentOrNull
        if (type == "error") {
            val err = root["error"]?.jsonObject
            val msg = err?.get("message")?.jsonPrimitive?.contentOrNull ?: "unknown error"
            return LlmResponse("", emptyList()).copy(text = "Gateway error: $msg")
        }
        val output = root["output"]?.jsonArray ?: return LlmResponse("", emptyList())
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        for (item in output) {
            val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "message" -> {
                    val content = obj["content"]?.jsonArray ?: continue
                    for (part in content) {
                        val p = runCatching { part.jsonObject }.getOrNull() ?: continue
                        if (p["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                            p["text"]?.jsonPrimitive?.contentOrNull?.let { text.append(it) }
                        }
                    }
                }
                "function_call" -> {
                    toolCalls.add(
                        ToolCall(
                            id = obj["call_id"]?.jsonPrimitive?.contentOrNull
                                ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                            argumentsJson = obj["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                        )
                    )
                }
            }
        }
        return LlmResponse(text.toString(), toolCalls)
    }

    companion object {
        /** Cap vision payloads: matches MAX_ATTACHMENTS_PER_MESSAGE. */
        const val MAX_VISION_IMAGES = 4

        /** Cloud Zen API base — the only endpoint used. No localhost/shim. */
        const val ZEN_BASE = "https://opencode.ai/zen/v1"

        /** Free-tier gateway checks presence, not value. */
        const val DEFAULT_SESSION_ID = "spiceagent-01"

        /**
         * Free tier without a user key: the gateway accepts `public` as bearer.
         * Verified live: `Bearer public` and no-auth route identically on
         * /chat/completions (FreeUsageLimitError rather than AuthError).
         */
        const val PUBLIC_BEARER = "public"

        const val UNSUPPORTED_RESPONSES_ONLY_MODEL = "muse-spark-1.3-contributor-free"

        /**
         * True for models that serve the /responses endpoint (`muse-spark-*`, `gpt-*`, `grok-*`).
         * Proven by curl: `muse-spark` returns HTTP 500 on /chat/completions and 200 on /responses.
         */
        fun isResponsesOnlyModel(id: String): Boolean {
            val low = id.trim().lowercase()
            return low.startsWith("muse-spark-") ||
                   low.startsWith("gpt-") ||
                   low.startsWith("grok-")
        }

        /**
         * Normalize model ids: trim + strip optional vendor prefixes
         * (zen/, opencode/, opencode-go/, go/) so pasted ids still work.
         */
        fun normalizeModelId(model: String): String {
            var m = model.trim()
            val prefixes = listOf("zen/", "opencode/", "opencode-go/", "go/")
            var changed = true
            while (changed) {
                changed = false
                val low = m.lowercase()
                for (p in prefixes) {
                    if (low.startsWith(p)) {
                        m = m.substring(p.length)
                        changed = true
                        break
                    }
                }
            }
            return m
        }

        fun parseListModelsResponse(bodyJson: String): List<String> {
            val json = Json { ignoreUnknownKeys = true }
            val root = runCatching { json.parseToJsonElement(bodyJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val data = root["data"]?.jsonArray ?: return emptyList()
            // Keep spark models in the catalog: they work, just via /responses.
            return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        }

        /**
         * Verified dead upstream (HTTP 400 "Model is unavailable" on every
         * route) — never auto-picked as the default. Listed explicitly so a
         * fresh install doesn't land on a broken model.
         */
        val DEAD_DEFAULT_DENYLIST = setOf("deepseek-v4-flash-free")

        /**
         * First auto-pickable free id: ranked alphabetically, skipping
         * denylisted dead ids (falls back to plain first when all are dead).
         * Pure, testable.
         */
        fun autoDefault(freeIds: List<String>): String? {
            val ranked = freeIds.map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedBy { it.lowercase() }
            return ranked.firstOrNull { it.lowercase() !in DEAD_DEFAULT_DENYLIST }
                ?: ranked.firstOrNull()
        }

        /**
         * Live free-tier detection: ids ending in `-free` (case-insensitive).
         * No hardcoded model list needed.
         */
        fun parseFreeModelIds(bodyJson: String): List<String> {
            return parseListModelsResponse(bodyJson)
                .filter { it.lowercase().endsWith("-free") }
        }
    }
}
