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
