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

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Streams canned SSE events through a fake transport. */
class StreamProvidersTest {

    private fun fakeStream(events: List<SseEvent>, capturedBody: StringBuilder? = null): HttpStream =
        { _, _, body, onEvent ->
            capturedBody?.append(body)
            events.forEach(onEvent)
        }

    private fun userReq() = LlmRequest(
        systemPrompt = "s",
        messages = listOf(ChatMessage(ChatRole.USER, "hi"))
    )

    @Test
    fun openAiStreamsTextAndTools() = runTest {
        val body = StringBuilder()
        val stream = fakeStream(
            listOf(
                SseEvent("", """{"choices":[{"delta":{"content":"Hel"}}]}"""),
                SseEvent("", """{"choices":[{"delta":{"content":"lo","tool_calls":[{"index":0,"id":"c1","function":{"name":"web_search","arguments":"{}"}}]}}]}"""),
                SseEvent("", "[DONE]")
            ),
            body
        )
        val provider = OpenAiProvider(http = { _, _, _ -> "{}" }, apiKey = "K", streamHttp = stream)
        val partials = mutableListOf<String>()
        val resp = provider.streamChat(userReq(), partials::add)
        assertEquals(listOf("Hel", "Hello"), partials)
        assertEquals("Hello", resp.text)
        assertEquals(1, resp.toolCalls.size)
        assertEquals("web_search", resp.toolCalls[0].name)
        assertTrue(body.contains("\"stream\":true"))
    }

    @Test
    fun openAiFallsBackWithoutTransport() = runTest {
        val provider = OpenAiProvider(
            http = { _, _, _ -> """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""" },
            apiKey = "K"
        )
        val partials = mutableListOf<String>()
        val resp = provider.streamChat(userReq(), partials::add)
        assertEquals(listOf("ok"), partials)
        assertEquals("ok", resp.text)
    }

    @Test
    fun zenResponsesStreamsTextAndFunctionCall() = runTest {
        val body = StringBuilder()
        val stream = fakeStream(
            listOf(
                SseEvent("response.output_text.delta", """{"output_index":0,"delta":"Hi"}"""),
                SseEvent(
                    "response.output_item.added",
                    """{"output_index":0,"item":{"type":"function_call","call_id":"f1","name":"fetch_url"}}"""
                ),
                SseEvent("response.function_call_arguments.delta", """{"output_index":0,"delta":"{}"}""")
            ),
            body
        )
        val provider = ZenProvider(
            http = { _, _, _ -> "{}" }, apiKey = "K", model = "muse-spark-x", streamHttp = stream
        )
        val partials = mutableListOf<String>()
        val resp = provider.streamChat(userReq(), partials::add)
        assertEquals(listOf("Hi"), partials)
        assertEquals("Hi", resp.text)
        assertEquals(1, resp.toolCalls.size)
        assertEquals("fetch_url", resp.toolCalls[0].name)
        assertEquals("f1", resp.toolCalls[0].id)
        assertTrue(body.contains("\"stream\":true"))
    }

    @Test
    fun anthropicStreamsTextAndInputJson() = runTest {
        val body = StringBuilder()
        val stream = fakeStream(
            listOf(
                SseEvent("content_block_delta", """{"index":0,"delta":{"type":"text_delta","text":"Sun"}}"""),
                SseEvent("content_block_delta", """{"index":0,"delta":{"type":"text_delta","text":"ny"}}"""),
                SseEvent("content_block_start", """{"index":1,"content_block":{"type":"tool_use","id":"t1","name":"web_search"}}"""),
                SseEvent("content_block_delta", """{"index":1,"delta":{"type":"input_json_delta","partial_json":"{\"q\":"}}"""),
                SseEvent("content_block_delta", """{"index":1,"delta":{"type":"input_json_delta","partial_json":"\"x\"}"}}""")
            ),
            body
        )
        val provider = AnthropicProvider(http = { _, _, _ -> "{}" }, apiKey = "K", streamHttp = stream)
        val partials = mutableListOf<String>()
        val resp = provider.streamChat(userReq(), partials::add)
        assertEquals(listOf("Sun", "Sunny"), partials)
        assertEquals("Sunny", resp.text)
        assertEquals(1, resp.toolCalls.size)
        assertEquals("t1", resp.toolCalls[0].id)
        assertEquals("{\"q\":\"x\"}", resp.toolCalls[0].argumentsJson)
        assertTrue(body.contains("\"stream\":true"))
    }

    @Test
    fun geminiStreamsChunks() = runTest {
        var capturedUrl = ""
        val stream: HttpStream = { url, _, _, onEvent ->
            capturedUrl = url
            onEvent(SseEvent("", """{"candidates":[{"content":{"parts":[{"text":"Hel"}]}}]}"""))
            onEvent(SseEvent("", """{"candidates":[{"content":{"parts":[{"text":"lo"}]}}]}"""))
        }
        val provider = GeminiProvider(http = { _, _, _ -> "{}" }, apiKey = "K", streamHttp = stream)
        val partials = mutableListOf<String>()
        val resp = provider.streamChat(userReq(), partials::add)
        assertTrue(capturedUrl.contains(":streamGenerateContent?alt=sse"))
        assertEquals(listOf("Hel", "Hello"), partials)
        assertEquals("Hello", resp.text)
    }

    @Test
    fun goRoutesStreamsByModel() = runTest {
        val urls = mutableListOf<String>()
        val stream: HttpStream = { url, _, _, onEvent ->
            urls.add(url)
            if (url.endsWith("/messages")) {
                onEvent(SseEvent("content_block_delta", """{"index":0,"delta":{"type":"text_delta","text":"q"}}"""))
            } else {
                onEvent(SseEvent("", """{"choices":[{"delta":{"content":"z"}}]}"""))
                onEvent(SseEvent("", "[DONE]"))
            }
        }
        val qwen = GoProvider(http = { _, _, _ -> "{}" }, apiKey = "K", model = "qwen3.8-max", streamHttp = stream)
        val partials = mutableListOf<String>()
        assertEquals("q", qwen.streamChat(userReq(), partials::add).text)
        val kimi = GoProvider(http = { _, _, _ -> "{}" }, apiKey = "K", model = "kimi-k3", streamHttp = stream)
        assertEquals("z", kimi.streamChat(userReq(), {}).text)
        assertEquals(
            listOf(
                "https://opencode.ai/zen/go/v1/messages",
                "https://opencode.ai/zen/go/v1/chat/completions"
            ),
            urls
        )
        assertEquals(listOf("q"), partials)
    }
}
