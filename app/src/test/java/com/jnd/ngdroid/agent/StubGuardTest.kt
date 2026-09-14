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
        assertEquals("Still working on it, one moment please.", out)
        assertEquals(3, provider.calls)
    }
}
