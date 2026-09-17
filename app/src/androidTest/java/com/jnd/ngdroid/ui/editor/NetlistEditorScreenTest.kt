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

package com.jnd.ngdroid.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jnd.ngdroid.data.NetlistDataStore
import com.jnd.ngdroid.data.PresetNetlists
import com.jnd.ngdroid.data.SettingsRepository
import com.jnd.ngdroid.ui.SimulationViewModel
import kotlinx.coroutines.runBlocking
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
        // Clear any persisted draft so the editor falls back to the default
        // preset (PresetNetlists.items.first()).
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking { NetlistDataStore(context).saveDraft("") }
        val vm = makeViewModel()
        val settingsRepo = SettingsRepository()
        composeTestRule.setContent {
            NetlistEditorScreen(viewModel = vm, settingsRepository = settingsRepo, onNavigateToPlot = {})
        }

        val firstLine = PresetNetlists.items.first().netlist.lines().first()
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

        composeTestRule.onNodeWithText("Examples").performClick()

        val preset = PresetNetlists.items[2]
        composeTestRule.onNodeWithText(preset.title).performClick()

        val firstLine = preset.netlist.lines().first()
        composeTestRule.onNodeWithText(firstLine, substring = true).assertExists()
    }
}
