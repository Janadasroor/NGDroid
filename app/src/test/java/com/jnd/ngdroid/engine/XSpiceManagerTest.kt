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

package com.jnd.ngdroid.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XSpiceManagerTest {

    @Test
    fun detectsXSpiceInNetlistWithADevices() {
        val xspiceNetlist = """
            * XSPICE Mixed-Signal Digital AND Gate
            V1 in1 0 PULSE(0 5 0 1u 1u 5m 10m)
            A_adc [in1] [d_in] adc_buff
            .model adc_buff d_adc(in_low=0.8 in_high=2.0)
            A_gate [d_in d_in] [d_out] gate_and
            .model gate_and d_and(rise_delay=10n fall_delay=10n)
            .tran 10u 20m
            .end
        """.trimIndent()

        assertTrue(XSpiceManager.containsXSpiceDevices(xspiceNetlist))
    }

    @Test
    fun returnsFalseForStandardAnalogNetlist() {
        val rcNetlist = """
            * RC Low-Pass Filter
            V1 in 0 5
            R1 in out 1k
            C1 out 0 1u
            .tran 10u 20m
            .end
        """.trimIndent()

        assertFalse(XSpiceManager.containsXSpiceDevices(rcNetlist))
    }
}
