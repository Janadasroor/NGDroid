package com.jnd.ngdroid.ui.console

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jnd.ngdroid.engine.SimulationRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysLogs() {
        val repo = SimulationRepository()

        composeTestRule.setContent {
            ConsoleScreen(repository = repo)
        }

        repo.runSimulation()

        composeTestRule.onNodeWithText("--- Running Simulation ---").assertExists()
    }

    @Test
    fun clearLogsButton_emptiesScreen() {
        val repo = SimulationRepository()

        composeTestRule.setContent {
            ConsoleScreen(repository = repo)
        }

        repo.runSimulation()

        // Logs exist initially
        composeTestRule.onNodeWithText("--- Running Simulation ---").assertExists()

        // Click clear
        composeTestRule.onNodeWithContentDescription("Clear Logs").performClick()

        // Should be empty message
        composeTestRule.onNodeWithText("No simulation logs yet. Run a simulation from the Netlist tab.").assertExists()
    }
}
