package com.jnd.ngdroid.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlotToolTest {

    @Test
    fun parseNullOnEmpty() {
        assertNull(parseRenderPlotArgs(""))
        assertNull(parseRenderPlotArgs("{}"))
    }

    @Test
    fun parseVectorAllowlist() {
        assertEquals(
            listOf("v(out)"),
            parseRenderPlotArgs("""{"vectors":["v(out)"]}""")
        )
    }

    @Test
    fun parseCapsAtSixAndTrims() {
        val args = (1..9).joinToString(",", """{"vectors":[""", "]}") { "\"v($it)\" " }
        val out = parseRenderPlotArgs(args)
        assertEquals(6, out?.size)
    }

    @Test
    fun toolReturnsImageResult() {
        val img = LlmImage("image/jpeg", "AAAA", "plot.jpg")
        val tool = RenderPlotTool { ToolResult("plot: v(out)", listOf(img)) }
        val res: ToolResult = runBlocking { tool.executeEx("""{"vectors":["v(out)"]}""") }
        assertTrue("plot:" in res.text)
        assertEquals(1, res.images.size)
        assertEquals("image/jpeg", res.images[0].mimeType)
    }

    @Test
    fun legacyExecuteStillTextOnly() {
        val tool = RenderPlotTool { ToolResult("plot: v(out)") }
        val text: String = runBlocking { tool.execute("{}") }
        assertTrue("plot:" in text)
    }
}
