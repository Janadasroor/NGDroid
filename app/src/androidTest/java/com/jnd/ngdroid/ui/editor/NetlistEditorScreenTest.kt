package com.jnd.ngdroid.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jnd.ngdroid.data.PresetNetlists
import com.jnd.ngdroid.data.SettingsRepository
import com.jnd.ngdroid.ui.SimulationViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetlistEditorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeViewModel(): SimulationViewModel {
        return SimulationViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle()
        )
    }

    @Test
    fun displaysDefaultNetlist() {
        val vm = makeViewModel()
        val settingsRepo = SettingsRepository()
        composeTestRule.setContent {
            NetlistEditorScreen(viewModel = vm, settingsRepository = settingsRepo, onNavigateToPlot = {})
        }

        val firstLine = "* RC Low-Pass Filter Transient Analysis"
        composeTestRule.onNodeWithText(firstLine, substring = true).assertExists()
    }

    @Test
    fun runButton_triggersCallback() {
        val vm = makeViewModel()
        val settingsRepo = SettingsRepository()
        var navigated = false

        composeTestRule.setContent {
            NetlistEditorScreen(viewModel = vm, settingsRepository = settingsRepo, onNavigateToPlot = { navigated = true })
        }

        composeTestRule.onNodeWithText("Run", substring = true, useUnmergedTree = true).performClick()

        assert(navigated)
    }

    @Test
    fun presetMenu_loadsNewCircuit() {
        val vm = makeViewModel()
        val settingsRepo = SettingsRepository()
        composeTestRule.setContent {
            NetlistEditorScreen(viewModel = vm, settingsRepository = settingsRepo, onNavigateToPlot = {})
        }

        composeTestRule.onNodeWithText("Presets").performClick()

        val rlcTitle = PresetNetlists.items[2].title
        composeTestRule.onNodeWithText(rlcTitle).performClick()

        val nodes = composeTestRule.onAllNodesWithText("RLC Parallel Resonant Circuit", substring = true).fetchSemanticsNodes()
        assert(nodes.isNotEmpty())
    }
}
