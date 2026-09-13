package com.jnd.ngdroid.agent

import com.jnd.ngdroid.ui.assistant.ChatMsg
import com.jnd.ngdroid.ui.assistant.ChatRoleUi
import com.jnd.ngdroid.ui.assistant.argsUrl
import com.jnd.ngdroid.ui.assistant.faviconUrl
import com.jnd.ngdroid.ui.assistant.hostOfUrl
import com.jnd.ngdroid.ui.assistant.SearchSummary
import com.jnd.ngdroid.ui.assistant.argsQuery
import com.jnd.ngdroid.ui.assistant.humanizeTool
import com.jnd.ngdroid.ui.assistant.isErrorStep
import com.jnd.ngdroid.ui.assistant.liveThinkingSteps
import com.jnd.ngdroid.ui.assistant.liveThinkingTitle
import com.jnd.ngdroid.ui.assistant.pagesForHost
import com.jnd.ngdroid.ui.assistant.parseSearchSummary
import com.jnd.ngdroid.ui.assistant.pastThinkingTitle
import com.jnd.ngdroid.ui.assistant.searchSummaryTitle
import com.jnd.ngdroid.ui.assistant.stepsByAssistant
import com.jnd.ngdroid.ui.assistant.summarizeObservation
import com.jnd.ngdroid.ui.assistant.thinkingTitle
import com.jnd.ngdroid.ui.assistant.toolCallLabel
import com.jnd.ngdroid.ui.assistant.toolStepDetail
import com.jnd.ngdroid.ui.assistant.toolStepTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingUtilsTest {

    @Test
    fun liveTitleMapsToolCallsToVerbs() {
        assertEquals("Validating…", thinkingTitle("Calling validate_netlist…", null))
        assertEquals("Applying…", thinkingTitle("Calling apply_netlist…", null))
        assertEquals("Running…", thinkingTitle("Calling run_simulation…", null))
        assertEquals("Generating…", thinkingTitle("Calling generate_netlist_template…", null))
    }

    @Test
    fun liveTitleKeepsProviderContactLine() {
        assertEquals("Contacting OpenCode Zen…", thinkingTitle("Contacting OpenCode Zen…", null))
    }

    @Test
    fun clearedStatusFallsBackToLastStepPastTense() {
        assertEquals("Validated", thinkingTitle(null, "validate_netlist: VALID"))
        assertEquals("Applied", thinkingTitle(null, "Calling apply_netlist…"))
        assertEquals("Thinking…", thinkingTitle(null, null))
        assertEquals("Thinking…", thinkingTitle("  ", null))
    }

    @Test
    fun pastTitleSummarizesSteps() {
        val steps = listOf(
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling validate_netlist…"),
            ChatMsg(id = "s2", role = ChatRoleUi.SYSTEM, text = "validate_netlist: VALID")
        )
        assertEquals("Validated • 2 steps", pastThinkingTitle(steps))
    }

    @Test
    fun liveStepsAreOnlyTheRunningTurn() {
        val msgs = listOf(
            ChatMsg(id = "u1", role = ChatRoleUi.USER, text = "first"),
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling validate_netlist…"),
            ChatMsg(id = "a1", role = ChatRoleUi.ASSISTANT, text = "done"),
            ChatMsg(id = "u2", role = ChatRoleUi.USER, text = "second"),
            ChatMsg(id = "s2", role = ChatRoleUi.SYSTEM, text = "Calling apply_netlist…")
        )
        val live = liveThinkingSteps(msgs)
        assertEquals(listOf("s2"), live.map { it.id })
    }

    @Test
    fun stepsGroupedPerAnswer() {
        val msgs = listOf(
            ChatMsg(id = "u1", role = ChatRoleUi.USER, text = "first"),
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling validate_netlist…"),
            ChatMsg(id = "a1", role = ChatRoleUi.ASSISTANT, text = "done"),
            ChatMsg(id = "u2", role = ChatRoleUi.USER, text = "second"),
            ChatMsg(id = "a2", role = ChatRoleUi.ASSISTANT, text = "no tools used")
        )
        val grouped = stepsByAssistant(msgs)
        assertEquals(listOf("s1"), grouped["a1"]!!.map { it.id })
        assertTrue(grouped["a2"]!!.isEmpty())
    }

    @Test
    fun hostAndArgsHelpers() {
        assertEquals("ti.com", hostOfUrl("https://www.ti.com/lit/ds/x.pdf"))
        assertEquals("example.com", hostOfUrl("http://example.com:8080/a?b=1"))
        assertEquals(
            "https://example.com/page",
            argsUrl("""{"url":"https://example.com/page"}""")
        )
        assertEquals(
            "https://example.com/x",
            argsUrl("""{"command":"curl -sSL https://example.com/x"}""")
        )
        assertTrue("ti.com" in faviconUrl("www.ti.com"))
    }

    @Test
    fun observationPrefixesCarryCountsAndUrls() {
        val search = summarizeObservation(
            "web_search",
            "1. TL494\n   https://www.ti.com/product/TL494\n   snippet\n" +
                "2. PDF\n   https://www.ti.com/lit/ds/x.pdf\n   snippet",
            ""
        )
        assertTrue(search.startsWith("web_search: found 2 pages"))
        assertTrue("ti.com" in search)

        val read = summarizeObservation(
            "fetch_url",
            "some page text here",
            """{"url":"https://example.com/datasheet"}"""
        )
        assertTrue(read.startsWith("fetch_url: read https://example.com/datasheet"))

        val failed = summarizeObservation(
            "fetch_url",
            "ERROR: HTTP 404 for https://example.com/x",
            """{"url":"https://example.com/x"}"""
        )
        assertTrue(failed.contains("failed https://example.com/x"))
    }

    @Test
    fun summaryGroupsFoundAndRead() {
        val steps = listOf(
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling web_search…"),
            ChatMsg(
                id = "s2", role = ChatRoleUi.SYSTEM,
                text = "web_search: found 5 pages (ti.com, analog.com): 1. TL494 https://www.ti.com/product/TL494"
            ),
            ChatMsg(id = "s3", role = ChatRoleUi.SYSTEM, text = "Calling fetch_url…"),
            ChatMsg(
                id = "s4", role = ChatRoleUi.SYSTEM,
                text = "fetch_url: read https://www.ti.com/product/TL494 — page text"
            ),
            ChatMsg(
                id = "s5", role = ChatRoleUi.SYSTEM,
                text = "fetch_url: read https://analog.com/page — page text"
            )
        )
        val summary = parseSearchSummary(steps)
        assertEquals(5, summary.foundTotal)
        assertTrue("ti.com" in summary.foundHosts)
        assertEquals(2, summary.readOk.size)
        assertEquals("Found 5 pages • Read 2 pages", searchSummaryTitle(summary))
    }

    @Test
    fun pastTitlePrefersSearchSummary() {
        val steps = listOf(
            ChatMsg(id = "s1", role = ChatRoleUi.SYSTEM, text = "Calling web_search…"),
            ChatMsg(
                id = "s2", role = ChatRoleUi.SYSTEM,
                text = "web_search: found 20 pages (ti.com): 1. TL494 https://www.ti.com/x"
            ),
            ChatMsg(
                id = "s3", role = ChatRoleUi.SYSTEM,
                text = "fetch_url: read https://www.ti.com/x — text"
            )
        )
        assertEquals("Found 20 pages • Read 1 page", pastThinkingTitle(steps))
    }

    @Test
    fun liveTitleShowsSearchProgress() {
        val steps = listOf(
            ChatMsg(
                id = "s1", role = ChatRoleUi.SYSTEM,
                text = "web_search: found 12 pages (ti.com): 1. A https://www.ti.com/a"
            )
        )
        val title = liveThinkingTitle("Calling fetch_url…", steps)
        assertTrue("Found 12 pages" in title)
        assertTrue(title.startsWith("Reading…"))
    }

    @Test
    fun callLabelsNameQueryHostAndFile() {
        assertEquals(
            "Searching “tl494 datasheet”…",
            toolCallLabel("web_search", """{"query":"tl494 datasheet"}""")
        )
        assertEquals("Searching…", toolCallLabel("web_search", "{}"))
        assertEquals(
            "Searching images “555 pinout”…",
            toolCallLabel("image_search", """{"query":"555 pinout"}""")
        )
        assertEquals(
            "Reading ti.com…",
            toolCallLabel("fetch_url", """{"url":"https://www.ti.com/product/TL494"}""")
        )
        assertEquals(
            "Downloading tl494.pdf…",
            toolCallLabel("download_file", """{"url":"https://x/y","fileName":"tl494.pdf"}""")
        )
        assertEquals("Validating…", toolCallLabel("validate_netlist", "{}"))
        assertEquals("Listing files…", toolCallLabel("list_files", "{}"))
        assertEquals("tl494 datasheet", argsQuery("""{"query":"tl494 datasheet"}"""))
        assertEquals("List Files", humanizeTool("list_files"))
    }

    @Test
    fun stepTitlesAreFriendly() {
        assertEquals("Searching “tl494”…", toolStepTitle("Searching “tl494”…"))
        assertEquals("Validating…", toolStepTitle("Calling validate_netlist…"))
        assertEquals(
            "Found 5 pages for “tl494”",
            toolStepTitle("web_search “tl494”: found 5 pages (ti.com): 1. A https://www.ti.com/a")
        )
        assertEquals(
            "Read ti.com",
            toolStepTitle("fetch_url: read https://www.ti.com/x — page text")
        )
        assertEquals(
            "Couldn't read example.com",
            toolStepTitle("fetch_url: failed https://example.com/x — Error: HTTP 404")
        )
        assertEquals("Netlist valid", toolStepTitle("validate_netlist: VALID"))
        assertTrue(toolStepDetail("fetch_url: read https://www.ti.com/x — page text here").isNotBlank())
    }

    @Test
    fun errorToneFlagsFailuresOnly() {
        assertTrue(isErrorStep("fetch_url: failed https://example.com/x — Error: HTTP 404"))
        assertTrue(isErrorStep("ERROR: timeout"))
        assertTrue(isErrorStep("validate_netlist: INVALID:\n- bad"))
        assertTrue(isErrorStep("Validation issues"))
        assertTrue(!isErrorStep("fetch_url: read https://www.ti.com/x — page text"))
        assertTrue(!isErrorStep("web_search “q”: found 5 pages (ti.com): 1. A"))
        assertTrue(!isErrorStep("Searching “tl494”…"))
        assertTrue(!isErrorStep("validate_netlist: VALID"))
    }

    @Test
    fun hostPagesGroupFoundAndRead() {
        val summary = SearchSummary(
            foundTotal = 2,
            foundHosts = listOf("ti.com"),
            foundSamples = listOf(
                com.jnd.ngdroid.ui.assistant.FoundPage("TL494", "https://www.ti.com/tl494", "ti.com")
            ),
            readPages = listOf(
                com.jnd.ngdroid.ui.assistant.ReadPage("https://www.ti.com/tl494", "ti.com", true),
                com.jnd.ngdroid.ui.assistant.ReadPage("https://analog.com/x", "analog.com", true)
            )
        )
        val pages = pagesForHost(summary, "ti.com")
        assertEquals(1, pages.size)
        assertEquals("https://www.ti.com/tl494", pages[0].url)
        assertTrue(pagesForHost(summary, "unknown.test").isEmpty())
    }
}
