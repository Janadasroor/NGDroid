package com.jnd.ngdroid.agent

data class AgentConfig(
    val maxIterations: Int = 8
)

sealed interface AgentEvent {
    data class Message(val text: String) : AgentEvent
    data class ToolCallEvent(val name: String, val argsJson: String) : AgentEvent
    data class Observation(val toolName: String, val output: String) : AgentEvent
    data class Error(val message: String) : AgentEvent
}

/**
 * ReAct loop: Reason (LLM) -> Act (tools) -> Observe (feed back).
 * Iterates until the LLM returns a final answer with no tool calls, or
 * [AgentConfig.maxIterations] is reached (then forces a final answer with tools removed).
 */
class AgentOrchestrator(
    private val config: AgentConfig = AgentConfig(),
    private val provider: LlmProvider,
    private val tools: ToolRegistry
) {
    suspend fun run(
        userMessage: String,
        history: List<ChatMessage> = emptyList(),
        onEvent: (AgentEvent) -> Unit = {}
    ): String {
        val conversation = history.toMutableList()
        conversation.add(ChatMessage(ChatRole.USER, userMessage))
        onEvent(AgentEvent.Message(userMessage))

        val llmTools = tools.list().map { LlmTool(it.name, it.description, it.parametersJsonSchema) }
        var lastText = ""
        var iterations = 0

        while (iterations < config.maxIterations) {
            iterations++
            val req = LlmRequest(
                systemPrompt = SPICE_SYSTEM,
                messages = conversation.toList(),
                tools = llmTools
            )
            val resp: LlmResponse = try {
                provider.chat(req)
            } catch (e: Exception) {
                onEvent(AgentEvent.Error("Provider error: ${e.message}"))
                return lastText.ifBlank { "Provider error: ${e.message}" }
            }
            if (resp.text.isNotBlank()) lastText = resp.text

            if (resp.toolCalls.isEmpty()) {
                if (resp.text.isNotBlank()) onEvent(AgentEvent.Message(resp.text))
                return resp.text.ifBlank { lastText.ifBlank { "(empty response)" } }
            }

            conversation.add(
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = resp.text,
                    toolCalls = resp.toolCalls
                )
            )

            for (tc in resp.toolCalls) {
                onEvent(AgentEvent.ToolCallEvent(tc.name, tc.argumentsJson))
                val tool = tools.get(tc.name)
                val output = if (tool == null) {
                    "ERROR: unknown tool '${tc.name}'"
                } else {
                    try {
                        tool.execute(tc.argumentsJson)
                    } catch (e: Exception) {
                        "ERROR: ${e.message}"
                    }
                }
                onEvent(AgentEvent.Observation(tc.name, output))
                conversation.add(
                    ChatMessage(
                        role = ChatRole.TOOL,
                        content = output,
                        toolCallId = tc.id.ifBlank { null }
                    )
                )
            }
        }

        return try {
            val finalReq = LlmRequest(
                systemPrompt = "$SPICE_SYSTEM\n\nYou have reached the tool-call limit. " +
                    "Provide your best final answer now as plain text with no further tool calls.",
                messages = conversation.toList(),
                tools = emptyList()
            )
            val finalResp = provider.chat(finalReq)
            val text = finalResp.text.ifBlank { lastText }
            if (text.isNotBlank()) onEvent(AgentEvent.Message(text))
            text.ifBlank { "Stopped after ${config.maxIterations} iterations without a final answer." }
        } catch (e: Exception) {
            onEvent(AgentEvent.Error("Final-answer error: ${e.message}"))
            lastText.ifBlank { "Stopped after ${config.maxIterations} iterations: ${e.message}" }
        }
    }
}
