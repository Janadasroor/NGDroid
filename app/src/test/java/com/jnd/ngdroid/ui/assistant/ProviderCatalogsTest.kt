package com.jnd.ngdroid.ui.assistant

import com.jnd.ngdroid.data.AgentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogsTest {

    @Test
    fun emptyByDefault() {
        val c = ProviderCatalogs()
        assertTrue(c.models(AgentProvider.GEMINI).isEmpty())
        assertFalse(c.has(AgentProvider.GEMINI))
    }

    @Test
    fun providersNeverOverwriteEachOther() {
        val c = ProviderCatalogs()
        c.store(AgentProvider.OPENCODE_ZEN, listOf("zen-a-free", "zen-b"), listOf("zen-a-free"))
        c.store(AgentProvider.GEMINI, listOf("gemini-2.5-flash"), emptyList())
        // Storing B must leave A's catalog untouched (the reported bug).
        assertEquals(listOf("zen-a-free", "zen-b"), c.models(AgentProvider.OPENCODE_ZEN))
        assertEquals(listOf("zen-a-free"), c.freeModels(AgentProvider.OPENCODE_ZEN))
        assertEquals(listOf("gemini-2.5-flash"), c.models(AgentProvider.GEMINI))
        // Re-storing A (late stale fetch) must not touch B either.
        c.store(AgentProvider.OPENCODE_ZEN, listOf("zen-a-free"), listOf("zen-a-free"))
        assertEquals(listOf("gemini-2.5-flash"), c.models(AgentProvider.GEMINI))
    }

    @Test
    fun clearDropsOneProvider() {
        val c = ProviderCatalogs()
        c.store(AgentProvider.OPENAI, listOf("gpt-4.1-mini"), emptyList())
        c.clear(AgentProvider.OPENAI)
        assertFalse(c.has(AgentProvider.OPENAI))
    }

    @Test
    fun autoRefreshOnlyOnFreshPasteWithEmptyCatalog() {
        // Fresh paste, nothing fetched yet -> fetch.
        assertTrue(shouldAutoRefreshOnKeySave("sk-abc", true))
        // Blank key -> never fetch.
        assertFalse(shouldAutoRefreshOnKeySave("   ", true))
        // Catalog already present -> no fetch per keystroke.
        assertFalse(shouldAutoRefreshOnKeySave("sk-abc", false))
    }
}
