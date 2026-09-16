package com.jnd.ngdroid.agent

data class AgentConfig(
    val maxIterations: Int = 8,
    /** Extra turns granted when the model ends with a promise but no payload. */
    val maxStubRetries: Int = 1
)

/** Tools whose use promises deliverables (netlist, links, images, files) in the reply. */
private val contentToolNames = setOf(
    "web_search", "fetch_url", "curl_fetch", "image_search",
    "netlist_template", "validate_netlist", "apply_netlist", "run_simulation",
    "render_plot",
    "download_file", "read_file", "list_files", "read_skill"
)

/** Cap vision images injected per tool result (token saver). */
const val MAX_TOOL_IMAGES = 1

private val promisePhrases = listOf(
    "coming up", "coming right up", "i'll pull", "i'll fetch", "i'll get",
    "i'll put together", "let me ", "one moment", "one sec", "moment please",
    "building your", "putting together", "putting that", "working on it",
    "stay tuned", "bear with", "fetching", "gathering", "looking that up",
    "looking it up", "looking into", "on it", "right away", "give me a",
    "hold on", "hold tight", "hang on", "stand by", "in a moment",
    "in just a moment", "shortly", "almost done", "almost ready",
    "just a sec", "pulling", "downloading", "grabbing", "getting them",
    "getting those", "getting that", "getting it", "preparing", "assembling",
    "compiling", "be right back", "back in a", "will pull", "will fetch",
    "will get", "will grab", "will put"
)

/** True when the text carries something usable: code, image, link, or saved file. Pure. */
fun finalHasPayload(text: String): Boolean {
    if ("```" in text) return true
    if (Regex("""!\[[^\]]*]\([^)]+\)""").containsMatchIn(text)) return true
    if (Regex("""https?://""").containsMatchIn(text)) return true
    val lower = text.lowercase()
    if ("downloads/ngdroid" in lower || "assistant_files" in lower) return true
    if (Regex("""saved .+\.(pdf|png|jpe?g|gif|webp|svg|ico|zip|csv|txt|md|json)""").containsMatchIn(lower)) return true
    return false
}

/**
 * True when the final reply reads like a promise ("coming up", "I'll pull…",
 * trailing "…:") yet contains no netlist, link, or image. Pure; JVM-testable.
 */
fun isPromiseWithoutPayload(text: String): Boolean {
    if (finalHasPayload(text)) return false
    val lower = text.trim().lowercase()
    if (lower.isEmpty()) return false
    if (promisePhrases.any { it in lower }) return true
    return lower.endsWith(":")
}

/**
 * Provider died mid-run: never present a stale promise ("Building your
 * Colpitts…") as the final answer. A partial that already carries a payload
 * (code block, links) is still useful, so only bare promises are replaced
 * by the friendly error. Pure; JVM-testable.
 */
fun finalAfterError(lastText: String, friendly: String, contentToolUsed: Boolean): String =
    if (lastText.isBlank() || (contentToolUsed && isPromiseWithoutPayload(lastText))) friendly
    else lastText

/**
 * Repairs a resumed conversation so strict providers accept it: merges
 * consecutive same-role turns (text joined, tool calls and images unioned)
 * and drops leading orphan TOOL observations. TOOL turns never merge —
 * each observation keeps its own tool_call_id for tool_use/tool_result
 * pairing (Anthropic rejects mismatched pairs). Pure; JVM-testable.
 */
fun repairHistory(history: List<ChatMessage>): List<ChatMessage> {
    val out = mutableListOf<ChatMessage>()
    for (m in history) {
        if (m.role == ChatRole.TOOL && out.isEmpty()) continue
        val last = out.lastOrNull()
        if (last != null && last.role == m.role && m.role != ChatRole.TOOL) {
            val text = when {
                last.content.isBlank() -> m.content
                m.content.isBlank() -> last.content
                else -> "${last.content}\n\n${m.content}"
            }
            out[out.lastIndex] = last.copy(
                content = text,
                toolCalls = last.toolCalls + m.toolCalls,
                toolCallId = last.toolCallId ?: m.toolCallId,
                images = last.images + m.images
            )
        } else {
            out.add(m)
        }
    }
    return out
}

/** Nudge appended as a user turn when the guard fires. */
const val STUB_RETRY_NUDGE: String =
    "Your last reply promises results but contains no netlist code block, " +
        "link, image, or saved file. Deliver them now in this reply: include the validated " +
        "netlist in a fenced code block and any links, " +
        "![description](image-url) images, or Downloads/NGDroid file locations. " +
        "Do not end with another promise."

/**
 * Trailer appended to a final answer that is a bare promise after the model
 * already tried to act (any tool call, even an unknown one): the run stalled
 * instead of delivering, so say so and point at Regenerate rather than
 * leaving a dead-end "coming up" message.
 */
const val STALLED_TRAILER: String =
    "The run stopped before delivering results — tap Regenerate to continue from here."

sealed interface AgentEvent {
    data class Message(val text: String) : AgentEvent
    /** Live streamed text (accumulated so far) from the current provider call. */
    data class Partial(val text: String) : AgentEvent
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
        onEvent: (AgentEvent) -> Unit = {},
        /** Maps raw provider failure text to user-visible text (default: passthrough). */
        errorFormatter: (String) -> String = { it },
        /** Base system prompt; custom skills are pre-appended by the caller. */
        systemPrompt: String = SPICE_SYSTEM,
        /** Vision payload for this turn (raster attachments as base64). History stays text-only. */
        userImages: List<LlmImage> = emptyList()
    ): String {
        val conversation = repairHistory(history).toMutableList()
        conversation.add(ChatMessage(ChatRole.USER, userMessage, images = userImages))
        onEvent(AgentEvent.Message(userMessage))

        val llmTools = tools.list().map { LlmTool(it.name, it.description, it.parametersJsonSchema) }
        var lastText = ""
        var iterations = 0
        var stubRetries = 0
        var contentToolUsed = false
        // Any tool call this run (even an unknown name): the model tried to
        // act, so a bare-promise final is a stall, not an answer.
        var toolAttempted = false
        val forwardPartial: (String) -> Unit = { onEvent(AgentEvent.Partial(it)) }

        while (iterations < config.maxIterations + stubRetries) {
            iterations++
            val req = LlmRequest(
                systemPrompt = systemPrompt,
                messages = conversation.toList(),
                tools = llmTools
            )
            val resp: LlmResponse = try {
                provider.streamChat(req, forwardPartial)
            } catch (e: Exception) {
                // Vision fallback: a text-only model rejecting image_url/input_image
                // should still answer from the metadata instead of hard-failing.
                val msg = e.message.orEmpty()
                if (conversation.any { it.images.isNotEmpty() } && isVisionRejection(msg)) {
                    for (i in conversation.indices) {
                        if (conversation[i].images.isNotEmpty()) {
                            conversation[i] = conversation[i].copy(images = emptyList())
                        }
                    }
                    try {
                        provider.streamChat(
                            LlmRequest(
                                systemPrompt = systemPrompt,
                                messages = conversation.toList(),
                                tools = llmTools
                            ),
                            forwardPartial
                        )
                    } catch (e2: Exception) {
                        onEvent(AgentEvent.Error("Provider error: ${e2.message}"))
                        val friendly = errorFormatter(e2.message ?: e2.javaClass.simpleName)
                        return finalAfterError(lastText, friendly, contentToolUsed)
                    }
                } else {
                    onEvent(AgentEvent.Error("Provider error: ${e.message}"))
                    val friendly = errorFormatter(e.message ?: e.javaClass.simpleName)
                    return finalAfterError(lastText, friendly, contentToolUsed)
                }
            }
            if (resp.text.isNotBlank()) lastText = resp.text

            if (resp.toolCalls.isEmpty()) {
                val text = resp.text.ifBlank { lastText.ifBlank { "(empty response)" } }
                // Stub guard: tools ran but the "final" reply is a promise with
                // no netlist, link, or image — grant one nudge turn instead of
                // showing "coming up" with nothing behind it.
                if (stubRetries < config.maxStubRetries &&
                    contentToolUsed && isPromiseWithoutPayload(text)
                ) {
                    stubRetries++
                    conversation.add(ChatMessage(ChatRole.ASSISTANT, resp.text))
                    conversation.add(ChatMessage(ChatRole.USER, STUB_RETRY_NUDGE))
                    continue
                }
                // Stalled run: the model tried to act (any tool call) but the
                // final is a bare promise — say so and point at Regenerate
                // instead of leaving a dead-end "coming up" message.
                if (toolAttempted && isPromiseWithoutPayload(text)) {
                    val out = "$text\n\n_${STALLED_TRAILER}_"
                    if (resp.text.isNotBlank()) onEvent(AgentEvent.Message(out))
                    return out
                }
                if (resp.text.isNotBlank()) onEvent(AgentEvent.Message(resp.text))
                return text
            }
            toolAttempted = true

            conversation.add(
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = resp.text,
                    toolCalls = resp.toolCalls
                )
            )

            for (tc in resp.toolCalls) {
                onEvent(AgentEvent.ToolCallEvent(tc.name, tc.argumentsJson))
                if (tc.name in contentToolNames) contentToolUsed = true
                val tool = tools.get(tc.name)
                val result = if (tool == null) {
                    ToolResult("ERROR: unknown tool '${tc.name}'")
                } else {
                    try {
                        tool.executeEx(tc.argumentsJson)
                    } catch (e: Exception) {
                        ToolResult("ERROR: ${e.message}")
                    }
                }
                onEvent(AgentEvent.Observation(tc.name, result.text))
                conversation.add(
                    ChatMessage(
                        role = ChatRole.TOOL,
                        content = result.text,
                        toolCallId = tc.id.ifBlank { null }
                    )
                )
                // TOOL images ride as a follow-up USER turn: every provider
                // already sends USER vision (image_url/inlineData/base64),
                // while TOOL vision shapes differ per API.
                if (result.images.isNotEmpty()) {
                    conversation.add(
                        ChatMessage(
                            role = ChatRole.USER,
                            content = "[${tc.name} image attached — shape check only; numbers come from samples:/vectors:]",
                            images = result.images.take(MAX_TOOL_IMAGES)
                        )
                    )
                }
            }
        }

        return try {
            val finalReq = LlmRequest(
                systemPrompt = "$systemPrompt\n\nYou have reached the tool-call limit. " +
                    "Provide your best final answer now as plain text with no further tool calls.",
                messages = conversation.toList(),
                tools = emptyList()
            )
            val finalResp = provider.streamChat(finalReq, forwardPartial)
            val text = finalResp.text.ifBlank { lastText }
            if (text.isNotBlank()) onEvent(AgentEvent.Message(text))
            text.ifBlank { "Stopped after ${config.maxIterations} iterations without a final answer." }
        } catch (e: Exception) {
            onEvent(AgentEvent.Error("Final-answer error: ${e.message}"))
            finalAfterError(lastText, errorFormatter(e.message ?: e.javaClass.simpleName), contentToolUsed)
        }
    }
}
