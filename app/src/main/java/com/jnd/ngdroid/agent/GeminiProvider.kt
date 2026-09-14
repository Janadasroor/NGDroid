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
import okhttp3.OkHttpClient
import okhttp3.Request

class GeminiProvider(
    private val http: HttpPost,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) : LlmProvider {

    override val id: String = "gemini"
    override val displayName: String = "Google Gemini"
    override val defaultModel: String = "gemini-2.5-flash"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(req: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val body = buildRequestJson(req.systemPrompt, req.messages, req.temperature, req.maxTokens)
        val respBody = http(url, mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json"), body)
        parseChatResponse(respBody)
    }

    override suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
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
        maxTokens: Int
    ): String {
        val contents = messages.map { m ->
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
                put("parts", JsonArray(parts))
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
        }
        return root.toString()
    }

    fun parseChatResponse(bodyJson: String): LlmResponse {
        val root = json.parseToJsonElement(bodyJson).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return LlmResponse("", emptyList())
        if (candidates.isEmpty()) return LlmResponse("", emptyList())
        val content = candidates[0].jsonObject["content"]?.jsonObject ?: return LlmResponse("", emptyList())
        val parts = content["parts"]?.jsonArray ?: JsonArray(emptyList())
        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
        return LlmResponse(text, emptyList())
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
