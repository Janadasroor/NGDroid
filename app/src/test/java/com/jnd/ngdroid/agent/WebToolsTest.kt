package com.jnd.ngdroid.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebToolsTest {

    private val liteHtml = """
        <html><body>
        <table><tr><td valign="top">1.&nbsp;</td><td>
        <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.onsemi.com%2F1n4148&amp;rut=abc">1N4148 Small Signal Diode</a>
        <br><span class='link-text'>onsemi.com/1n4148</span>
        <table><tr><td class='result-snippet'>Fast switching diode, 100V reverse voltage &amp; 200mA current.</td></tr></table>
        </td></tr></table>
        <table><tr><td valign="top">2.&nbsp;</td><td>
        <a rel="nofollow" href="https://example.com/direct"><b>Direct</b> Link Result</a>
        <table><tr><td class='result-snippet'>Plain direct link.</td></tr></table>
        </td></tr></table>
        <a rel="nofollow" href="/lite/?q=more&amp;s=10">Next</a>
        </td></tr></table>
        </body></html>
    """.trimIndent()

    @Test
    fun liteParserUnwrapsUddgAndSkipsInternalLinks() {
        val results = parseDuckDuckGoLite(liteHtml, 5)
        assertEquals(2, results.size)
        assertEquals("1N4148 Small Signal Diode", results[0].title)
        assertEquals("https://www.onsemi.com/1n4148", results[0].url)
        assertEquals("Fast switching diode, 100V reverse voltage & 200mA current.", results[0].snippet)
        assertEquals("Direct Link Result", results[1].title)
        assertEquals("https://example.com/direct", results[1].url)
    }

    @Test
    fun liteParserRespectsMaxResults() {
        assertEquals(1, parseDuckDuckGoLite(liteHtml, 1).size)
        assertTrue(parseDuckDuckGoLite("", 5).isEmpty())
        assertTrue(parseDuckDuckGoLite(liteHtml, 0).isEmpty())
    }

    @Test
    fun htmlToTextStripsScriptsAndCollapses() {
        val html = "<html><head><style>.x{color:red}</style>" +
            "<script>alert(1)</script></head><body>" +
            "<h1>Title &amp; more</h1><!-- hidden --><p>First  paragraph.</p><p>Second.</p>" +
            "</body></html>"
        val text = htmlToText(html)
        assertTrue("alert" !in text)
        assertTrue("color" !in text)
        assertTrue("Title & more" in text)
        assertTrue("First paragraph." in text)
        assertTrue("Second." in text)
    }

    @Test
    fun searchToolFormatsResults() = runTest {
        val tool = WebSearchTool(httpGet = { _, _ -> liteHtml })
        val out = tool.execute("""{"query":"1n4148","count":2}""")
        assertTrue("1. 1N4148 Small Signal Diode" in out)
        assertTrue("https://www.onsemi.com/1n4148" in out)
        assertTrue("2. Direct Link Result" in out)
    }

    @Test
    fun searchToolHandlesEmptyAndErrors() = runTest {
        val empty = WebSearchTool(httpGet = { _, _ -> "<html></html>" })
        assertTrue("No results" in empty.execute("""{"query":"zzzqqq"}"""))
        assertTrue(
            "ERROR" in empty.execute("""{}""")
        )
        val failing = WebSearchTool(httpGet = { _, _ -> throw IllegalStateException("HTTP 403") })
        assertTrue(failing.execute("""{"query":"x"}""").startsWith("ERROR:"))
    }

    @Test
    fun braveParserReadsWebResults() {
        val json = """
            {"query":{"original":"1n4148"},"web":{"results":[
              {"title":"1N4148 Diode","url":"https://www.onsemi.com/1n4148","description":"Fast switching diode."},
              {"title":"No URL here","description":"skipped"},
              {"title":"Bad scheme","url":"ftp://example.com/x","description":"skipped"}
            ]}}
        """.trimIndent()
        val results = parseBraveSearch(json, 5)
        assertEquals(1, results.size)
        assertEquals("1N4148 Diode", results[0].title)
        assertEquals("https://www.onsemi.com/1n4148", results[0].url)
        assertEquals("Fast switching diode.", results[0].snippet)
        assertTrue(parseBraveSearch("not json", 5).isEmpty())
        assertTrue(parseBraveSearch("{}", 5).isEmpty())
    }

    @Test
    fun searchToolUsesBraveWhenKeySet() = runTest {
        var sawUrl = ""
        var sawHeaders: Map<String, String> = emptyMap()
        val braveJson = """{"web":{"results":[
            {"title":"Brave Hit","url":"https://brave.example/hit","description":"From Brave."}
        ]}}"""
        val tool = WebSearchTool(
            httpGet = { url, headers -> sawUrl = url; sawHeaders = headers; braveJson },
            searchKeyProvider = { "  test-key-123 " }
        )
        val out = tool.execute("""{"query":"1n4148","count":3}""")
        assertTrue("api.search.brave.com" in sawUrl)
        assertTrue("q=1n4148" in sawUrl)
        assertEquals("test-key-123", sawHeaders["X-Subscription-Token"])
        assertTrue("1. Brave Hit" in out)
        assertTrue("https://brave.example/hit" in out)
    }

    @Test
    fun searchToolFallsBackToDuckOnRejectedKey() = runTest {
        // Brave rejects bad subscription tokens with HTTP 422.
        val badKey = WebSearchTool(
            httpGet = { url, _ ->
                if ("brave" in url) throw IllegalStateException("HTTP 422 for $url")
                liteHtml
            },
            searchKeyProvider = { "bad-key" }
        )
        val out = badKey.execute("""{"query":"1n4148","count":1}""")
        assertTrue("rejected" in out)
        assertTrue("1N4148 Small Signal Diode" in out)

        val noQuota = WebSearchTool(
            httpGet = { url, _ ->
                if ("brave" in url) throw IllegalStateException("HTTP 429 for $url")
                liteHtml
            },
            searchKeyProvider = { "key" }
        )
        val quotaOut = noQuota.execute("""{"query":"1n4148","count":1}""")
        assertTrue("quota" in quotaOut)
        assertTrue("1N4148 Small Signal Diode" in quotaOut)
    }

    @Test
    fun searchToolSurfacesNonKeyBraveErrors() = runTest {
        val tool = WebSearchTool(
            httpGet = { _, _ -> throw IllegalStateException("HTTP 500: server error") },
            searchKeyProvider = { "key" }
        )
        val out = tool.execute("""{"query":"x"}""")
        assertTrue(out.startsWith("ERROR:"))
        assertTrue("rejected" !in out)
        assertTrue("DuckDuckGo" !in out)
    }

    @Test
    fun fetchToolReadsTextAndRejectsSchemes() = runTest {
        val tool = FetchUrlTool(httpGet = { _, _ -> "<p>Hello <b>world</b></p><script>evil()</script>" })
        assertEquals("Hello world", tool.execute("""{"url":"https://example.com/x"}"""))
        assertTrue(tool.execute("""{"url":"ftp://example.com/x"}""").startsWith("ERROR:"))
        val failing = FetchUrlTool(httpGet = { _, _ -> throw IllegalStateException("timeout") })
        assertTrue(failing.execute("""{"url":"https://example.com/"}""").startsWith("ERROR:"))
    }

    @Test
    fun fetchToolRejectsFileDownloads() = runTest {
        var calls = 0
        val tool = FetchUrlTool(httpGet = { _, _ -> calls++; "<p>unreachable</p>" })
        val pdf = tool.execute("""{"url":"https://www.ti.com/lit/ds/symlink/tl071.pdf"}""")
        assertTrue(pdf.startsWith("ERROR:"))
        assertTrue("PDF" in pdf)
        assertEquals(0, calls)
        val zip = tool.execute("""{"url":"https://example.com/a.zip?v=2"}""")
        assertTrue(zip.startsWith("ERROR:"))
        assertEquals(0, calls)
    }
}
