package com.jnd.ngdroid.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelUtilsTest {

    @Test
    fun familyLabels() {
        assertEquals("Spark", modelFamily("muse-spark-1.3-contributor-free"))
        assertEquals("GPT", modelFamily("gpt-4o-mini"))
        assertEquals("Gemini", modelFamily("gemini-2.5-flash-lite"))
        assertEquals("Claude", modelFamily("claude-sonnet-4-5"))
        assertEquals("Kimi", modelFamily("kimi-k3"))
        assertEquals("GLM", modelFamily("glm-5.3-flash"))
        assertEquals("Qwen", modelFamily("qwen3.8-max"))
        assertEquals("MiniMax", modelFamily("minimax-m2.5"))
        assertEquals("Llama", modelFamily("meta-llama/llama-3.3-70b-instruct:free"))
        assertEquals("Claude", modelFamily("anthropic/claude-sonnet-4:free"))
        assertEquals("Nemotron", modelFamily("nemotron-3-ultra-free"))
    }

    @Test
    fun searchRanksFreeFirstAndMatchesFamily() {
        val models = listOf("big-pickle", "muse-spark-1.3-contributor-free", "gemini-2.5-flash-lite")
        val free = setOf("muse-spark-1.3-contributor-free")
        val res = searchModelCatalog(models, "spark", ModelTierFilter.ALL, free)
        assertEquals(listOf("muse-spark-1.3-contributor-free"), res)
        val all = searchModelCatalog(models, "", ModelTierFilter.ALL, free)
        assertEquals("muse-spark-1.3-contributor-free", all.first())
    }

    @Test
    fun tierFilters() {
        val models = listOf("a-free", "b-pro")
        val free = setOf("a-free")
        assertEquals(listOf("a-free"), searchModelCatalog(models, "", ModelTierFilter.FREE, free))
        assertEquals(listOf("b-pro"), searchModelCatalog(models, "", ModelTierFilter.KEYED, free))
    }

    @Test
    fun multiTokenAnd() {
        val models = listOf("gemini-2.5-flash-lite", "gemini-2.5-pro", "gpt-4o")
        val res = searchModelCatalog(models, "gemini flash", ModelTierFilter.ALL, emptySet())
        assertTrue(res.contains("gemini-2.5-flash-lite"))
        assertEquals(1, res.size)
    }
}
