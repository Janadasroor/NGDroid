package com.jnd.ngdroid.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentErrorsTest {

    private val deepseek400 =
        "Provider error: HTTP 400 for https://opencode.ai/zen/v1/chat/completions: " +
            "{\"error\":{\"type\":\"server_error\",\"message\":\"Error from provider " +
            "(Console): Upstream request failed: Model is unavailable.\"}}"

    @Test
    fun unavailableModelBecomesFriendlySentence() {
        val out = AgentErrors.format(deepseek400, "deepseek-v4-flash-free")
        assertTrue(out, out.contains("temporarily unavailable"))
        assertTrue(out, out.contains("deepseek-v4-flash-free"))
        assertFalse(out, out.contains("{"))
        assertFalse(out, out.contains("http"))
    }

    @Test
    fun rateLimitSuggestsWaiting() {
        val raw = "HTTP 429: {\"type\":\"error\",\"error\":{\"type\":\"FreeUsageLimitError\"," +
            "\"message\":\"Error from provider (Console): Rate limit exceeded.\"}}"
        val out = AgentErrors.format(raw, "mimo-v2.5-free")
        assertTrue(out, out.contains("exhausted"))
        assertFalse(out, out.contains("{"))
    }

    @Test
    fun freeModelLimitNamesLaneAndKeyOption() {
        val raw = "{\"type\":\"error\",\"error\":{\"type\":\"FreeUsageLimitError\"," +
            "\"message\":\"Error from provider (Console): Rate limit exceeded. Please try again later.\"}}"
        val out = AgentErrors.format(raw, "mimo-v2.5-free")
        assertTrue(out, out.contains("mimo-v2.5-free"))
        assertTrue(out, out.contains("anonymous free lane"))
        assertTrue(out, out.contains("API key"))
    }

    @Test
    fun keyedModelLimitHasNoLaneTalk() {
        val out = AgentErrors.format("HTTP 429 rate limit exceeded", "gpt-5")
        assertTrue(out, out.contains("limit"))
        assertFalse(out, out.contains("anonymous"))
    }

    @Test
    fun missingKeyPointsToSettings() {
        val out = AgentErrors.format("HTTP 401: AuthError Missing API key", "gpt-5")
        assertTrue(out, out.contains("API key"))
    }

    @Test
    fun networkFailureMentionsConnection() {
        val out = AgentErrors.format("Unable to resolve host \"opencode.ai\": No address", "big-pickle")
        assertTrue(out, out.contains("connection"))
    }

    @Test
    fun nullAndBlankNeverCrashOrLeak() {
        val a = AgentErrors.format(null, "m")
        val b = AgentErrors.format("   ", "m")
        assertTrue(a.isNotBlank())
        assertTrue(b.isNotBlank())
        assertFalse(a, a.contains("{"))
    }

    @Test
    fun unknownPlainTextPassesThroughTrimmed() {
        val out = AgentErrors.format("Provider error: something odd happened", "")
        assertTrue(out, out.contains("something odd happened"))
        assertFalse(out, out.contains("http"))
    }

    @Test
    fun upstream400SuggestsRetryOrOtherModel() {
        // Exact gateway shape from the Colpitts chat on mimo-v2.5-free.
        val raw = "Provider error: HTTP 400 for https://opencode.ai/zen/v1/chat/completions: " +
            "{\"error\":{\"type\":\"server_error\",\"message\":\"Error from provider " +
            "(Console): Upstream request failed: [400] Provider returned error\"}}"
        val out = AgentErrors.format(raw, "mimo-v2.5-free")
        assertTrue(out, out.contains("mimo-v2.5-free"))
        assertTrue(out, out.contains("Regenerate"))
        assertFalse(out, out.contains("{"))
        assertFalse(out, out.contains("http"))
    }
}
