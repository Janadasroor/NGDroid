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

package com.jnd.ngdroid.ui.plot

import com.jnd.ngdroid.engine.VectorSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTracesTest {

    private fun vec(name: String, vararg v: Double) =
        VectorSeries(name = name, values = v.toList())

    private fun eval(src: String, vararg vecs: VectorSeries) =
        evalMathExpr(src, vecs.toList())

    @Test
    fun scalarMultiply() {
        val out = eval("v(n)*5", vec("v(n)", 1.0, 2.0))!!
        assertEquals("v(n)*5", out.name)
        assertEquals(listOf(5.0, 10.0), out.values)
    }

    @Test
    fun traceNameKeepsEquationVerbatim() {
        val out = eval("v(n) * 5", vec("v(n)", 1.0))!!
        assertEquals("v(n) * 5", out.name)
    }

    @Test
    fun subtractTwoTraces() {
        val out = eval(
            "v(out)-v(in)",
            vec("v(out)", 5.0, 4.0),
            vec("v(in)", 1.0, 1.5)
        )!!
        assertEquals(listOf(4.0, 2.5), out.values)
    }

    @Test
    fun precedenceAndParens() {
        val v = vec("a", 2.0)
        assertEquals(8.0, eval("a+2*3", v)!!.values[0], 1e-12)
        assertEquals(12.0, eval("(a+2)*3", v)!!.values[0], 1e-12)
        assertEquals(8.0, eval("2^a+4", v)!!.values[0], 1e-12)
        assertEquals(1.0, eval("-a+3", v)!!.values[0], 1e-12)
    }

    @Test
    fun spiceSuffixes() {
        val v = vec("a", 1.0)
        assertEquals(2000.0, eval("a*2k", v)!!.values[0], 1e-9)
        assertEquals(1e6, eval("a*1M", v)!!.values[0], 1.0)
        assertEquals(0.001, eval("a*1m", v)!!.values[0], 1e-12)
        assertEquals(3.32e-8, eval("33.2e-9+a*0", v)!!.values[0], 1e-18)
    }

    @Test
    fun divByZeroYieldsNaN() {
        val out = eval("a/b", vec("a", 1.0, 2.0), vec("b", 2.0, 0.0))!!
        assertEquals(0.5, out.values[0], 1e-12)
        assertTrue(out.values[1].isNaN())
    }

    @Test
    fun functions() {
        val v = vec("v(out)", 1.0, -4.0)
        assertEquals(0.0, eval("db(v(out))", v)!!.values[0], 1e-9)
        assertEquals(listOf(1.0, 4.0), eval("abs(v(out))", v)!!.values)
        assertEquals(2.0, eval("sqrt(abs(v(out)))", vec("v(out)", 4.0))!!.values[0], 1e-12)
    }

    @Test
    fun dbZeroFloorsNeverInf() {
        val out = eval("db(v(out))", vec("v(out)", 0.0))!!
        assertEquals(-600.0, out.values[0], 1e-9)
        assertTrue(out.values[0].isFinite())
    }

    @Test
    fun vectorNamesWithParens() {
        val out = eval(
            "v(out)-i(vcc)",
            vec("v(out)", 5.0),
            vec("i(vcc)", 0.5)
        )!!
        assertEquals(4.5, out.values[0], 1e-12)
    }

    @Test
    fun invalidInputsReturnNull() {
        val v = vec("a", 1.0)
        assertNull(eval("", v))
        assertNull(eval("a+", v))
        assertNull(eval("(a", v))
        assertNull(eval("b*2", v)) // unknown trace
        assertNull(eval("2*3", v)) // needs a vector
        assertNull(eval("foo(a)", v)) // unknown name resolves as vector -> unknown
    }

    @Test
    fun validationMessages() {
        val vecs = listOf(vec("v(n)", 1.0, 2.0, 3.0)).associateBy { it.name }
        val ok = validateMathExpr("v(n)*5", vecs)
        assertTrue(ok.ok)
        assertTrue("3 pts" in ok.message)
        assertTrue(!validateMathExpr("v(n)+", vecs).ok)
        val missing = validateMathExpr("v(x)*2", vecs)
        assertTrue(!missing.ok)
        assertTrue("v(x)" in missing.message)
    }

    @Test
    fun currentFollowsOperandsUnlessDb() {
        val i1 = vec("v1#branch", 1.0, 2.0)
        val i2 = vec("v2#branch", 3.0, 4.0)
        val v = vec("v(out)", 5.0, 6.0)
        assertTrue(eval("v1#branch-v2#branch", i1, i2)!!.isCurrent)
        assertTrue(!eval("v1#branch-v(out)", i1, v)!!.isCurrent)
        assertTrue(!eval("db(v1#branch)", i1)!!.isCurrent)
    }

    @Test
    fun caseInsensitiveResolve() {
        val out = eval("V(OUT)*2", vec("v(out)", 3.0))!!
        assertEquals(6.0, out.values[0], 1e-12)
    }
}
