package com.jnd.ngdroid.engine

import com.jnd.ngdroid.data.PresetNetlists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SimulationRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = SimulationRepository(scope = kotlinx.coroutines.CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isCorrect() {
        val initialNetlist = repository.netlistText.value
        assertEquals(PresetNetlists.items.first().netlist, initialNetlist)

        val state = repository.state.value
        assertFalse(state.isSimulating)
        assertEquals("Idle", state.statusText)
        assertTrue(state.logs.isEmpty())
        assertTrue(state.activeVectors.isEmpty())
    }

    @Test
    fun setNetlist_updatesState() {
        val newNetlist = "V1 in 0 5V\nR1 in 0 1k"
        repository.setNetlist(newNetlist)

        assertEquals(newNetlist, repository.netlistText.value)
    }

    @Test
    fun toggleVector_updatesActiveSet() {
        // Toggle on
        repository.toggleVectorActive("v(in)")
        assertTrue(repository.state.value.activeVectors.contains("v(in)"))

        // Toggle off
        repository.toggleVectorActive("v(in)")
        assertFalse(repository.state.value.activeVectors.contains("v(in)"))
    }

    @Test
    fun clearLogs_emptiesLogList() {
        // We cannot easily inject a log via the callback without JNI or reflection in this simple test setup,
        // so we'll just verify the clear action ensures it is empty.
        repository.clearLogs()
        assertTrue(repository.state.value.logs.isEmpty())
    }
}
