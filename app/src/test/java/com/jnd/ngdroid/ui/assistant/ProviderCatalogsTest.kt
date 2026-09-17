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

    private fun sampleCatalogs() = listOf(
        ProviderCatalog(
            AgentProvider.OPENCODE_ZEN,
            listOf("mimo-v2.5-free", "big-pickle"),
            listOf("mimo-v2.5-free")
        ),
        ProviderCatalog(
            AgentProvider.GEMINI,
            listOf("gemini-2.5-flash"),
            emptyList()
        )
    )

    @Test
    fun allSearchKeepsProviderGroupsAndTags() {
        val out = searchAllCatalogs(sampleCatalogs(), "", ModelTierFilter.ALL)
        assertEquals(3, out.size)
        assertEquals(AgentProvider.OPENCODE_ZEN, out[0].provider)
        assertEquals("mimo-v2.5-free", out[0].id)
        assertTrue(out[0].free)
        assertEquals(AgentProvider.GEMINI, out[2].provider)
        assertFalse(out[2].free)
    }

    @Test
    fun allSearchFiltersAcrossProviders() {
        val out = searchAllCatalogs(sampleCatalogs(), "mimo", ModelTierFilter.ALL)
        assertEquals(1, out.size)
        assertEquals("mimo-v2.5-free", out[0].id)
    }

    @Test
    fun allSearchTierAppliesPerRow() {
        val free = searchAllCatalogs(sampleCatalogs(), "", ModelTierFilter.FREE)
        assertEquals(listOf("mimo-v2.5-free"), free.map { it.id })
        val keyed = searchAllCatalogs(sampleCatalogs(), "", ModelTierFilter.KEYED)
        assertEquals(2, keyed.size)
        assertTrue(keyed.all { !it.free })
    }

    @Test
    fun providerNameMatchSelectsWholeCatalog() {
        // "zen" matches no model id, but selects the Zen provider catalog.
        val out = searchAllCatalogs(sampleCatalogs(), "zen", ModelTierFilter.ALL)
        assertEquals(2, out.size)
        assertTrue(out.all { it.provider == AgentProvider.OPENCODE_ZEN })
        // Tier still applies on provider-name match.
        val freeOnly = searchAllCatalogs(sampleCatalogs(), "zen", ModelTierFilter.FREE)
        assertEquals(listOf("mimo-v2.5-free"), freeOnly.map { it.id })
    }

    @Test
    fun tierFilterIdsSplitsWithoutRanking() {
        val ids = listOf("b-free", "a", "b-free", " ")
        assertEquals(listOf("b-free", "a"), tierFilterIds(ids, ModelTierFilter.ALL, setOf("b-free")))
        assertEquals(listOf("b-free"), tierFilterIds(ids, ModelTierFilter.FREE, setOf("b-free")))
        assertEquals(listOf("a"), tierFilterIds(ids, ModelTierFilter.KEYED, setOf("b-free")))
    }

    @Test
    fun apiKeyForReadsEachProvider() {
        val s = com.jnd.ngdroid.data.AgentSettings(geminiApiKey = "g-key", zenApiKey = "z-key")
        assertEquals("g-key", s.apiKeyFor(AgentProvider.GEMINI))
        assertEquals("z-key", s.apiKeyFor(AgentProvider.OPENCODE_ZEN))
        assertEquals("", s.apiKeyFor(AgentProvider.OPENAI))
        assertEquals("g-key", s.copy(provider = AgentProvider.GEMINI).activeApiKey())
    }

    @Test
    fun eligibilityIsZenPlusKeyedProviders() {
        val none = com.jnd.ngdroid.data.AgentSettings()
        assertEquals(
            listOf(AgentProvider.OPENCODE_ZEN),
            eligibleCatalogProviders(none)
        )
        val keyed = none.copy(geminiApiKey = "g-key", openaiApiKey = "   ", anthropicApiKey = "a-key")
        assertEquals(
            listOf(AgentProvider.GEMINI, AgentProvider.ANTHROPIC, AgentProvider.OPENCODE_ZEN),
            eligibleCatalogProviders(keyed)
        )
    }
}
