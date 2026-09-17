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

package com.jnd.ngdroid.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

class GeminiProvider(
    private val http: HttpPost,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    /** SSE transport; null = non-streaming chat() with a single partial. */
    private val streamHttp: HttpStream? = null
) : LlmProvider {

    override val id: String = "gemini"
    override val displayName: String = "Google Gemini"
    override val defaultModel: String = "gemini-2.5-flash"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(req: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val body = buildRequestJson(req.systemPrompt, req.messages, req.temperature, req.maxTokens, req.tools)
        val respBody = http(url, mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json"), body)
        parseChatResponse(respBody)
    }

    override suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val client = HttpClients.listModelsClient()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${body.take(500)}")
            parseListModelsResponse(body)
        }
    }

    fun buildRequestJson(
        system: String,
        messages: List<ChatMessage>,
        temp: Double,
        maxTokens: Int,
        tools: List<LlmTool> = emptyList()
    ): String {
        // TOOL messages carry no function name (only toolCallId); correlate
        // them to the most recent unmatched ASSISTANT functionCall in order —
        // the orchestrator always appends each tool result right after its call.
        val pendingToolNames = ArrayDeque<String>()
        val contents = messages.map { m ->
            if (m.role == ChatRole.TOOL) {
                val name = pendingToolNames.removeFirstOrNull().orEmpty()
                buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", JsonArray(listOf(buildJsonObject {
                        put("functionResponse", buildJsonObject {
                            put("name", JsonPrimitive(name))
                            put("response", buildJsonObject {
                                put("result", JsonPrimitive(m.content))
                            })
                        })
                    })))
                }
            } else {
                val role = if (m.role == ChatRole.ASSISTANT) "model" else "user"
                val text = if (m.role == ChatRole.SYSTEM) "SYSTEM: ${m.content}" else m.content
                buildJsonObject {
                    put("role", JsonPrimitive(role))
                    val parts = mutableListOf<JsonObject>()
                    // Keep a text part even when empty so image-only turns stay valid.
                    parts.add(buildJsonObject { put("text", JsonPrimitive(text)) })
                    for (img in m.images.take(MAX_VISION_IMAGES)) {
                        parts.add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", JsonPrimitive(img.mimeType.ifBlank { "image/jpeg" }))
                                put("data", JsonPrimitive(img.base64))
                            })
                        })
                    }
                    for (tc in m.toolCalls) {
                        pendingToolNames.addLast(tc.name)
                        parts.add(buildJsonObject {
                            put("functionCall", buildJsonObject {
                                put("name", JsonPrimitive(tc.name))
                                val args = runCatching { json.parseToJsonElement(tc.argumentsJson) }
                                    .getOrElse { JsonPrimitive(tc.argumentsJson) }
                                put("args", args)
                            })
                        })
                    }
                    put("parts", JsonArray(parts))
                }
            }
        }
        val root = buildJsonObject {
            put("contents", JsonArray(contents))
            put("systemInstruction", buildJsonObject {
                put("parts", JsonArray(listOf(buildJsonObject { put("text", JsonPrimitive(system)) })))
            })
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(temp))
                put("maxOutputTokens", JsonPrimitive(maxTokens))
            })
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(listOf(buildJsonObject {
                    put("functionDeclarations", JsonArray(tools.map { buildFunctionDeclaration(it) }))
                })))
            }
        }
        return root.toString()
    }

    /** Gemini `functionDeclarations` tool shape. Pure. */
    private fun buildFunctionDeclaration(t: LlmTool): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(t.name))
        put("description", JsonPrimitive(t.description))
        val params = runCatching { json.parseToJsonElement(t.parametersJsonSchema) }.getOrElse {
            buildJsonObject { put("type", JsonPrimitive("object")) }
        }
        put("parameters", params)
    }

    /**
     * Streams `:streamGenerateContent` SSE chunks, accumulating part texts
     * live. functionCall parts are collected (last args win per name) so the
     * tool loop works like the other providers.
     */
    override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse =
        withContext(Dispatchers.IO) {
            val stream = streamHttp ?: return@withContext super.streamChat(req, onPartial)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
            val body = buildRequestJson(req.systemPrompt, req.messages, req.temperature, req.maxTokens, req.tools)
            val text = StringBuilder()
            val calls = linkedMapOf<String, String>()
            stream(url, mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json"), body) { ev ->
                val root = runCatching { json.parseToJsonElement(ev.data).jsonObject }
                    .getOrElse { return@stream }
                root["error"]?.jsonObject?.let { err ->
                    throw IllegalStateException(
                        err["message"]?.jsonPrimitive?.contentOrNull ?: "stream error"
                    )
                }
                root["candidates"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.forEach { part ->
                        val obj = part.jsonObject
                        (obj["text"] as? JsonPrimitive)?.contentOrNull?.let {
                            if (it.isNotEmpty()) {
                                text.append(it)
                                onPartial(text.toString())
                            }
                        }
                        obj["functionCall"]?.jsonObject?.let { fn ->
                            val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                            calls[name] = fn["args"]?.toString() ?: "{}"
                        }
                    }
            }
            LlmResponse(
                text.toString(),
                calls.map { (name, args) -> ToolCall(id = "", name = name, argumentsJson = args) }
            )
        }

    fun parseChatResponse(bodyJson: String): LlmResponse {
        val root = json.parseToJsonElement(bodyJson).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return LlmResponse("", emptyList())
        if (candidates.isEmpty()) return LlmResponse("", emptyList())
        val content = candidates[0].jsonObject["content"]?.jsonObject ?: return LlmResponse("", emptyList())
        val parts = content["parts"]?.jsonArray ?: JsonArray(emptyList())
        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
        val toolCalls = parts.mapNotNull { part ->
            val fn = part.jsonObject["functionCall"]?.jsonObject ?: return@mapNotNull null
            val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ToolCall(id = "", name = name, argumentsJson = fn["args"]?.toString() ?: "{}")
        }
        return LlmResponse(text, toolCalls)
    }

    companion object {
        const val MAX_VISION_IMAGES = 4

        fun parseListModelsResponse(bodyJson: String): List<String> {
            val json = Json { ignoreUnknownKeys = true }
            val root = runCatching { json.parseToJsonElement(bodyJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val models = root["models"]?.jsonArray ?: return emptyList()
            return models.mapNotNull { el ->
                val obj = el.jsonObject
                val full = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                full.substringAfterLast("/")
            }
        }
    }
}
