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

import com.jnd.ngdroid.ui.assistant.extractCodeBlocks
import com.jnd.ngdroid.ui.assistant.extractFirstNetlist
import com.jnd.ngdroid.ui.assistant.isNetlistBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun isNetlistBlockGatesApplyActions() {
        assertTrue(isNetlistBlock("* RC\nR1 in out 1k\nC1 out 0 100n\n.end"))
        assertFalse(isNetlistBlock("print('hello')"))
        assertFalse(isNetlistBlock("not a netlist at all"))
        assertFalse(isNetlistBlock(""))
        assertFalse(isNetlistBlock("   "))
    }
}
