package com.jnd.ngdroid.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepairTest {

    @Test
    fun mergesConsecutiveSameRole() {
        val history = listOf(
            ChatMessage(ChatRole.USER, "Design an RC filter"),
            ChatMessage(ChatRole.USER, "1kHz cutoff"),
            ChatMessage(ChatRole.ASSISTANT, "Here it is")
        )
        val repaired = repairHistory(history)
        assertEquals(2, repaired.size)
        assertEquals("Design an RC filter\n\n1kHz cutoff", repaired[0].content)
        assertEquals("Here it is", repaired[1].content)
    }

    @Test
    fun toolTurnsNeverMerge() {
        val history = listOf(
            ChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("a", "web_search", "{}"))),
            ChatMessage(ChatRole.TOOL, "obs one", toolCallId = "a"),
            ChatMessage(ChatRole.TOOL, "obs two", toolCallId = "b")
        )
        val repaired = repairHistory(history)
        assertEquals(3, repaired.size)
        assertEquals("a", repaired[1].toolCallId)
        assertEquals("b", repaired[2].toolCallId)
    }

    @Test
    fun dropsLeadingOrphanTool() {
        val history = listOf(
            ChatMessage(ChatRole.TOOL, "stale observation", toolCallId = "x"),
            ChatMessage(ChatRole.USER, "hi")
        )
        val repaired = repairHistory(history)
        assertEquals(1, repaired.size)
        assertTrue(repaired[0].role == ChatRole.USER)
    }

    @Test
    fun assistantToolCallsUnioned() {
        val history = listOf(
            ChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("a", "web_search", "{}"))),
            ChatMessage(ChatRole.ASSISTANT, "done", toolCalls = listOf(ToolCall("b", "read_file", "{}")))
        )
        val repaired = repairHistory(history)
        assertEquals(1, repaired.size)
        assertEquals(2, repaired[0].toolCalls.size)
        assertEquals("done", repaired[0].content)
    }
}
