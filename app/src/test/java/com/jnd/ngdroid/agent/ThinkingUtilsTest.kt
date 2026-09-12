package com.jnd.ngdroid.agent

import com.jnd.ngdroid.ui.assistant.ChatMsg
import com.jnd.ngdroid.ui.assistant.ChatRoleUi
import com.jnd.ngdroid.ui.assistant.liveThinkingSteps
import com.jnd.ngdroid.ui.assistant.pastThinkingTitle
import com.jnd.ngdroid.ui.assistant.stepsByAssistant
import com.jnd.ngdroid.ui.assistant.thinkingTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingUtilsTest {

    @Test
    fun liveTitleMapsToolCallsToVerbs() {
        assertEquals("Validating…", thinkingTitle("Calling validate_netlist…", null))
        assertEquals("Applying…", thinkingTitle("Calling apply_netlist…", null))
        assertEquals("Running…", thinkingTitle("Calling run_simulation…", null))
        assertEquals("Generating…", thinkingTitle("Calling generate_netlist_template…", null))
    }

    @Test
    fun liveTitleKeepsProviderContactLine() {
        assertEquals("Contacting OpenCode Zen…", thinkingTitle("Contacting OpenCode Zen…", null))
    }

    @Test
    fun clearedStatusFallsBackToLastStepPastTense() {
        assertEquals("Validated", thinkingTitle(null, "validate_netlist: VALID"))
        assertEquals("Applied", thinkingTitle(null, "Calling apply_netlist…"))
        assertEquals("Thinking…", thinkingTitle(null, null))
        assertEquals("Thinking…", thinkingTitle("  ", null))
    }

    @Test
    fun pastTitleSummarizesSteps() {
        val steps = listOf(
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling validate_netlist…"),
            ChatMsg(id = "s2", role = ChatRoleUi.SYSTEM, text = "validate_netlist: VALID")
        )
        assertEquals("Validated • 2 steps", pastThinkingTitle(steps))
    }

    @Test
    fun liveStepsAreOnlyTheRunningTurn() {
        val msgs = listOf(
            ChatMsg(id = "u1", role = ChatRoleUi.USER, text = "first"),
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling validate_netlist…"),
            ChatMsg(id = "a1", role = ChatRoleUi.ASSISTANT, text = "done"),
            ChatMsg(id = "u2", role = ChatRoleUi.USER, text = "second"),
            ChatMsg(id = "s2", role = ChatRoleUi.SYSTEM, text = "Calling apply_netlist…")
        )
        val live = liveThinkingSteps(msgs)
        assertEquals(listOf("s2"), live.map { it.id })
    }

    @Test
    fun stepsGroupedPerAnswer() {
        val msgs = listOf(
            ChatMsg(id = "u1", role = ChatRoleUi.USER, text = "first"),
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling validate_netlist…"),
            ChatMsg(id = "a1", role = ChatRoleUi.ASSISTANT, text = "done"),
            ChatMsg(id = "u2", role = ChatRoleUi.USER, text = "second"),
            ChatMsg(id = "a2", role = ChatRoleUi.ASSISTANT, text = "no tools used")
        )
        val grouped = stepsByAssistant(msgs)
        assertEquals(listOf("s1"), grouped["a1"]!!.map { it.id })
        assertTrue(grouped["a2"]!!.isEmpty())
    }
}
