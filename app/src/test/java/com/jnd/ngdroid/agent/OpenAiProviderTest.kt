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

class OpenAiProviderTest {

    @Test
    fun chatHitsCloudCompletions() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val sample = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        val provider = OpenAiProvider(
            http = { url, headers, _ ->
                capturedUrl = url
                capturedHeaders = headers
                sample
            },
            apiKey = "USER_KEY",
            model = "gpt-4.1-mini"
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("https://api.openai.com/v1/chat/completions", capturedUrl)
        assertEquals("Bearer USER_KEY", capturedHeaders["Authorization"])
    }

    @Test
    fun visionEmitsImageUrlParts() {
        val provider = OpenAiProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val img = LlmImage("image/jpeg", "QUJD", "board.jpg")
        val body = provider.buildRequestJson(
            "gpt-4.1-mini", "s",
            listOf(ChatMessage(ChatRole.USER, "see this", images = listOf(img))),
            emptyList(), 0.7, 50
        )
        assertTrue(body.contains("image_url"))
        assertTrue(body.contains("data:image/jpeg;base64,QUJD"))
    }

    @Test
    fun quotaErrorSurfacedAsText() {
        val provider = OpenAiProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val body = """{"error":{"message":"You have no credits remaining.","type":"insufficient_quota","code":"credit_balance_exhausted"}}"""
        val resp = provider.parseChatResponse(body)
        assertTrue(resp.text.contains("no credits"))
    }

    @Test
    fun listModelsParsed() {
        val body = """{"data":[{"id":"gpt-4o-mini"},{"id":"gpt-4.1-nano"}]}"""
        val ids = OpenAiProvider.parseListModelsResponse(body)
        assertTrue(ids.contains("gpt-4o-mini"))
        assertTrue(ids.contains("gpt-4.1-nano"))
    }
}
