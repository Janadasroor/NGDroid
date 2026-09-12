package com.jnd.ngdroid.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class UndoRedoManager(
    private val maxHistorySize: Int = 50,
    private val debounceMillis: Long = 500L
) {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var lastSnapshotTime = 0L
    private var lastPushedText: String? = null

    fun onTextChanged(currentText: String, isDiscreteAction: Boolean = false) {
        if (currentText == lastPushedText) return

        val now = System.currentTimeMillis()
        val isDebounceElapsed = (now - lastSnapshotTime) > debounceMillis

        if (isDiscreteAction || isDebounceElapsed || lastPushedText == null) {
            lastPushedText?.let { prev ->
                if (undoStack.peek() != prev) {
                    undoStack.push(prev)
                    if (undoStack.size > maxHistorySize) {
                        undoStack.removeLast()
                    }
                }
            }
            redoStack.clear()
            lastSnapshotTime = now
        }

        lastPushedText = currentText
        updateStateFlows()
    }

    fun undo(currentText: String): String? {
        if (undoStack.isEmpty()) return null

        val previousText = undoStack.pop()
        redoStack.push(currentText)
        lastPushedText = previousText
        lastSnapshotTime = System.currentTimeMillis()

        updateStateFlows()
        return previousText
    }

    fun redo(currentText: String): String? {
        if (redoStack.isEmpty()) return null

        val nextText = redoStack.pop()
        undoStack.push(currentText)
        lastPushedText = nextText
        lastSnapshotTime = System.currentTimeMillis()

        updateStateFlows()
        return nextText
    }

    fun reset(initialText: String = "") {
        undoStack.clear()
        redoStack.clear()
        lastPushedText = initialText
        lastSnapshotTime = System.currentTimeMillis()
        updateStateFlows()
    }

    private fun updateStateFlows() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
