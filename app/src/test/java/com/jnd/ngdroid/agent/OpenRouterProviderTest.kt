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

class OpenRouterProviderTest {

    @Test
    fun chatHitsOpenRouterCompletions() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val sample = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        val provider = OpenRouterProvider(
            http = { url, headers, _ ->
                capturedUrl = url
                capturedHeaders = headers
                sample
            },
            apiKey = "OR_KEY"
        )
        val resp = provider.chat(
            LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi")))
        )
        assertEquals("https://openrouter.ai/api/v1/chat/completions", capturedUrl)
        assertEquals("Bearer OR_KEY", capturedHeaders["Authorization"])
        assertEquals("NGDroid", capturedHeaders["X-Title"])
        assertEquals("ok", resp.text)
        assertEquals("openrouter", provider.id)
        assertEquals("openrouter/free", provider.defaultModel)
    }

    @Test
    fun visionAndToolsInherited() {
        val provider = OpenRouterProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val img = LlmImage("image/jpeg", "QUJD", "board.jpg")
        val body = provider.buildRequestJson(
            "meta-llama/llama-3.3-70b-instruct:free", "s",
            listOf(ChatMessage(ChatRole.USER, "see this", images = listOf(img))),
            listOf(LlmTool("web_search", "Search", """{"type":"object"}""")),
            0.7, 50
        )
        assertTrue(body.contains("data:image/jpeg;base64,QUJD"))
        assertTrue(body.contains("tools"))
    }

    @Test
    fun listModelsParsed() {
        val body = """{"data":[{"id":"meta-llama/llama-3.3-70b-instruct:free"},{"id":"openrouter/free"}]}"""
        val ids = OpenAiProvider.parseListModelsResponse(body)
        assertTrue(ids.contains("meta-llama/llama-3.3-70b-instruct:free"))
        assertTrue(ids.contains("openrouter/free"))
    }
}
