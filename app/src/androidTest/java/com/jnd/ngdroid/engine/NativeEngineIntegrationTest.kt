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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NativeEngineIntegrationTest {

    @Test
    fun initAndRunSimpleNetlist_completes() {
        var initialized = false
        val latch = CountDownLatch(1)
        var receivedInitData = false

        val callback = object : NgSpiceCallback {
            override fun onLog(msg: String) {
                // Ignore for test
            }

            override fun onInitData(scaleName: String, title: String, name: String, type: String, vecNames: Array<String>) {
                receivedInitData = true
                latch.countDown()
            }

            override fun onData(vecNames: Array<String>, values: DoubleArray) {
                // Ignore for test
            }

            override fun onStatus(status: String) {
                // Ignore for test
            }
        }

        initialized = NativeNgSpice.nativeInit(callback)
        assertTrue("Native engine failed to initialize", initialized)

        val netlist = arrayOf(
            "* Simple DC Test",
            "V1 1 0 DC 5",
            "R1 1 0 1k",
            ".op",
            ".end"
        )

        val success = NativeNgSpice.nativeRunNetlist(netlist)
        assertTrue("Netlist run failed", success)

        // Wait for ngspice to parse and send initial plot data
        val waitSuccess = latch.await(2, TimeUnit.SECONDS)
        
        assertTrue("Should have received init data callback from native engine", receivedInitData)
    }
}
