package com.jnd.ngdroid.agent

import com.jnd.ngdroid.data.ChatJsonCodec
import com.jnd.ngdroid.data.ChatSession
import com.jnd.ngdroid.data.StoredMsg
import com.jnd.ngdroid.data.StoredMsgRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCodecTest {

    private fun sample(): ChatSession = ChatSession(
        id = "chat|1",
        title = "Title with | pipe\nand newline",
        providerName = "OpenCode Zen",
        model = "some-model",
        createdAtMillis = 1000L,
        updatedAtMillis = 2000L,
        messages = listOf(
            StoredMsg(id = "m1", role = StoredMsgRole.USER, text = "Hello | world\nline2 \\ back", timestampMillis = 1001L),
            StoredMsg(id = "m2", role = StoredMsgRole.ASSISTANT, text = "```spice\n* t\n.end\n```", timestampMillis = 1002L),
            StoredMsg(id = "m3", role = StoredMsgRole.SYSTEM, text = "Calling validate_netlist…", timestampMillis = 1003L)
        )
    )

    @Test
    fun roundTripPreservesEverything() {
        val original = listOf(sample())
        val decoded = ChatJsonCodec.decode(ChatJsonCodec.encode(original))
        assertEquals(1, decoded.size)
        val s = decoded[0]
        assertEquals("chat|1", s.id)
        assertEquals("Title with | pipe\nand newline", s.title)
        assertEquals("OpenCode Zen", s.providerName)
        assertEquals("some-model", s.model)
        assertEquals(1000L, s.createdAtMillis)
        assertEquals(2000L, s.updatedAtMillis)
        assertEquals(3, s.messages.size)
        assertEquals("Hello | world\nline2 \\ back", s.messages[0].text)
        assertEquals(StoredMsgRole.USER, s.messages[0].role)
        assertEquals("m1", s.messages[0].id)
        assertEquals(StoredMsgRole.ASSISTANT, s.messages[1].role)
        assertEquals(StoredMsgRole.SYSTEM, s.messages[2].role)
    }

    @Test
    fun blankDecodesToEmpty() {
        assertTrue(ChatJsonCodec.decode("").isEmpty())
        assertTrue(ChatJsonCodec.decode("   ").isEmpty())
    }

    @Test
    fun emptyMessagesRoundTrip() {
        val decoded = ChatJsonCodec.decode(ChatJsonCodec.encode(listOf(ChatSession(id = "x"))))
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].messages.isEmpty())
    }

    @Test
    fun unicodeRoundTrip() {
        val s = ChatSession(title = "díodo ⚡ مرحبا", messages = listOf(StoredMsg(text = "✓ Δ ±")))
        val decoded = ChatJsonCodec.decode(ChatJsonCodec.encode(listOf(s)))
        assertEquals("díodo ⚡ مرحبا", decoded[0].title)
        assertEquals("✓ Δ ±", decoded[0].messages[0].text)
    }

    @Test
    fun multipleSessionsRoundTrip() {
        val list = listOf(
            ChatSession(id = "a", title = "First"),
            ChatSession(id = "b", title = "Second", messages = listOf(StoredMsg(text = "hi")))
        )
        val decoded = ChatJsonCodec.decode(ChatJsonCodec.encode(list))
        assertEquals(2, decoded.size)
        assertEquals("a", decoded[0].id)
        assertEquals("b", decoded[1].id)
    }
}
