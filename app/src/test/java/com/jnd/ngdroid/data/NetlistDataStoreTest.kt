package com.jnd.ngdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetlistDataStoreTest {

    @Test
    fun codec_roundTrip() {
        val items = listOf(
            SavedNetlist(id = "1", title = "RC", netlist = "R1 in out 1k", updatedAtMillis = 1000L),
            SavedNetlist(id = "2", title = "RLC", netlist = "L1 out 0 10m", updatedAtMillis = 2000L)
        )
        val json = NetlistJsonCodec.encode(items)
        val decoded = NetlistJsonCodec.decode(json)
        assertEquals(items, decoded)
    }

    @Test
    fun codec_blankAndCorrupt_returnEmpty() {
        assertTrue(NetlistJsonCodec.decode("").isEmpty())
        assertTrue(NetlistJsonCodec.decode("not-json{{{").isEmpty())
    }

    @Test
    fun repository_capsAtMax() {
        val repo = NetlistRepository()
        val many = (1..(MAX_SAVED_NETLISTS + 10)).map { i ->
            SavedNetlist(id = "$i", title = "C$i", netlist = "net $i", updatedAtMillis = i.toLong())
        }
        repo.setLibrary(many)
        assertEquals(MAX_SAVED_NETLISTS, repo.library.value.size)
        // Most recent first
        assertEquals("${MAX_SAVED_NETLISTS + 10}", repo.library.value.first().id)
    }

    @Test
    fun repository_autosaveDirtyFlow() {
        val repo = NetlistRepository()
        repo.setDraft("R1 in out 1k")
        assertEquals("R1 in out 1k", repo.draftText.value)
        repo.markDirty()
        assertEquals(AutosaveStatus.DIRTY, repo.autosave.value.status)
        repo.setAutosave(AutosaveUiState(AutosaveStatus.SAVED, 123L))
        assertEquals(AutosaveStatus.SAVED, repo.autosave.value.status)
        assertEquals(123L, repo.autosave.value.lastSavedAtMillis)
    }
}
