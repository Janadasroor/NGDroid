package com.jnd.ngdroid.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenProviderCopyTest {

    @Test
    fun urlIsCloud() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val sample = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        val provider = ZenProvider(
            http = { url, headers, _ ->
                capturedUrl = url
                capturedHeaders = headers
                sample
            },
            apiKey = "USER_KEY",
            model = "big-pickle"
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("https://opencode.ai/zen/v1/chat/completions", capturedUrl)
        assertEquals("Bearer USER_KEY", capturedHeaders["Authorization"])
    }

    @Test
    fun sessionHeaderPresentByDefault() = runTest {
        var capturedHeaders: Map<String, String> = emptyMap()
        val sample = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        val provider = ZenProvider(
            http = { _, headers, _ ->
                capturedHeaders = headers
                sample
            },
            apiKey = "KEY"
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("spiceagent-01", capturedHeaders["x-opencode-session"])
    }

    @Test
    fun blankSessionOmitsHeader() = runTest {
        var capturedHeaders: Map<String, String> = emptyMap()
        val sample = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        val provider = ZenProvider(
            http = { _, headers, _ ->
                capturedHeaders = headers
                sample
            },
            apiKey = "KEY",
            sessionId = ""
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertFalse(capturedHeaders.containsKey("x-opencode-session"))
    }

    @Test
    fun modelNormalize() {
        assertEquals("big-pickle", ZenProvider.normalizeModelId("zen/big-pickle"))
        assertEquals("big-pickle", ZenProvider.normalizeModelId("big-pickle"))
    }

    @Test
    fun providerConfigHasNoBaseUrl() {
        val fields = ProviderConfig::class.java.declaredFields.map { it.name }
        assertFalse(fields.contains("baseUrl"))
    }

    @Test
    fun listModelsIncludesMuseSpark() {
        val body = """{"data":[{"id":"big-pickle"},{"id":"muse-spark-1.3-contributor-free"}]}"""
        val ids = ZenProvider.parseListModelsResponse(body)
        assertTrue(ids.contains("big-pickle"))
        assertTrue(ids.contains("muse-spark-1.3-contributor-free"))
    }

    @Test
    fun freeIdsDetectedLiveFromSuffix() {
        val body = """{"data":[{"id":"big-pickle"},{"id":"mimo-v2.5-free"},{"id":"gpt-x"},{"id":"muse-spark-1.3-contributor-free"}]}"""
        val free = ZenProvider.parseFreeModelIds(body)
        assertTrue(free.contains("mimo-v2.5-free"))
        assertTrue(free.contains("muse-spark-1.3-contributor-free"))
        assertFalse(free.contains("big-pickle"))
        assertFalse(free.contains("gpt-x"))
    }
}
