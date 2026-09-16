package com.jnd.ngdroid.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProviderToolsTest {

    private val provider = GeminiProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
    private val tool = LlmTool(
        name = "run_simulation",
        description = "Run it",
        parametersJsonSchema = """{"type":"object","properties":{}}"""
    )

    @Test
    fun requestIncludesFunctionDeclarations() {
        val body = provider.buildRequestJson(
            system = "s",
            messages = listOf(ChatMessage(ChatRole.USER, "hi")),
            temp = 0.7,
            maxTokens = 512,
            tools = listOf(tool)
        )
        assertTrue("functionDeclarations" in body)
        assertTrue("run_simulation" in body)
    }

    @Test
    fun requestOmitsToolsWhenEmpty() {
        val body = provider.buildRequestJson(
            system = "s",
            messages = listOf(ChatMessage(ChatRole.USER, "hi")),
            temp = 0.7,
            maxTokens = 512
        )
        assertTrue("functionDeclarations" !in body)
    }

    @Test
    fun toolRoundTripRendersCallAndResponse() {
        val body = provider.buildRequestJson(
            system = "s",
            messages = listOf(
                ChatMessage(ChatRole.USER, "run it"),
                ChatMessage(
                    ChatRole.ASSISTANT, "",
                    toolCalls = listOf(ToolCall(id = "", name = "run_simulation", argumentsJson = "{}"))
                ),
                ChatMessage(ChatRole.TOOL, "status: ok", toolCallId = null)
            ),
            temp = 0.7,
            maxTokens = 512,
            tools = listOf(tool)
        )
        assertTrue("functionCall" in body)
        assertTrue("functionResponse" in body)
        assertTrue("status: ok" in body)
    }

    @Test
    fun parseExtractsFunctionCall() {
        val resp = provider.parseChatResponse(
            """{"candidates":[{"content":{"parts":[{"text":"on it"},{"functionCall":{"name":"run_simulation","args":{}}}]}}]}"""
        )
        assertEquals("on it", resp.text)
        assertEquals(1, resp.toolCalls.size)
        assertEquals("run_simulation", resp.toolCalls[0].name)
        assertEquals("{}", resp.toolCalls[0].argumentsJson)
    }
}
