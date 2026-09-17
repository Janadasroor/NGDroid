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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiceValidatorCopyTest {

    @Test
    fun validRcPasses() {
        val netlist = "* RC low-pass\nV1 in 0 AC 1 SIN(0 1 1k)\nR1 in out 1k\nC1 out 0 100n\n.tran 0.1m 10m\n.end"
        val result = SpiceValidator.validate(netlist)
        assertTrue(result.errors.joinToString(), result.isValid)
    }

    @Test
    fun missingEndFails() {
        val netlist = "* RC low-pass\nR1 in out 1k\nC1 out 0 100n"
        val result = SpiceValidator.validate(netlist)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains(".end") })
    }

    @Test
    fun undefinedSubcktRefFails() {
        val netlist = "* X test\nX1 in out INV\n.tran 1n 10n\n.end"
        val result = SpiceValidator.validate(netlist)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("INV") })
    }

    @Test
    fun undefinedTransistorModelFails() {
        val netlist = "* Q test\nQ1 c b e 0 qmissing\nVcc c 0 5\n.tran 1u 1m\n.end"
        val result = SpiceValidator.validate(netlist)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("qmissing") })
    }

    @Test
    fun duplicateInstanceFails() {
        val netlist = "* dup\nR1 in out 1k\nR1 out 0 2k\n.end"
        val result = SpiceValidator.validate(netlist)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("duplicate") })
    }

    @Test
    fun unbalancedEndsFails() {
        val netlist = "* unbalanced\n.ends\n.end"
        val result = SpiceValidator.validate(netlist)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains(".ends") })
    }

    @Test
    fun controlBlockRejectedForAndroid() {
        val netlist = "* ctrl\nV1 in 0 1\nR1 in 0 1k\n.control\nrun\n.endc\n.end"
        val result = SpiceValidator.validate(netlist)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains(".control") })
    }

    @Test
    fun complexSubcktWithForwardRefsPasses() {
        val netlist = """
            * CMOS inverter (model defined after use)
            X1 vdd vss in out INV
            Vdd vdd 0 5
            Vin in 0 PULSE(0 5 0 1n 1n 50n 100n)
            .subckt INV vdd vss vin vout
            M1 vout vin vdd vdd pmos1
            M2 vout vin vss vss nmos1
            .ends INV
            .model pmos1 pmos(vto=-0.8 kp=20u)
            .model nmos1 nmos(vto=0.8 kp=50u)
            .tran 1n 200n
            .end
        """.trimIndent()
        val result = SpiceValidator.validate(netlist)
        assertTrue(result.errors.joinToString(), result.isValid)
    }

    @Test
    fun allTemplatesValidate() {
        for (kind in listOf("rc", "rlc", "diode", "bjt", "opamp")) {
            val result = SpiceValidator.validate(GenerateNetlistTemplateTool.templateFor(kind))
            assertTrue("$kind: ${result.errors}", result.isValid)
        }
    }
}
