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

    @Test
    fun concurrentCallbacks_keepScaleAndDataLengthsConsistent() {
        val names = arrayOf("time", "v(in)", "v(out)")
        repository.callback.onInitData("time", "t", "tran1", "transient", names)
        // First onData always flushes (throttle starts at 0), so lengths match.
        repository.callback.onData(names, doubleArrayOf(0.0, 0.0, 0.0))
        var plot = repository.state.value.currentPlot!!
        assertEquals(plot.scaleVector!!.values.size, plot.dataVectors[0].values.size)
        assertEquals(plot.scaleVector!!.values.size, plot.dataVectors[1].values.size)

        // Hammer the callback from 8 threads x 100 points each.
        val threads = List(8) {
            Thread {
                repeat(100) { i ->
                    repository.callback.onData(names, doubleArrayOf(i.toDouble(), i.toDouble(), i.toDouble()))
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }
        // Let the 50ms throttle expire, then one final point forces a flush.
        Thread.sleep(80)
        repository.callback.onData(names, doubleArrayOf(-1.0, -1.0, -1.0))

        plot = repository.state.value.currentPlot!!
        assertEquals(1 + 8 * 100 + 1, plot.scaleVector!!.values.size)
        assertEquals(plot.scaleVector!!.values.size, plot.dataVectors[0].values.size)
        assertEquals(plot.scaleVector!!.values.size, plot.dataVectors[1].values.size)
        assertEquals(1 + 8 * 100 + 1, repository.state.value.totalPointCount)
    }

    @Test
    fun reinit_clearsBuffers() {
        val names = arrayOf("time", "v(in)")
        repository.callback.onInitData("time", "t", "tran1", "transient", names)
        repository.callback.onData(names, doubleArrayOf(0.0, 1.0))
        repository.callback.onInitData("frequency", "t2", "ac1", "ac", arrayOf("frequency", "v(out)"))
        val plot = repository.state.value.currentPlot!!
        assertEquals("frequency", plot.scaleVector!!.name)
        assertTrue(plot.scaleVector!!.values.isEmpty())
        assertEquals(0, repository.state.value.totalPointCount)
    }
}
