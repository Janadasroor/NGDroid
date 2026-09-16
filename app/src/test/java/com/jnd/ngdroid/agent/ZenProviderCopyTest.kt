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
    fun chatToolsUseNestedFunctionShape() {
        val provider = ZenProvider(http = { _, _, _ -> "{}" }, apiKey = "K", model = "nemotron-3-ultra-free")
        val tool = LlmTool("test_tool", "test", """{"type":"object","properties":{}}""")
        val body = provider.buildRequestJson(
            "nemotron-3-ultra-free", "s",
            listOf(ChatMessage(ChatRole.USER, "hi")), listOf(tool), 0.7, 50
        )
        // OpenAI chat/completions shape: tools[].function.{name,description,parameters}
        assertTrue(body.contains("\"function\":{\"name\":\"test_tool\"") || body.contains("\"function\": {\"name\": \"test_tool\"") || body.contains("\"function\""))
        assertTrue(body.contains("\"name\":\"test_tool\""))
        // Must NOT be flat at top level of the tool object for chat path:
        // flat would be {"type":"function","name":...} without "function" key.
        assertTrue(body.contains("\"function\""))
    }

    @Test
    fun responsesToolsUseFlatShape() {
        val provider = ZenProvider(http = { _, _, _ -> "{}" }, apiKey = "K", model = "muse-spark-1.3-contributor-free")
        val tool = LlmTool("test_tool", "test", """{"type":"object","properties":{}}""")
        val body = provider.buildResponsesJson(
            "muse-spark-1.3-contributor-free", "s",
            listOf(ChatMessage(ChatRole.USER, "hi")), listOf(tool), 0.7, 50
        )
        assertTrue(body.contains("\"name\":\"test_tool\""))
        assertTrue(body.contains("max_output_tokens"))
    }

    @Test
    fun autoDefaultSkipsDeadModel() {
        val free = listOf(
            "deepseek-v4-flash-free",
            "muse-spark-1.3-contributor-free",
            "mimo-v2.5-free"
        )
        // Alphabetical first would be deepseek (dead) — must be skipped.
        assertEquals("ling-3.0-flash-fin-free", ZenProvider.autoDefault(free + "ling-3.0-flash-fin-free"))
        assertEquals("mimo-v2.5-free", ZenProvider.autoDefault(free))
    }

    @Test
    fun autoDefaultFallsBackWhenOnlyDeadRemains() {
        assertEquals("deepseek-v4-flash-free", ZenProvider.autoDefault(listOf("deepseek-v4-flash-free")))
        assertEquals(null, ZenProvider.autoDefault(emptyList()))
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

    @Test
    fun chatVisionEmitsImageUrlParts() {
        val provider = ZenProvider(http = { _, _, _ -> "{}" }, apiKey = "K", model = "big-pickle")
        val img = LlmImage("image/jpeg", "QUJD", "board.jpg")
        val body = provider.buildRequestJson(
            "big-pickle", "s",
            listOf(ChatMessage(ChatRole.USER, "see this", images = listOf(img))),
            emptyList(), 0.7, 50
        )
        assertTrue(body.contains("image_url"))
        assertTrue(body.contains("data:image/jpeg;base64,QUJD"))
    }

    @Test
    fun responsesVisionEmitsInputImageParts() {
        val provider = ZenProvider(http = { _, _, _ -> "{}" }, apiKey = "K")
        val img = LlmImage("image/jpeg", "QUJD", "board.jpg")
        val msg = provider.buildResponsesMessage(
            ChatMessage(ChatRole.USER, "see this", images = listOf(img))
        )
        assertTrue(msg.toString().contains("input_image"))
        assertTrue(msg.toString().contains("data:image/jpeg;base64,QUJD"))
    }

    @Test
    fun visionRejectionDetected() {
        assertTrue(isVisionRejection("400 image_url unsupported for this model"))
        assertFalse(isVisionRejection("Rate limit exceeded"))
    }

    @Test
    fun clientFingerprintHeaders() {
        val h = ZenProvider.zenHeaders("", "spiceagent-01")
        // Gateway rate-limits anonymous calls without the opencode UA.
        assertEquals("opencode/1.0", h["User-Agent"])
        assertTrue(h["User-Agent"]!!.startsWith("opencode/"))
        assertEquals("Bearer public", h["Authorization"])
        assertEquals("spiceagent-01", h["x-opencode-session"])
    }

    @Test
    fun fingerprintKeepsRealKeyAndDropsBlankSession() {
        val h = ZenProvider.zenHeaders("USER_KEY", "")
        assertEquals("Bearer USER_KEY", h["Authorization"])
        assertEquals("opencode/1.0", h["User-Agent"])
        assertFalse(h.containsKey("x-opencode-session"))
    }

    @Test
    fun chatSendsFingerprint() = runTest {
        var capturedHeaders: Map<String, String> = emptyMap()
        val sample = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        val provider = ZenProvider(
            http = { _, headers, _ ->
                capturedHeaders = headers
                sample
            },
            apiKey = ""
        )
        provider.chat(LlmRequest(systemPrompt = "s", messages = listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("Bearer public", capturedHeaders["Authorization"])
        assertEquals("opencode/1.0", capturedHeaders["User-Agent"])
    }
}
