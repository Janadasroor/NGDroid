package com.jnd.ngdroid.agent

/** Shared Http POST fun-type for testability: (url, headers, bodyJson) -> responseBodyString */
typealias HttpPost = (url: String, headers: Map<String, String>, bodyJson: String) -> String

/** Shared Http GET fun-type for testability: (url, headers) -> responseBodyString */
typealias HttpGet = (url: String, headers: Map<String, String>) -> String

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

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
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
}
