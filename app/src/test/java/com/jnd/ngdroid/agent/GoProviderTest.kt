package com.jnd.ngdroid.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoProviderTest {

    @Test
    fun chatCompletionsRouteForOpenModels() = runTest {
        var capturedUrl = ""
        val provider = GoProvider(
            http = { url, _, _ ->
                capturedUrl = url
                """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
            },
            apiKey = "GO_KEY",
            model = "kimi-k3"
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("https://opencode.ai/zen/go/v1/chat/completions", capturedUrl)
    }

    @Test
    fun responsesRouteForGrok() = runTest {
        var capturedUrl = ""
        val provider = GoProvider(
            http = { url, _, _ ->
                capturedUrl = url
                """{"output":[]}"""
            },
            apiKey = "GO_KEY",
            model = "grok-4.6"
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("https://opencode.ai/zen/go/v1/responses", capturedUrl)
    }

    @Test
    fun messagesRouteForQwen() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val provider = GoProvider(
            http = { url, headers, _ ->
                capturedUrl = url
                capturedHeaders = headers
                """{"content":[{"type":"text","text":"ok"}]}"""
            },
            apiKey = "GO_KEY",
            model = "qwen3.8-max"
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("https://opencode.ai/zen/go/v1/messages", capturedUrl)
        assertEquals("GO_KEY", capturedHeaders["x-api-key"])
    }

    @Test
    fun messagesRouteDetection() {
        assertTrue(GoProvider.isMessagesModel("qwen3.8-max"))
        assertTrue(GoProvider.isMessagesModel("minimax-m2.5"))
        assertTrue(!GoProvider.isMessagesModel("kimi-k3"))
        assertTrue(!GoProvider.isMessagesModel("grok-4.6"))
        assertTrue(!GoProvider.isMessagesModel("deepseek-v4-flash"))
    }
}
