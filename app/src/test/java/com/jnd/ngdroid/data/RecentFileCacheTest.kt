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
