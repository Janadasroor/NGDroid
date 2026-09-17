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

package com.jnd.ngdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFileCacheTest {

    @Test
    fun codec_roundTrip() {
        val items = listOf(
            RecentFile(uri = "content://a/b.cir", name = "b.cir", openedAtMillis = 1000L),
            RecentFile(uri = "content://a/c.cir", name = "c|c.cir", openedAtMillis = 2000L)
        )
        val decoded = RecentFileCodec.decode(RecentFileCodec.encode(items))
        assertEquals(items, decoded)
    }

    @Test
    fun codec_blank_returnsEmpty() {
        assertTrue(RecentFileCodec.decode("").isEmpty())
    }

    @Test
    fun repository_capsAtFive_mostRecentFirst() {
        val repo = NetlistRepository()
        val many = (1..8).map { i ->
            RecentFile(uri = "content://f/$i", name = "$i.cir", openedAtMillis = i.toLong())
        }
        repo.setRecentFiles(many)
        assertEquals(MAX_RECENT_FILES, repo.recentFiles.value.size)
        assertEquals(5, MAX_RECENT_FILES)
        assertEquals("content://f/8", repo.recentFiles.value.first().uri)
    }
}
