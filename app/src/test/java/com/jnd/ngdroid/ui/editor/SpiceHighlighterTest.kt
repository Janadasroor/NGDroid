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

package com.jnd.ngdroid.ui.editor

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiceHighlighterTest {

    private val colors = SpiceColors(
        base = Color.Black,
        comment = Color.Gray,
        directive = Color.Blue,
        component = Color.Red,
        number = Color.Green
    )

    @Test
    fun emptyInput_returnsEmpty() {
        val result = highlightNetlist("", colors)
        assertEquals(0, result.length)
    }

    @Test
    fun commentLine_getsCommentSpan() {
        val text = "* RC filter comment"
        val result = highlightNetlist(text, colors)
        assertEquals(text.length, result.length)
        val spans = result.spanStyles
        assertTrue(spans.any { it.item.color == colors.comment })
    }

    @Test
    fun directive_getsDirectiveSpan() {
        val text = ".tran 10u 10m"
        val result = highlightNetlist(text, colors)
        val spans = result.spanStyles
        assertTrue(spans.any { it.item.color == colors.directive })
    }

    @Test
    fun componentToken_getsComponentSpan() {
        val text = "R1 in out 1k"
        val result = highlightNetlist(text, colors)
        val spans = result.spanStyles
        assertTrue(spans.any { it.item.color == colors.component })
    }

    @Test
    fun number_getsNumberSpan() {
        val text = "R1 in out 1k"
        val result = highlightNetlist(text, colors)
        val spans = result.spanStyles
        assertTrue(spans.any { it.item.color == colors.number })
    }

    @Test
    fun multiline_offsetsAreCorrect() {
        val text = "* comment\nR1 in out 1k\n.tran 1u 1m"
        val result = highlightNetlist(text, colors)
        assertEquals(text.length, result.length)
        val colorsUsed = result.spanStyles.map { it.item.color }.toSet()
        assertTrue(colorsUsed.contains(colors.comment))
        assertTrue(colorsUsed.contains(colors.component))
        assertTrue(colorsUsed.contains(colors.directive))
    }
}
