package com.jnd.ngdroid.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownStyleTest {

    @Test
    fun headingLadderIsCompactAndStrictlyDescending() {
        val sizes = CHAT_HEADING_SPECS.sortedBy { it.level }.map { it.fontSizeSp }
        assertEquals(listOf(22f, 20f, 18f, 16f, 14f, 12f), sizes)
        assertTrue("H1 must fit a chat bubble", sizes.first() <= 22f)
        for (i in 1 until sizes.size) {
            assertTrue("H${i + 1} must be smaller than H$i", sizes[i] < sizes[i - 1])
        }
    }

    @Test
    fun headingLevelDetectsAtxVariants() {
        assertEquals(1, headingLevelOf("# Title"))
        assertEquals(1, headingLevelOf("#Title"))
        assertEquals(2, headingLevelOf("##Title"))
        assertEquals(3, headingLevelOf("   ### Deep"))
        assertEquals(0, headingLevelOf("####### seven is not a heading"))
        assertEquals(0, headingLevelOf("    # indented code"))
        assertEquals(0, headingLevelOf("plain text"))
        assertEquals(0, headingLevelOf("- # not a heading"))
    }

    @Test
    fun normalizeFixesMissingSpaceButKeepsCodeIdentical() {
        val raw = "#Title\n\n```spice\n# not a heading\n```\n\n##Real"
        val out = normalizeMarkdownForChat(raw)
        assertTrue(out.contains("# Title"))
        assertTrue(out.contains("```spice\n# not a heading\n```"))
        assertTrue(out.contains("## Real"))
    }

    @Test
    fun normalizeCollapsesBlankRunsAndTrims() {
        val out = normalizeMarkdownForChat("a\n\n\n\n\nb   \n")
        assertEquals("a\n\n\nb", out)
    }

    @Test
    fun headingsIgnoredInsideFences() {
        val md = "# Real\n```\n# Fake\n```\n## Also real"
        val found = extractChatHeadings(md)
        assertEquals(listOf(1, 2), found.map { it.level })
        assertEquals(listOf("Real", "Also real"), found.map { it.title })
    }

    @Test
    fun e2eModelResponseKeepsTitlesMathTablesAndCode() {
        val d = "${'$'}"
        val md = """
            #Cutoff Frequency
            ##Details

            The cutoff is ${d}${d}f_c = \frac{1}{2\pi RC}${d}${d} and gain ${d}A_v = -R_c/R_E${d}.

            | Part | Value |
            | --- | --- |
            | R1 | 10k |

            ```spice
            R1 in out 10k
            ```

            Plain ${d}5 and ${d}10 stay literal.
        """.trimIndent()
        val normalized = normalizeMarkdownForChat(md)
        // Titles fixed and extractable.
        val headings = extractChatHeadings(normalized)
        assertEquals(listOf(1, 2), headings.map { it.level })
        assertEquals("Cutoff Frequency", headings[0].title)
        // Display math splits out; inline math prettifies; currency kept.
        assertTrue(hasDisplayMath(normalized))
        val pretty = prettifyInlineMath(normalized)
        assertTrue(pretty.contains("Aᵥ"))
        assertTrue(pretty.contains("Plain ${d}5 and ${d}10 stay literal."))
        // Table pipes survive normalization; netlist block extractable.
        assertTrue(normalized.contains("| Part | Value |"))
        assertEquals("R1 in out 10k", extractFirstNetlist(normalized)?.trim())
    }

    @Test
    fun e2eLightAndDarkShareTheSameHeadingLadder() {
        // Variants differ only in color scheme (composable); the size ladder
        // is fixed so both themes render identical title hierarchy.
        for (level in 1..6) {
            val size = chatHeadingFontSizeSp(level)
            assertTrue(size in 12f..22f)
        }
        assertTrue(chatHeadingFontSizeSp(1) > chatHeadingFontSizeSp(6))
        assertEquals(14f, chatHeadingFontSizeSp(99))
    }
}
