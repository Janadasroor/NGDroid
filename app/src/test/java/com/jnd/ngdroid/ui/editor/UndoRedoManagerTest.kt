package com.jnd.ngdroid.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoRedoManagerTest {

    private lateinit var manager: UndoRedoManager

    @Before
    fun setup() {
        manager = UndoRedoManager(maxHistorySize = 10, debounceMillis = 100L)
    }

    @Test
    fun initialState_cannotUndoOrRedo() {
        assertFalse(manager.canUndo.value)
        assertFalse(manager.canRedo.value)
    }

    @Test
    fun discreteAction_pushesUndoStateImmediately() {
        manager.onTextChanged("initial", isDiscreteAction = true)
        manager.onTextChanged("loaded preset", isDiscreteAction = true)

        assertTrue(manager.canUndo.value)
        assertFalse(manager.canRedo.value)

        val undone = manager.undo("loaded preset")
        assertEquals("initial", undone)
        assertTrue(manager.canRedo.value)

        val redone = manager.redo("initial")
        assertEquals("loaded preset", redone)
    }

    @Test
    fun undoAndRedo_cyclesCorrectly() {
        manager.onTextChanged("v1", isDiscreteAction = true)
        manager.onTextChanged("v2", isDiscreteAction = true)
        manager.onTextChanged("v3", isDiscreteAction = true)

        assertEquals("v2", manager.undo("v3"))
        assertEquals("v1", manager.undo("v2"))
        assertNull(manager.undo("v1"))

        assertEquals("v2", manager.redo("v1"))
        assertEquals("v3", manager.redo("v2"))
        assertNull(manager.redo("v3"))
    }

    @Test
    fun reset_clearsStacks() {
        manager.onTextChanged("v1", isDiscreteAction = true)
        manager.onTextChanged("v2", isDiscreteAction = true)
        assertTrue(manager.canUndo.value)

        manager.reset("new start")
        assertFalse(manager.canUndo.value)
        assertFalse(manager.canRedo.value)
    }
}
