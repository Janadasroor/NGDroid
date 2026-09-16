package com.jnd.ngdroid.agent

data class AgentConfig(
    val maxIterations: Int = 8,
    /** Extra turns granted when the model ends with a promise but no payload. */
    val maxStubRetries: Int = 1,
    /** App-specific content policy (tool ids, stall phrases, nudge texts). */
    val contentPolicy: AgentContentPolicy = AgentContentPolicy(),
    /** Retries per provider call for transient errors (timeout/429/5xx/stall). */
    val maxErrorRetries: Int = 2,
    /** Linear backoff base between error retries (× attempt, plus jitter). */
    val errorRetryBaseDelayMs: Long = 2000L
)

/** App-specific content policy: which tools promise deliverables, which phrases read as stalls. */
data class AgentContentPolicy(
    val contentToolNames: Set<String> = DEFAULT_CONTENT_TOOLS,
    val promisePhrases: List<String> = DEFAULT_PROMISE_PHRASES,
    val stubRetryNudge: String = STUB_RETRY_NUDGE,
    val stalledTrailer: String = STALLED_TRAILER,
    val emptyRetryNudge: String = EMPTY_RETRY_NUDGE
)

/** Tools whose use promises deliverables (netlist, links, images, files) in the reply. */
private val DEFAULT_CONTENT_TOOLS = setOf(
    "web_search", "fetch_url", "curl_fetch", "image_search",
    "netlist_template", "validate_netlist", "apply_netlist", "run_simulation",
    "render_plot",
    "download_file", "read_file", "list_files", "read_skill"
)

/** Cap vision images injected per tool result (token saver). */
const val MAX_TOOL_IMAGES = 1

private val DEFAULT_PROMISE_PHRASES = listOf(
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
fun finalHasPayload(text: String, policy: AgentContentPolicy = AgentContentPolicy()): Boolean {
    if ("```" in text) return true
    if (Regex("""!\[[^\]]*]\([^)]+\)""").containsMatchIn(text)) return true
    if (Regex("""https?://""").containsMatchIn(text)) return true
    val lower = text.lowercase()
    if (HostDefaults.containsSavedFileMention(lower)) return true
    if (Regex("""saved .+\.(pdf|png|jpe?g|gif|webp|svg|ico|zip|csv|txt|md|json)""").containsMatchIn(lower)) return true
    return false
}

/**
 * True when the final reply reads like a promise ("coming up", "I'll pull…",
 * trailing "…:") yet contains no netlist, link, or image. Pure; JVM-testable.
 */
fun isPromiseWithoutPayload(text: String, policy: AgentContentPolicy = AgentContentPolicy()): Boolean {
    if (finalHasPayload(text, policy)) return false
    val lower = text.trim().lowercase()
    if (lower.isEmpty()) return false
    if (policy.promisePhrases.any { it in lower }) return true
    return lower.endsWith(":")
}

/**
 * Provider died mid-run: never present a stale promise ("Building your
 * Colpitts…") as the final answer — unless the partial already carries a
 * payload (code block, links), which stays useful. Pure; JVM-testable.
 */
fun finalAfterError(
    lastText: String,
    friendly: String,
    contentToolUsed: Boolean,
    policy: AgentContentPolicy = AgentContentPolicy()
): String =
    if (lastText.isBlank() || (contentToolUsed && isPromiseWithoutPayload(lastText, policy))) friendly
    else lastText

/**
 * Repairs a resumed conversation so strict providers accept it: merges
 * consecutive same-role turns and drops leading orphan TOOL observations.
 * TOOL turns never merge — each keeps its tool_call_id for pairing
 * (Anthropic rejects mismatches). Pure; JVM-testable.
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

/** Nudge appended as a user turn when the model returns blank after tools ran. */
const val EMPTY_RETRY_NUDGE: String =
    "Your last reply came back empty. Answer now in plain text using the tool " +
        "results already in this conversation: include the validated netlist " +
        "in a fenced code block plus links or file locations. " +
        "Do not call more tools, do not end with a promise."

/** Shown when the model stays empty even after the nudge (budget spent). */
const val EMPTY_FINAL_FALLBACK: String =
    "The model returned an empty reply after running tools — tap Regenerate " +
        "to retry; the tool results above are kept."

/**
 * False for failures no retry can fix (bad key, unsupported model/route):
 * surfacing immediately beats burning quota and the user's time. Everything
 * else (timeouts, resets, 429/5xx/400-route flakes, stalls) is worth
 * another attempt. Pure.
 */
fun isRetryableError(message: String?): Boolean {
    val low = message.orEmpty().lowercase()
    if (low.isBlank()) return true
    val fatal = listOf(
        "autherror", "missing api key", "unauthorized", "invalid api key",
        "http 401", "[401]", "(401)",
        "missingsessionid", "modelerror", "is not supported",
        // Rate limits need minutes, not seconds: fail fast with the lane
        // guidance instead of burning attempts that will also 429.
        " 429", "[429]", "(429)", "rate limit", "ratelimit", "freeusagelimit"
    )
    return fatal.none { it in low }
}
/**
 * True for HTTP 400/422 rejections. Gateways often return these without any
 * image/vision wording (e.g. `Upstream request failed: [400] Provider
 * returned error`), so they must count as vision rejections on their own
 * when the failed request carried images. Pure.
 */
fun isBadRequest(message: String): Boolean {
    val low = message.lowercase()
    return "400" in low || "422" in low
}

/** Nudge appended as a user turn when the guard fires. */
const val STUB_RETRY_NUDGE: String =
    "Your last reply promises results but contains no netlist code block, " +
        "link, image, or saved file. Deliver them now in this reply: include the validated " +
        "netlist in a fenced code block and any links, " +
            "![description](image-url) images, or ${HostDefaults.DOWNLOAD_DIR_LABEL} file locations. " +
        "Do not end with another promise."

/** Trailer for a final answer that is a bare promise after acting: points at Regenerate. */
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

        // Single provider attempt with the vision fallback baked in: a model
        // rejecting images still answers from the text metadata. Throws on
        // any failure the caller (fetchWithRetry) classifies.
        suspend fun attemptCall(req: LlmRequest): LlmResponse {
            try {
                return provider.streamChat(req, forwardPartial)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = e.message.orEmpty()
                val hasImages = conversation.any { it.images.isNotEmpty() }
                // Bare 400/422s count too (see isBadRequest).
                if (hasImages && (isVisionRejection(msg) || isBadRequest(msg))) {
                    for (i in conversation.indices) {
                        if (conversation[i].images.isNotEmpty()) {
                            conversation[i] = conversation[i].copy(images = emptyList())
                        }
                    }
                    return provider.streamChat(
                        req.copy(messages = conversation.toList()),
                        forwardPartial
                    )
                }
                throw e
            }
        }

        // Bounded retries with linear backoff for transient failures, so one
        // blip (timeout, 429, 5xx, stall) doesn't kill the whole turn.
        // Cancellation always propagates — Stop keeps working mid-backoff.
        suspend fun fetchWithRetry(req: LlmRequest): LlmResponse {
            var attempt = 0
            while (true) {
                try {
                    return attemptCall(req)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isRetryableError(e.message) || attempt >= config.maxErrorRetries) throw e
                    attempt++
                    onEvent(
                        AgentEvent.Error(
                            "Request faltered — retrying ($attempt/${config.maxErrorRetries})…"
                        )
                    )
                    val jitter = kotlin.random.Random.nextLong(0, 1000)
                    kotlinx.coroutines.delay(config.errorRetryBaseDelayMs * attempt + jitter)
                }
            }
        }

        while (iterations < config.maxIterations + stubRetries) {
            iterations++
            val req = LlmRequest(
                systemPrompt = systemPrompt,
                messages = conversation.toList(),
                tools = llmTools
            )
            val resp: LlmResponse = try {
                fetchWithRetry(req)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onEvent(AgentEvent.Error("Provider error: ${e.message}"))
                val friendly = errorFormatter(e.message ?: e.javaClass.simpleName)
                return finalAfterError(lastText, friendly, contentToolUsed, config.contentPolicy)
            }
            if (resp.text.isNotBlank()) lastText = resp.text

            if (resp.toolCalls.isEmpty()) {
                val text = resp.text.ifBlank { lastText.ifBlank { "(empty response)" } }
                // Never show "(empty response)": strip images, nudge once for
                // a text-only answer from the results already in context.
                if (resp.text.isBlank() && lastText.isBlank() &&
                    stubRetries < config.maxStubRetries && (toolAttempted || contentToolUsed)
                ) {
                    stubRetries++
                    for (i in conversation.indices) {
                        if (conversation[i].images.isNotEmpty()) {
                            conversation[i] = conversation[i].copy(images = emptyList())
                        }
                    }
                    // No blank ASSISTANT turn: null-content assistant messages
                    // trip strict gateways — the USER nudge alone continues.
                    conversation.add(ChatMessage(ChatRole.USER, config.contentPolicy.emptyRetryNudge))
                    continue
                }
                if (resp.text.isBlank() && lastText.isBlank()) return EMPTY_FINAL_FALLBACK
                // Stub guard: tools ran but the "final" is a promise with no
                // payload — grant one nudge turn instead.
                if (stubRetries < config.maxStubRetries &&
                    contentToolUsed && isPromiseWithoutPayload(text, config.contentPolicy)
                ) {
                    stubRetries++
                    conversation.add(ChatMessage(ChatRole.ASSISTANT, resp.text))
                    conversation.add(ChatMessage(ChatRole.USER, config.contentPolicy.stubRetryNudge))
                    continue
                }
                // Stalled run: the model acted but the final is a bare
                // promise — say so and point at Regenerate.
                if (toolAttempted && isPromiseWithoutPayload(text, config.contentPolicy)) {
                    val out = "$text\n\n_${config.contentPolicy.stalledTrailer}_"
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
                if (tc.name in config.contentPolicy.contentToolNames) contentToolUsed = true
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
            val finalResp = fetchWithRetry(finalReq)
            val text = finalResp.text.ifBlank { lastText }
            if (text.isNotBlank()) onEvent(AgentEvent.Message(text))
            text.ifBlank { "Stopped after ${config.maxIterations} iterations without a final answer." }
        } catch (e: Exception) {
            onEvent(AgentEvent.Error("Final-answer error: ${e.message}"))
            finalAfterError(lastText, errorFormatter(e.message ?: e.javaClass.simpleName), contentToolUsed, config.contentPolicy)
        }
    }
}
