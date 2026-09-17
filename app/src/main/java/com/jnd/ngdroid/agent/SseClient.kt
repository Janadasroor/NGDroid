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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One server-sent event: optional event name + data payload. */
data class SseEvent(val event: String, val data: String)

/**
 * Parses one SSE block (lines between blank lines) into an event.
 * Multi-line data is joined with "\n"; comment lines are ignored.
 * Returns null for empty/comment-only blocks. Pure; JVM-testable.
 */
fun parseSseBlock(block: String): SseEvent? {
    var event = ""
    val data = mutableListOf<String>()
    for (raw in block.split('\n')) {
        val line = raw.trimEnd('\r')
        if (line.isEmpty() || line.startsWith(':')) continue
        when {
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> data.add(line.removePrefix("data:").removePrefix(" "))
            line == "data" -> data.add("")
        }
    }
    if (event.isEmpty() && data.isEmpty()) return null
    return SseEvent(event, data.joinToString("\n"))
}

/**
 * Splits a raw SSE payload into events. Pure — shared by the live
 * OkHttp reader and unit tests.
 */
fun parseSseEvents(raw: String): List<SseEvent> =
    raw.replace("\r\n", "\n").split("\n\n").mapNotNull { parseSseBlock(it) }

/**
 * Accumulates OpenAI-style `chat/completions` SSE deltas into text plus
 * index-keyed tool calls. Pure apart from [onPartial], which fires with
 * the full text so far.
 */
class ChatStreamAccumulator(private val onPartial: (String) -> Unit = {}) {
    private val json = Json { ignoreUnknownKeys = true }
    private val text = StringBuilder()
    private val ids = mutableMapOf<Int, String>()
    private val names = mutableMapOf<Int, String>()
    private val args = mutableMapOf<Int, StringBuilder>()

    /**
     * Feeds one event. Returns false when the stream is done (`[DONE]`);
     * throws on provider error objects so the orchestrator formats them.
     */
    fun accept(ev: SseEvent): Boolean {
        if (ev.data == "[DONE]") return false
        val root = runCatching { json.parseToJsonElement(ev.data).jsonObject }
            .getOrElse { return true }
        root["error"]?.jsonObject?.let { err ->
            throw IllegalStateException(
                err["message"]?.jsonPrimitive?.contentOrNull ?: "stream error"
            )
        }
        val delta = root["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("delta")?.jsonObject ?: return true
        (delta["content"] as? JsonPrimitive)?.contentOrNull?.let {
            if (it.isNotEmpty()) {
                text.append(it)
                onPartial(text.toString())
            }
        }
        delta["tool_calls"]?.jsonArray?.forEach { el ->
            val o = el.jsonObject
            val idx = (o["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            (o["id"] as? JsonPrimitive)?.contentOrNull?.let { if (it.isNotEmpty()) ids[idx] = it }
            o["function"]?.jsonObject?.let { fn ->
                (fn["name"] as? JsonPrimitive)?.contentOrNull?.let { if (it.isNotEmpty()) names[idx] = it }
                (fn["arguments"] as? JsonPrimitive)?.contentOrNull?.let {
                    args.getOrPut(idx) { StringBuilder() }.append(it)
                }
            }
        }
        return true
    }

    fun response(): LlmResponse = LlmResponse(
        text.toString(),
        ids.keys.sorted().map {
            ToolCall(ids[it].orEmpty(), names[it].orEmpty(), args[it]?.toString() ?: "{}")
        }
    )
}
