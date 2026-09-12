package com.jnd.ngdroid.agent

import com.jnd.ngdroid.data.ChatSession
import com.jnd.ngdroid.data.StoredMsg
import com.jnd.ngdroid.data.StoredMsgRole
import com.jnd.ngdroid.ui.assistant.ChatMsg
import com.jnd.ngdroid.ui.assistant.ChatRoleUi
import com.jnd.ngdroid.ui.assistant.buildTitle
import com.jnd.ngdroid.ui.assistant.filterSessions
import com.jnd.ngdroid.ui.assistant.toStored
import com.jnd.ngdroid.ui.assistant.toUi
import com.jnd.ngdroid.ui.assistant.truncateAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryUtilsTest {

    @Test
    fun buildTitleTruncatesAndCleans() {
        assertEquals("Hello world", buildTitle("  Hello   world  "))
        assertEquals("New chat", buildTitle("   "))
        assertEquals("New chat", buildTitle(""))
        val long = "a".repeat(100)
        val title = buildTitle(long)
        assertTrue(title.length <= 43)
        assertTrue(title.endsWith("…"))
        assertEquals("Line one line two", buildTitle("Line one\nline two"))
    }

    @Test
    fun truncateAfterKeepsHeadInclusive() {
        val msgs = listOf(
            ChatMsg(id = "1", role = ChatRoleUi.USER, text = "a"),
            ChatMsg(id = "2", role = ChatRoleUi.ASSISTANT, text = "b"),
            ChatMsg(id = "3", role = ChatRoleUi.USER, text = "c")
        )
        val out = truncateAfter(msgs, "2")
        assertEquals(listOf("1", "2"), out.map { it.id })
    }

    @Test
    fun truncateAfterUnknownIdUnchanged() {
        val msgs = listOf(ChatMsg(id = "1", role = ChatRoleUi.USER, text = "a"))
        assertSame(msgs, truncateAfter(msgs, "nope"))
    }

    @Test
    fun uiStoredRoundTripPreservesRoles() {
        val ui = listOf(
            ChatMsg(id = "u1", role = ChatRoleUi.USER, text = "hi"),
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling x…"),
            ChatMsg(id = "a1", role = ChatRoleUi.ASSISTANT, text = "resp")
        )
        val stored = ui.map { it.toStored() }
        assertEquals(
            listOf(StoredMsgRole.USER, StoredMsgRole.SYSTEM, StoredMsgRole.ASSISTANT),
            stored.map { it.role }
        )
        val back = stored.map { it.toUi() }
        assertEquals(ui.map { it.id }, back.map { it.id })
        assertEquals(ui.map { it.role }, back.map { it.role })
        assertEquals(ui.map { it.text }, back.map { it.text })
    }

    @Test
    fun storedToUiRoundTrip() {
        val stored = StoredMsg(id = "m", role = StoredMsgRole.ASSISTANT, text = "t")
        val ui = stored.toUi()
        assertEquals(ChatRoleUi.ASSISTANT, ui.role)
        assertEquals("m", ui.toStored().id)
    }

    @Test
    fun filterSessionsMatchesTitleAndBody() {
        val sessions = listOf(
            ChatSession(id = "1", title = "RC filter"),
            ChatSession(
                id = "2",
                title = "Other",
                messages = listOf(StoredMsg(text = "diode rectifier talk"))
            )
        )
        assertEquals(listOf("1"), filterSessions(sessions, "rc").map { it.id })
        assertEquals(listOf("2"), filterSessions(sessions, "DIODE").map { it.id })
        assertEquals(2, filterSessions(sessions, "").size)
        assertTrue(filterSessions(sessions, "zzz").isEmpty())
    }
}
