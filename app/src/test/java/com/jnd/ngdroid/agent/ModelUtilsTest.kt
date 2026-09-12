package com.jnd.ngdroid.agent

import com.jnd.ngdroid.ui.assistant.filterModels
import com.jnd.ngdroid.ui.assistant.rankModels
import com.jnd.ngdroid.ui.assistant.rankModelsFreeFirst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelUtilsTest {

    @Test
    fun sortsAlphabeticallyCaseInsensitive() {
        val ranked = rankModels(listOf("zeta", "Alpha", "mimo"))
        assertEquals(listOf("Alpha", "mimo", "zeta"), ranked)
    }

    @Test
    fun dedupesAndTrims() {
        val ranked = rankModels(listOf(" b ", "a", "b", ""))
        assertEquals(listOf("a", "b"), ranked)
    }

    @Test
    fun emptyInEmptyOut() {
        assertTrue(rankModels(emptyList()).isEmpty())
    }

    @Test
    fun filterIsCaseInsensitive() {
        val models = listOf("model-b", "Model-A", "other")
        assertEquals(listOf("Model-A"), filterModels(models, "model-a"))
        assertEquals(models, filterModels(models, ""))
        assertEquals(2, filterModels(models, "model").size)
        assertTrue(filterModels(models, "zzz").isEmpty())
    }

    @Test
    fun freeFirstThenRestAlphabetical() {
        val models = listOf("zeta", "mimo-v2.5-free", "alpha", "big-pickle")
        val ranked = rankModelsFreeFirst(models, setOf("mimo-v2.5-free"))
        assertEquals(listOf("mimo-v2.5-free", "alpha", "big-pickle", "zeta"), ranked)
    }

    @Test
    fun freeFirstEmptySetIsPlainSort() {
        assertEquals(
            listOf("a", "b"),
            rankModelsFreeFirst(listOf("b", "a", "b", " "), emptySet())
        )
    }
}
