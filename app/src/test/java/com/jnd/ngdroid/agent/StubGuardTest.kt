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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StubGuardTest {

    @Test
    fun detectorAcceptsPayload() {
        assertTrue(finalHasPayload("```spice\n* x\n.end\n```"))
        assertTrue(finalHasPayload("see ![pinout](https://example.com/p.png)"))
        assertTrue(finalHasPayload("datasheet: https://www.ti.com/lit/ds/ne555.pdf"))
        assertFalse(finalHasPayload("I'll pull the datasheet and build a demo."))
    }

    @Test
    fun detectorFlagsPromises() {
        assertTrue(
            isPromiseWithoutPayload(
                "I'll pull the official 555 datasheet and put together a demo circuit."
            )
        )
        assertTrue(
            isPromiseWithoutPayload(
                "Building your 555 timer — classic astable circuit coming up."
            )
        )
        // Exact stub from the TL494 chat turn (guard missed "pulling … now").
        assertTrue(
            isPromiseWithoutPayload(
                "Official TL494 PDFs + a quick SPICE demo of its timing/PWM — pulling them now."
            )
        )
        assertTrue(isPromiseWithoutPayload("Here you go:"))
        // Complete answers pass through even without links.
        assertFalse(isPromiseWithoutPayload("The NE555 runs on 4.5V to 16V."))
        assertFalse(isPromiseWithoutPayload(""))
        assertFalse(
            isPromiseWithoutPayload("Done. Netlist:\n```spice\n* x\n.end\n``` coming up.")
        )
    }

    private class ScriptedProvider(val script: List<LlmResponse>) : LlmProvider {
        override val id = "test"
        override val displayName = "Test"
        override val defaultModel = "test"
        var calls = 0
        val seenSystems = mutableListOf<String>()
        override suspend fun chat(req: LlmRequest): LlmResponse {
            seenSystems.add(req.messages.lastOrNull()?.content.orEmpty())
            return script[minOf(calls++, script.size - 1)]
        }
        override suspend fun listModels(apiKey: String) = emptyList<String>()
    }

    private class EchoTool : AgentTool {
        override val name = "web_search"
        override val description = "fake search"
        override val parametersJsonSchema = "{}"
        override suspend fun execute(argsJson: String) = "1. Thing\n   https://example.com/thing"
    }

    private fun registry() = MapToolRegistry().apply { register(EchoTool()) }

    @Test
    fun stubAfterToolsTriggersOneRetry() = runTest {
        val provider = ScriptedProvider(
            listOf(
                LlmResponse(
                    "Searching now.",
                    listOf(ToolCall("1", "web_search", """{"query":"555"}"""))
                ),
                LlmResponse("I'll pull the official 555 datasheet and put together a demo circuit."),
                LlmResponse("Here it is: https://example.com/thing")
            )
        )
        val agent = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
        val events = mutableListOf<AgentEvent>()
        val out = agent.run("download timer 555 datasheet", onEvent = { events.add(it) })
        assertEquals("Here it is: https://example.com/thing", out)
        assertEquals(3, provider.calls)
        // The nudge went back in as a user turn, not shown as chat output.
        assertTrue(provider.seenSystems.any { STUB_RETRY_NUDGE in it })
        assertFalse(events.any { it is AgentEvent.Message && STUB_RETRY_NUDGE in it.text })
    }

    @Test
    fun completeAnswerAfterToolsHasNoRetry() = runTest {
        val provider = ScriptedProvider(
            listOf(
                LlmResponse(
                    "Searching now.",
                    listOf(ToolCall("1", "web_search", """{"query":"555"}"""))
                ),
                LlmResponse("The NE555 runs on 4.5V to 16V.")
            )
        )
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
            .run("max vcc of 555?")
        assertEquals("The NE555 runs on 4.5V to 16V.", out)
        assertEquals(2, provider.calls)
    }

    @Test
    fun nonStreamingProviderStillEmitsPartial() = runTest {
        val provider = ScriptedProvider(listOf(LlmResponse("Hello there")))
        val agent = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
        val partials = mutableListOf<String>()
        val out = agent.run("hi?", onEvent = { if (it is AgentEvent.Partial) partials.add(it.text) })
        assertEquals("Hello there", out)
        assertEquals(listOf("Hello there"), partials)
    }

    @Test
    fun stubWithNoToolsHasNoRetry() = runTest {
        val provider = ScriptedProvider(
            listOf(LlmResponse("Something is coming up, I promise."))
        )
        val out = AgentOrchestrator(AgentConfig(), provider, MapToolRegistry())
            .run("hi")
        assertEquals("Something is coming up, I promise.", out)
        assertEquals(1, provider.calls)
    }

    @Test
    fun retryFiresAtMostOnce() = runTest {
        val provider = ScriptedProvider(
            listOf(
                LlmResponse(
                    "Searching now.",
                    listOf(ToolCall("1", "web_search", """{"query":"555"}"""))
                ),
                LlmResponse("I'll pull that for you right away."),
                LlmResponse("Still working on it, one moment please.")
            )
        )
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
            .run("download timer 555 datasheet")
        assertEquals("Still working on it, one moment please.\n\n_${STALLED_TRAILER}_", out)
        assertEquals(3, provider.calls)
    }

    @Test
    fun stalledAfterUnknownToolGetsTrailerNotNudge() = runTest {
        val provider = ScriptedProvider(
            listOf(
                LlmResponse(
                    "Building your Colpitts oscillator.",
                    listOf(ToolCall("1", "validate", """{"netlist":"* x\n.end"}"""))
                ),
                LlmResponse("Checking for a clean sinewave, one moment please.")
            )
        )
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
            .run("build colpitts oscillator")
        // Unknown tool: no stub nudge (guard needs a content tool), but the
        // attempt marks a stall, so the trailer still applies.
        assertEquals(
            "Checking for a clean sinewave, one moment please.\n\n_${STALLED_TRAILER}_",
            out
        )
        assertEquals(2, provider.calls)
        assertFalse(provider.seenSystems.any { STUB_RETRY_NUDGE in it })
    }

    @Test
    fun finalAfterErrorPrefersFriendlyOverStalePromise() {
        assertEquals("FRIENDLY", finalAfterError("", "FRIENDLY", true))
        assertEquals(
            "FRIENDLY",
            finalAfterError("Building your Colpitts oscillator.", "FRIENDLY", true)
        )
        // A partial that already carries a payload is still useful: keep it.
        val withNetlist = "Here is a start:\n```spice\n* x\n.end\n```"
        assertEquals(withNetlist, finalAfterError(withNetlist, "FRIENDLY", true))
        // No tools ran: nothing promised, keep the model's own words.
        assertEquals("Working on it.", finalAfterError("Working on it.", "FRIENDLY", false))
    }

    private class FailSecondProvider(val first: LlmResponse) : LlmProvider {
        override val id = "test"
        override val displayName = "Test"
        override val defaultModel = "test"
        var calls = 0
        override suspend fun chat(req: LlmRequest): LlmResponse = throw AssertionError("no chat")
        override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse {
            calls++
            if (calls == 1) {
                if (first.text.isNotEmpty()) onPartial(first.text)
                return first
            }
            throw IllegalStateException("429 FreeUsageLimitError")
        }
        override suspend fun listModels(apiKey: String) = emptyList<String>()
    }

    @Test
    fun providerErrorAfterToolsYieldsFriendlyNotPromise() = runTest {
        val provider = FailSecondProvider(
            LlmResponse(
                "Building your Colpitts oscillator — checking for a clean sinewave.",
                listOf(ToolCall("1", "web_search", """{"query":"colpitts"}"""))
            )
        )
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
            .run("build colpitts oscillator", errorFormatter = { "FRIENDLY($it)" })
        assertEquals("FRIENDLY(429 FreeUsageLimitError)", out)
        assertEquals(2, provider.calls)
    }

    /**
     * Colpitts regression: after render_plot attaches a JPEG, the Zen gateway
     * can 400 with a bare `Upstream request failed: [400]` (no image/vision
     * wording). The run must strip images and retry text-only, not surface
     * the raw error.
     */
    private class ImageTool : AgentTool {
        override val name = "render_plot"
        override val description = "fake plot"
        override val parametersJsonSchema = "{}"
        override suspend fun execute(argsJson: String) = "plot: out (64KB jpeg)"
        override suspend fun executeEx(argsJson: String) = ToolResult(
            execute(argsJson),
            listOf(LlmImage("image/jpeg", "AAAA"))
        )
    }

    private class FailOnImagesProvider : LlmProvider {
        override val id = "test"
        override val displayName = "Test"
        override val defaultModel = "test"
        var calls = 0
        var retriedWithoutImages = false
        override suspend fun chat(req: LlmRequest): LlmResponse = throw AssertionError("no chat")
        override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse {
            calls++
            if (calls == 1) {
                return LlmResponse(
                    "",
                    listOf(ToolCall("1", "render_plot", """{}"""))
                )
            }
            val hasImages = req.messages.any { it.images.isNotEmpty() }
            if (hasImages) {
                throw IllegalStateException(
                    "HTTP 400 for https://opencode.ai/zen/v1/chat/completions: " +
                        "Upstream request failed: [400] Provider returned error"
                )
            }
            retriedWithoutImages = true
            return LlmResponse("Clean sinewave confirmed: https://example.com/plot.png")
        }
        override suspend fun listModels(apiKey: String) = emptyList<String>()
    }

    @Test
    fun generic400WithImagesRetriesTextOnly() = runTest {
        val provider = FailOnImagesProvider()
        val reg = MapToolRegistry().apply { register(ImageTool()) }
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, reg)
            .run("build colpitts oscillator")
        assertEquals("Clean sinewave confirmed: https://example.com/plot.png", out)
        assertTrue(provider.retriedWithoutImages)
    }

    /**
     * Colpitts regression: model goes quiet (blank text, no calls) after the
     * vision turn. The run must nudge once for a text-only answer instead of
     * storing "(empty response)".
     */
    @Test
    fun emptyAfterToolsRetriesWithNudge() = runTest {
        val provider = ScriptedProvider(
            listOf(
                LlmResponse(
                    "",
                    listOf(ToolCall("1", "render_plot", """{}"""))
                ),
                LlmResponse(""),
                LlmResponse("Clean sinewave: https://example.com/plot.png")
            )
        )
        val reg = MapToolRegistry().apply { register(ImageTool()) }
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, reg)
            .run("build colpitts oscillator")
        assertEquals("Clean sinewave: https://example.com/plot.png", out)
        assertEquals(3, provider.calls)
        assertTrue(provider.seenSystems.any { EMPTY_RETRY_NUDGE in it })
    }

    @Test
    fun emptyAfterToolsBudgetSpentFallsBack() = runTest {
        val provider = ScriptedProvider(
            listOf(
                LlmResponse(
                    "",
                    listOf(ToolCall("1", "render_plot", """{}"""))
                ),
                LlmResponse("")
            )
        )
        val reg = MapToolRegistry().apply { register(ImageTool()) }
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5, maxStubRetries = 0), provider, reg)
            .run("build colpitts oscillator")
        assertEquals(EMPTY_FINAL_FALLBACK, out)
        assertEquals(2, provider.calls)
    }

    // ---- error-retry policy ----

    /** Fails [failures] times with [error], then answers. */
    private class FlakyProvider(
        val failures: Int,
        val error: Throwable,
        val answer: String = "Recovered answer"
    ) : LlmProvider {
        override val id = "test"
        override val displayName = "Test"
        override val defaultModel = "test"
        var calls = 0
        override suspend fun chat(req: LlmRequest): LlmResponse = throw AssertionError("no chat")
        override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse {
            calls++
            if (calls <= failures) throw error
            return LlmResponse(answer)
        }
        override suspend fun listModels(apiKey: String) = emptyList<String>()
    }

    @Test
    fun transientErrorRetriesThenSucceeds() = runTest {
        val provider = FlakyProvider(
            1, IllegalStateException("timeout waiting for response")
        )
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
            .run("hi?")
        assertEquals("Recovered answer", out)
        assertEquals(2, provider.calls)
    }

    @Test
    fun fatalAuthErrorDoesNotRetry() = runTest {
        val provider = FlakyProvider(
            99, IllegalStateException("HTTP 401: AuthError Missing API key")
        )
        val out = AgentOrchestrator(AgentConfig(maxIterations = 5), provider, registry())
            .run("hi?", errorFormatter = { "FRIENDLY" })
        assertEquals("FRIENDLY", out)
        assertEquals(1, provider.calls)
    }

    @Test
    fun persistentErrorExhaustsBudget() = runTest {
        val provider = FlakyProvider(
            99, IllegalStateException("HTTP 500: internal server error")
        )
        val out = AgentOrchestrator(
            AgentConfig(maxIterations = 5, maxErrorRetries = 2, errorRetryBaseDelayMs = 10),
            provider, registry()
        ).run("hi?", errorFormatter = { "FRIENDLY" })
        assertEquals("FRIENDLY", out)
        assertEquals(3, provider.calls)
    }

    @Test
    fun cancelPropagatesWithoutRetry() = runTest {
        val provider = FlakyProvider(
            99, kotlinx.coroutines.CancellationException("stopped")
        )
        var calls = 0
        val counting = object : LlmProvider by provider {
            override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse {
                calls++
                return provider.streamChat(req, onPartial)
            }
        }
        try {
            AgentOrchestrator(AgentConfig(maxIterations = 5), counting, registry()).run("hi?")
            assertTrue("expected CancellationException", false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // expected: Stop must never be swallowed or retried
        }
        assertEquals(1, calls)
    }

    @Test
    fun retryableClassifier() {
        assertTrue(isRetryableError("timeout waiting for response"))
        assertTrue(isRetryableError("HTTP 500: internal server error"))
        assertTrue(isRetryableError("Upstream request failed: [400] Provider returned error"))
        assertTrue(isRetryableError("AI stream stalled (no data for 120 s)"))
        assertTrue(isRetryableError(null))
        assertFalse(isRetryableError("HTTP 401: AuthError Missing API key"))
        assertFalse(isRetryableError("unauthorized"))
        assertFalse(isRetryableError("model is not supported on this route"))
        // Rate limits fail fast: retry-after is minutes, not seconds.
        assertFalse(isRetryableError("HTTP 429: FreeUsageLimitError"))
        assertFalse(isRetryableError("rate limit exceeded"))
    }
}
