package com.jnd.ngdroid.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicProviderTest {

    @Test
    fun chatHitsCloudMessages() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val sample = """{"content":[{"type":"text","text":"ok"}]}"""
        val provider = AnthropicProvider(
            http = { url, headers, _ ->
                capturedUrl = url
                capturedHeaders = headers
                sample
            },
            apiKey = "USER_KEY",
            model = "claude-sonnet-4-5"
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("https://api.anthropic.com/v1/messages", capturedUrl)
        assertEquals("USER_KEY", capturedHeaders["x-api-key"])
        assertEquals("2023-06-01", capturedHeaders["anthropic-version"])
    }

    @Test
    fun visionEmitsImageBlocks() {
        val provider = AnthropicProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val img = LlmImage("image/jpeg", "QUJD", "board.jpg")
        val body = provider.buildRequestJson(
            "claude-sonnet-4-5", "s",
            listOf(ChatMessage(ChatRole.USER, "see this", images = listOf(img))),
            emptyList(), 0.7, 50
        )
        assertTrue(body.contains("\"type\":\"image\""))
        assertTrue(body.contains("\"media_type\":\"image/jpeg\""))
        assertTrue(body.contains("\"data\":\"QUJD\""))
    }

    @Test
    fun toolsUseInputSchema() {
        val provider = AnthropicProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val tool = LlmTool("web_search", "Search", """{"type":"object"}""")
        val body = provider.buildRequestJson(
            "claude-sonnet-4-5", "s",
            listOf(ChatMessage(ChatRole.USER, "hi")),
            listOf(tool), 0.7, 50
        )
        assertTrue(body.contains("input_schema"))
        assertTrue(body.contains("\"tool_choice\":{\"type\":\"auto\"}"))
    }

    @Test
    fun toolResultsRoundTrip() {
        val provider = AnthropicProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val body = provider.buildRequestJson(
            "claude-sonnet-4-5", "s",
            listOf(ChatMessage(ChatRole.TOOL, "72F, sunny", toolCallId = "toolu_1")),
            emptyList(), 0.7, 50
        )
        assertTrue(body.contains("tool_result"))
        assertTrue(body.contains("toolu_1"))
        val resp = provider.parseChatResponse(
            """{"content":[{"type":"text","text":"Sunny."},{"type":"tool_use","id":"toolu_2","name":"web_search","input":{"q":"x"}}]}"""
        )
        assertEquals("Sunny.", resp.text)
        assertEquals(1, resp.toolCalls.size)
        assertEquals("web_search", resp.toolCalls[0].name)
    }

    @Test
    fun authErrorSurfacedAsText() {
        val provider = AnthropicProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val body = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        val resp = provider.parseChatResponse(body)
        assertTrue(resp.text.contains("invalid x-api-key"))
    }

    @Test
    fun listModelsParsed() {
        val body = """{"data":[{"type":"model","id":"claude-sonnet-4-5"},{"type":"model","id":"claude-opus-5"}],"has_more":false}"""
        val ids = AnthropicProvider.parseListModelsResponse(body)
        assertTrue(ids.contains("claude-sonnet-4-5"))
        assertTrue(ids.contains("claude-opus-5"))
    }
}
