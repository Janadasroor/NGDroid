package com.jnd.ngdroid.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseClientTest {

    @Test
    fun blockParsesEventAndData() {
        val ev = parseSseBlock("event: message\ndata: {\"a\":1}")
        assertEquals("message", ev?.event)
        assertEquals("{\"a\":1}", ev?.data)
    }

    @Test
    fun dataOnlyBlockHasEmptyEvent() {
        val ev = parseSseBlock("data: hello")
        assertEquals("", ev?.event)
        assertEquals("hello", ev?.data)
    }

    @Test
    fun commentsAndBlanksIgnored() {
        assertNull(parseSseBlock(": ping\n\n"))
        assertNull(parseSseBlock(""))
        val ev = parseSseBlock(": ping\ndata: x")
        assertEquals("x", ev?.data)
    }

    @Test
    fun multiLineDataJoined() {
        val ev = parseSseBlock("data: one\ndata: two")
        assertEquals("one\ntwo", ev?.data)
    }

    @Test
    fun payloadSplitsOnBlankLines() {
        val raw = "data: one\n\ndata: two\n\ndata: [DONE]\n\n"
        val events = parseSseEvents(raw)
        assertEquals(3, events.size)
        assertEquals("[DONE]", events[2].data)
    }

    @Test
    fun crlfNormalized() {
        val events = parseSseEvents("event: delta\r\ndata: hi\r\n\r\n")
        assertEquals(1, events.size)
        assertEquals("delta", events[0].event)
        assertEquals("hi", events[0].data)
    }

    @Test
    fun accumulatorJoinsTextAndTools() {
        val partials = mutableListOf<String>()
        val acc = ChatStreamAccumulator(partials::add)
        val d1 = """{"choices":[{"delta":{"content":"Hel"}}]}"""
        val d2 = """{"choices":[{"delta":{"content":"lo","tool_calls":[{"index":0,"id":"c1","function":{"name":"web_search","arguments":"{\"q\":"}}]}}]}"""
        val d3 = """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"x\"}"}}]}}]}"""
        assertTrue(acc.accept(SseEvent("", d1)))
        assertTrue(acc.accept(SseEvent("", d2)))
        assertTrue(acc.accept(SseEvent("", d3)))
        assertEquals(listOf("Hel", "Hello"), partials)
        val resp = acc.response()
        assertEquals("Hello", resp.text)
        assertEquals(1, resp.toolCalls.size)
        assertEquals("c1", resp.toolCalls[0].id)
        assertEquals("web_search", resp.toolCalls[0].name)
        assertEquals("{\"q\":\"x\"}", resp.toolCalls[0].argumentsJson)
    }

    @Test
    fun accumulatorStopsOnDone() {
        val acc = ChatStreamAccumulator()
        assertEquals(false, acc.accept(SseEvent("", "[DONE]")))
    }
}
