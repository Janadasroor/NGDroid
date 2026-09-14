package com.jnd.ngdroid.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageToolsTest {

    private val braveJson = """
        {"results":[
          {"title":"555 pinout","url":"https://example.com/555","source":"example.com",
           "thumbnail":{"src":"https://example.com/t.jpg"},
           "properties":{"url":"https://example.com/555.png"}},
          {"title":"No image here","url":"https://example.com/x"},
          {"title":"Bad scheme","url":"https://example.com/y",
           "properties":{"url":"ftp://example.com/z.png"}}
        ]}
    """.trimIndent()

    private val duckJson = """
        {"results":[
          {"image":"https://img.example.com/a.jpg","thumbnail":"https://img.example.com/t.jpg",
           "title":"NE555  pinout","url":"https://img.example.com/page"},
          {"image":"ftp://img.example.com/b.jpg","title":"skipped","url":"https://x.example/"}
        ],"next":"1"}
    """.trimIndent()

    @Test
    fun braveParserReadsImageResults() {
        val results = parseBraveImageSearch(braveJson, 5)
        assertEquals(1, results.size)
        assertEquals("555 pinout", results[0].title)
        assertEquals("https://example.com/555.png", results[0].imageUrl)
        assertEquals("https://example.com/555", results[0].pageUrl)
        assertEquals("https://example.com/t.jpg", results[0].thumbnailUrl)
        assertEquals("example.com", results[0].source)
        assertTrue(parseBraveImageSearch("not json", 5).isEmpty())
        assertTrue(parseBraveImageSearch("{}", 5).isEmpty())
        assertTrue(parseBraveImageSearch(braveJson, 0).isEmpty())
    }

    @Test
    fun duckParserReadsImageResults() {
        val results = parseDuckImageJson(duckJson, 5)
        assertEquals(1, results.size)
        assertEquals("https://img.example.com/a.jpg", results[0].imageUrl)
        assertEquals("NE555 pinout", results[0].title)
        assertEquals("https://img.example.com/page", results[0].pageUrl)
        assertTrue(parseDuckImageJson("garbage", 5).isEmpty())
    }

    @Test
    fun vqdExtractorFindsToken() {
        assertEquals(
            "4-abc123",
            extractVqd("""<script>vqd="4-abc123";</script>""")
        )
        assertEquals("4-xyz", extractVqd("vqd=4-xyz&other=1"))
        assertEquals(null, extractVqd("<html>no token</html>"))
    }

    @Test
    fun extractImageUrlsFindsMarkdownThenBare() {
        val text = "Here ![pinout](https://example.com/pin.png) and " +
            "https://cdn.example.com/photo.jpg plus text."
        val urls = extractImageUrls(text)
        assertEquals(
            listOf("https://example.com/pin.png", "https://cdn.example.com/photo.jpg"),
            urls
        )
    }

    @Test
    fun extractImageUrlsSkipsNonImagesDedupesAndCaps() {
        val text = "![doc](https://example.com/d.pdf) " +
            "https://example.com/a.png https://example.com/a.png, " +
            "https://example.com/b.JPEG?size=large."
        val urls = extractImageUrls(text, maxImages = 2)
        assertEquals(
            listOf("https://example.com/a.png", "https://example.com/b.JPEG?size=large"),
            urls
        )
        assertTrue(extractImageUrls("no urls here").isEmpty())
        assertTrue(extractImageUrls("![x](https://e.com/a.png)", maxImages = 0).isEmpty())
    }

    @Test
    fun splitChatSegmentsLiftsImagesInPlace() {
        val text = "**Schematic image:** ![Boost Converter](https://example.com/boost.png)\n\nTopology ok."
        val segs = splitChatSegments(text)
        assertEquals(3, segs.size)
        assertEquals(ChatSegment.Text("**Schematic image:** "), segs[0])
        assertEquals(
            ChatSegment.Image("https://example.com/boost.png", "Boost Converter"),
            segs[1]
        )
        assertEquals(ChatSegment.Text("\n\nTopology ok."), segs[2])
    }

    @Test
    fun splitChatSegmentsLeavesFencesAndBadUrlsAlone() {
        val text = "```spice\n![x](https://example.com/a.png)\n```\n\n" +
            "![doc](https://example.com/d.pdf) done"
        val segs = splitChatSegments(text)
        assertEquals(1, segs.size)
        assertEquals(ChatSegment.Text(text), segs[0])
        assertTrue(splitChatSegments("plain text").all { it is ChatSegment.Text })
    }

    @Test
    fun extractBareImageUrlsSkipsMarkdownImages() {
        val text = "![pin](https://example.com/pin.png) and https://cdn.example.com/p.jpg"
        assertEquals(listOf("https://cdn.example.com/p.jpg"), extractBareImageUrls(text))
        assertTrue(extractBareImageUrls("![x](https://e.com/a.png)").isEmpty())
    }

    @Test
    fun commonsParserResolvesSvgToPngPreview() {
        val json = """
            {"query":{"pages":{
              "2801106":{"pageid":2801106,"ns":6,"title":"File:NE555 Symbol.svg","index":1,
                "imageinfo":[{"url":"https://upload.wikimedia.org/wikipedia/commons/3/35/NE555_Symbol.svg?utm_source=x",
                  "thumburl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/3/35/NE555_Symbol.svg/330px-NE555_Symbol.svg.png?utm_source=x",
                  "responsiveUrls":{"2":"https://thumb.wikimedia.org/wikipedia/commons/thumb/3/35/NE555_Symbol.svg/960px-NE555_Symbol.svg.png?utm_source=x"},
                  "descriptionurl":"https://commons.wikimedia.org/wiki/File:NE555_Symbol.svg"}]},
              "99":{"pageid":99,"ns":6,"title":"File:555 timer.jpg","index":0,
                "imageinfo":[{"url":"https://upload.wikimedia.org/555.jpg",
                  "thumburl":"https://thumb.wikimedia.org/320px-555.jpg",
                  "descriptionurl":"https://commons.wikimedia.org/wiki/File:555_timer.jpg"}]}
            }}}
        """.trimIndent()
        val results = parseCommonsApi(json, 5)
        assertEquals(2, results.size)
        // Sorted by index: the jpg first, keeps its original URL.
        assertEquals("555 timer.jpg", results[0].title)
        assertEquals("https://upload.wikimedia.org/555.jpg", results[0].imageUrl)
        assertEquals("Wikimedia Commons", results[0].source)
        // SVG original resolves to the large PNG preview.
        assertEquals("NE555 Symbol.svg", results[1].title)
        assertEquals(
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/35/NE555_Symbol.svg/960px-NE555_Symbol.svg.png",
            results[1].imageUrl
        )
        assertTrue(parseCommonsApi("not json", 5).isEmpty())
        assertTrue(parseCommonsApi("{}", 5).isEmpty())
    }

    @Test
    fun toolFallsBackToCommonsWhenDuckIsBlocked() = runTest {
        val commonsJson = """{"query":{"pages":{
            "1":{"pageid":1,"ns":6,"title":"File:NE555 Symbol.svg","index":0,
              "imageinfo":[{"url":"https://upload.wikimedia.org/NE555_Symbol.svg",
                "thumburl":"https://thumb.wikimedia.org/330px-NE555_Symbol.svg.png",
                "descriptionurl":"https://commons.wikimedia.org/wiki/File:NE555_Symbol.svg"}]}}}}"""
        val tool = ImageSearchTool(
            httpGet = { url, _ ->
                if ("commons.wikimedia.org" in url) commonsJson
                else throw IllegalStateException("plain HttpGet must not serve DDG here")
            },
            cookieHttpGet = { _, _ ->
                throw IllegalStateException(
                    "HTTP 403 for https://duckduckgo.com/i.js: blocked"
                )
            }
        )
        val out = tool.execute("""{"query":"NE555 pinout","count":3}""")
        assertTrue("rate-limited" in out)
        assertTrue("Wikimedia Commons" in out)
        assertTrue("Image: https://thumb.wikimedia.org/330px-NE555_Symbol.svg.png" in out)
        assertTrue("![title](image-url)" in out)
    }

    @Test
    fun toolUsesBraveWhenKeySet() = runTest {
        var sawUrl = ""
        var sawHeaders: Map<String, String> = emptyMap()
        val tool = ImageSearchTool(
            httpGet = { url, headers -> sawUrl = url; sawHeaders = headers; braveJson },
            searchKeyProvider = { "key-1" }
        )
        val out = tool.execute("""{"query":"555 pinout","count":3}""")
        assertTrue("api.search.brave.com" in sawUrl)
        assertTrue("/images/search" in sawUrl)
        assertTrue("q=555+pinout" in sawUrl || "q=555%20pinout" in sawUrl)
        assertEquals("key-1", sawHeaders["X-Subscription-Token"])
        assertTrue("1. 555 pinout" in out)
        assertTrue("Image: https://example.com/555.png" in out)
        assertTrue("![title](image-url)" in out)
    }

    @Test
    fun toolFallsBackToDuckOnRejectedKey() = runTest {
        val tool = ImageSearchTool(
            httpGet = { url, _ ->
                if ("brave" in url) throw IllegalStateException("HTTP 422 for $url")
                throw IllegalStateException("plain HttpGet must not serve DDG here")
            },
            cookieHttpGet = { url, _ ->
                if ("i.js" in url) duckJson
                else """<html><script>vqd="4-test";</script></html>"""
            },
            searchKeyProvider = { "bad-key" }
        )
        val out = tool.execute("""{"query":"555 pinout","count":1}""")
        assertTrue("rejected" in out)
        assertTrue("Image: https://img.example.com/a.jpg" in out)
    }

    @Test
    fun toolUsesDuckWhenNoKey() = runTest {
        var sawReferer = ""
        val tool = ImageSearchTool(
            cookieHttpGet = { url, headers ->
                if ("i.js" in url) {
                    sawReferer = headers["Referer"].orEmpty()
                    duckJson
                } else """vqd='4-abc'"""
            }
        )
        val out = tool.execute("""{"query":"ne555"}""")
        assertEquals("https://duckduckgo.com/", sawReferer)
        assertTrue("Image: https://img.example.com/a.jpg" in out)
    }

    @Test
    fun toolHandlesEmptyAndErrors() = runTest {
        val empty = ImageSearchTool(
            httpGet = { _, _ -> "{}" },
            cookieHttpGet = { _, _ -> """{"results":[]}""" }
        )
        // No vqd token and empty Commons index -> honest empty message, not a crash.
        val noToken = empty.execute("""{"query":"x"}""")
        assertTrue("No images found" in noToken)
        val missing = empty.execute("""{}""")
        assertTrue("ERROR" in missing)
        val failing = ImageSearchTool(
            httpGet = { _, _ -> throw IllegalStateException("down") },
            cookieHttpGet = { _, _ -> throw IllegalStateException("timeout") }
        )
        assertTrue(failing.execute("""{"query":"x"}""").startsWith("ERROR:"))
    }
}
