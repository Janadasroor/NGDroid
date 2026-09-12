package com.jnd.ngdroid.agent

import com.jnd.ngdroid.ui.assistant.extractCodeBlocks
import com.jnd.ngdroid.ui.assistant.extractFirstNetlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlockExtractorTest {

    @Test
    fun extractsFencedBlocksWithLanguageTags() {
        val md = "Here:\n```spice\n* RC\nR1 in out 1k\n.end\n```\nMore:\n```text\nhello\n```"
        val blocks = extractCodeBlocks(md)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].contains("R1 in out 1k"))
        assertTrue(blocks[1].contains("hello"))
    }

    @Test
    fun extractsUntaggedFence() {
        val md = "```\n* t\n.end\n```"
        assertEquals(listOf("* t\n.end"), extractCodeBlocks(md))
    }

    @Test
    fun emptyWhenNoFences() {
        assertTrue(extractCodeBlocks("plain text").isEmpty())
        assertNull(extractFirstNetlist("plain text"))
    }

    @Test
    fun prefersFirstValidNetlist() {
        val bad = "not a netlist at all"
        val good = "* RC low-pass\nR1 in out 1k\nC1 out 0 100n\n.tran 0.1m 10m\n.end"
        val md = "```text\n$bad\n```\n```spice\n$good\n```"
        assertEquals(good, extractFirstNetlist(md))
    }

    @Test
    fun fallsBackToFirstBlockWhenNoneValid() {
        val md = "```\nhello\n```\n```\nworld\n```"
        assertEquals("hello", extractFirstNetlist(md))
    }

    @Test
    fun validRcNetlistDetected() {
        val md = "```spice\n* RC\nV1 in 0 AC 1\nR1 in out 1k\nC1 out 0 100n\n.end\n```"
        assertNotNull(extractFirstNetlist(md))
    }
}
