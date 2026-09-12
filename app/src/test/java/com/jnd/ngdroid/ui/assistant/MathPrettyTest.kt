package com.jnd.ngdroid.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathPrettyTest {

    @Test
    fun cutoffFrequencyRendersReadably() {
        assertEquals("f_c = (1)/(2π RC)", prettyMath("f_c = \\frac{1}{2\\pi RC}"))
    }

    @Test
    fun quadraticFormulaKeepsStructure() {
        val out = prettyMath("x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}")
        assertTrue(out.contains("x ="))
        assertTrue(out.contains("±"))
        assertTrue(out.contains("√"))
    }

    @Test
    fun transferFunctionAndExponent() {
        assertEquals("H(s) = (1)/(1 + sRC)", prettyMath("H(s) = \\frac{1}{1 + sRC}"))
        assertEquals("e⁻ʲʷᵗ", prettyMath("e^{-jwt}"))
    }

    @Test
    fun greekAndOperatorsMapToUnicode() {
        assertEquals("α + β → ∞", prettyMath("\\alpha + \\beta \\rightarrow \\infty"))
        assertEquals("Vₒᵤₜ = Vᵢₙ · Aᵥ", prettyMath("V_{out} = V_{in} \\cdot A_v"))
    }

    @Test
    fun sumAndIntegralKeepLimits() {
        assertEquals("∑ᵢ₌₁ᴺ i²", prettyMath("\\sum_{i=1}^{N} i^2"))
        assertEquals("∫₀^∞ e⁻ˣdx", prettyMath("\\int_0^\\infty e^{-x}dx"))
    }

    @Test
    fun matrixStaysOnOneReadableLine() {
        val out = prettyMath("A = \\begin{bmatrix} 1&0\\\\ 0&1 \\end{bmatrix}")
        assertEquals("A = [1 0 ￨ 0 1]", out)
    }

    @Test
    fun inlineMathPrettifiedButCurrencyKept() {
        assertEquals("It costs 5 dollars.", prettifyInlineMath("It costs 5 dollars."))
        assertEquals("Plain \$5 and \$10 text.", prettifyInlineMath("Plain \$5 and \$10 text."))
        assertEquals("Gain Aᵥ = -R_c/R_E", prettifyInlineMath("Gain \$A_v = -R_c/R_E\$"))
        assertEquals("With V=IR inline.", prettifyInlineMath("With \\(V=IR\\) inline."))
    }

    @Test
    fun codeSpansAreNeverTreatedAsMath() {
        val md = "```spice\nR1 in out 5k\n```\nPlain \$5 and \$10 text."
        assertFalse(hasAnyMath(md))
        assertEquals(md, prettifyInlineMath(md))
    }

    @Test
    fun displaySplitKeepsProseAndEquations() {
        val md = "Cutoff:\n\$\$f_c = \\frac{1}{2\\pi RC}\$\$\nDone."
        val segs = parseDocSegments(md)
        assertEquals(3, segs.size)
        assertTrue(segs[0] is DocSegment.Prose)
        assertTrue(segs[1] is DocSegment.DisplayMath)
        assertTrue(segs[2] is DocSegment.Prose)
        assertTrue(hasDisplayMath(md))
    }

    @Test
    fun bracketDisplayFormSplits() {
        val segs = parseDocSegments("A \\[x^2\\] B")
        assertEquals(3, segs.size)
        assertEquals("x^2", (segs[1] as DocSegment.DisplayMath).latex)
    }

    @Test
    fun neverThrowsOnGarbage() {
        assertEquals("", prettyMath(""))
        assertEquals("unclosed", prettyMath("\$\$unclosed"))
        assertTrue(prettifyInlineMath("\$unclosed").isNotEmpty())
    }
}
