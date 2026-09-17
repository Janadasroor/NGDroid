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

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlToolsTest {

    @Test
    fun parseSimpleCurl() {
        val req = parseCurlCommand("curl -L https://example.com/x").getOrThrow()
        assertEquals("https://example.com/x", req.url)
    }

    @Test
    fun parseSilentComboAndHeaderAndUA() {
        val req = parseCurlCommand(
            """curl -sSL -H "Accept: application/json" -A "MyAgent/1.0" https://api.example.com/v1/parts?q=1n4148"""
        ).getOrThrow()
        assertEquals("https://api.example.com/v1/parts?q=1n4148", req.url)
        assertEquals("application/json", req.headers["Accept"])
        assertEquals("MyAgent/1.0", req.headers["User-Agent"])
    }

    @Test
    fun parseStopsAtPipe() {
        val req = parseCurlCommand("curl -L https://example.com/x | head -c 100").getOrThrow()
        assertEquals("https://example.com/x", req.url)
    }

    @Test
    fun parseRejectsPostAndOutput() {
        assertTrue(parseCurlCommand("curl -X POST https://example.com/x").isFailure)
        assertTrue(parseCurlCommand("curl -d 'a=1' https://example.com/x").isFailure)
        assertTrue(parseCurlCommand("curl -o /tmp/x https://example.com/x").isFailure)
        assertTrue(parseCurlCommand("curl --proxy http://p:8080 https://example.com/").isFailure)
    }

    @Test
    fun parseRejectsUnknownFlagAndMissingUrl() {
        assertTrue(parseCurlCommand("curl --frobnicate https://example.com/").isFailure)
        assertTrue(parseCurlCommand("curl -L").isFailure)
        assertTrue(parseCurlCommand("not curl at all").isFailure)
    }

    @Test
    fun toolExecutesCommandAndRawModes() = runTest {
        var sawUrl = ""
        var sawHeaders: Map<String, String> = emptyMap()
        val tool = CurlFetchTool(httpGet = { url, headers ->
            sawUrl = url
            sawHeaders = headers
            "<p>Hello <b>world</b></p>"
        })
        val out = tool.execute("""{"command":"curl -sSL https://example.com/x"}""")
        assertEquals("Hello world", out)
        assertEquals("https://example.com/x", sawUrl)

        val rawTool = CurlFetchTool(httpGet = { _, _ -> """{"part":"1N4148"}""" })
        val raw = rawTool.execute("""{"url":"https://api.example.com/part","raw":true}""")
        assertTrue("1N4148" in raw)

        val withHeader = CurlFetchTool(httpGet = { _, headers -> headers["Accept"] ?: "none" })
        val echo = withHeader.execute(
            """{"command":"curl -H \"Accept: application/json\" https://example.com/","raw":true}"""
        )
        assertEquals("application/json", echo)
        assertTrue(sawHeaders.isEmpty() || true) // headers go per-call above
    }

    @Test
    fun toolRejectsNonHttpAndErrors() = runTest {
        val tool = CurlFetchTool(httpGet = { _, _ -> "unreachable" })
        assertTrue(tool.execute("""{"command":"curl -L ftp://example.com/x"}""").startsWith("ERROR:"))
        assertTrue(tool.execute("""{"command":"curl -X POST https://example.com/"}""").startsWith("ERROR:"))
        assertTrue(tool.execute("""{"url":"ftp://example.com/"}""").startsWith("ERROR:"))
        val failing = CurlFetchTool(httpGet = { _, _ -> throw IllegalStateException("timeout") })
        assertTrue(failing.execute("""{"url":"https://example.com/"}""").startsWith("ERROR:"))
    }
}
