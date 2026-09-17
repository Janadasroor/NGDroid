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

/** Shared Http POST fun-type for testability: (url, headers, bodyJson) -> responseBodyString */
typealias HttpPost = (url: String, headers: Map<String, String>, bodyJson: String) -> String

/** Shared Http GET fun-type for testability: (url, headers) -> responseBodyString */
typealias HttpGet = (url: String, headers: Map<String, String>) -> String

/**
 * Shared Http SSE-stream fun-type for testability:
 * (url, headers, bodyJson, onEvent) -> Unit. Throws like [HttpPost].
 */
typealias HttpStream = (
    url: String,
    headers: Map<String, String>,
    bodyJson: String,
    onEvent: (SseEvent) -> Unit
) -> Unit

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class LlmImage(
    val mimeType: String,
    val base64: String,
    val name: String = ""
)

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    /** Vision payload: JPEG/PNG bytes as base64. Empty = text-only (old behavior). */
    val images: List<LlmImage> = emptyList()
)

data class LlmTool(
    val name: String,
    val description: String,
    val parametersJsonSchema: String
)

data class LlmRequest(
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val tools: List<LlmTool> = emptyList(),
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048
)

data class LlmResponse(
    val text: String,
    val toolCalls: List<ToolCall> = emptyList()
)

interface LlmProvider {
    val id: String
    val displayName: String
    val defaultModel: String
    suspend fun chat(req: LlmRequest): LlmResponse
    suspend fun listModels(apiKey: String): List<String>

    /**
     * Streaming chat: calls [onPartial] with the accumulated text as deltas
     * arrive, returns the full response. Default falls back to [chat] so
     * providers (and test fakes) without SSE support still emit one partial.
     */
    suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse {
        val full = chat(req)
        if (full.text.isNotEmpty()) onPartial(full.text)
        return full
    }
}

/** True when a provider failure looks like a vision rejection. Pure. */
fun isVisionRejection(message: String): Boolean {
    val low = message.lowercase()
    return ("image" in low || "vision" in low || "input_image" in low || "image_url" in low) &&
        ("support" in low || "unsupported" in low || "invalid" in low ||
            "reject" in low || "400" in low || "422" in low)
}
