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
