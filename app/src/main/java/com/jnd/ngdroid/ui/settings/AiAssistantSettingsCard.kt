package com.jnd.ngdroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jnd.ngdroid.data.AgentProvider
import com.jnd.ngdroid.ui.assistant.AssistantViewModel
import kotlinx.coroutines.delay

/**
 * AI Assistant provider + API key setup. Lives in the Settings tab:
 * pick a provider, paste its key — saved to DataStore automatically.
 * Model picking stays in the chat screen (live catalog, never hardcoded).
 */
@Composable
fun AiAssistantSettingsCard(
    assistantViewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val agentSettings by assistantViewModel.settings.collectAsState()
    val provider = agentSettings.provider
    val savedKey = when (provider) {
        AgentProvider.GEMINI -> agentSettings.geminiApiKey
        AgentProvider.OPENCODE_ZEN -> agentSettings.zenApiKey
    }

    var keyInput by remember(provider, savedKey) { mutableStateOf(savedKey) }
    var showKey by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf<SaveState>(SaveState.Idle) }

    // Debounced autosave: typing persists shortly after the user stops.
    LaunchedEffect(keyInput, provider) {
        val trimmed = keyInput.trim()
        if (trimmed == savedKey.trim()) {
            saveState = SaveState.Idle
            return@LaunchedEffect
        }
        saveState = SaveState.Editing
        delay(800)
        assistantViewModel.updateKey(provider, keyInput)
        saveState = SaveState.Saved
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("AI Assistant", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text("Provider", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgentProvider.entries.forEach { entry ->
                    FilterChip(
                        selected = provider == entry,
                        onClick = { assistantViewModel.updateProvider(entry) },
                        label = { Text(entry.displayName) }
                    )
                }
            }

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("${provider.displayName} API key") },
                placeholder = { Text("Paste key…") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key"
                        )
                    }
                },
                supportingText = {
                    when (saveState) {
                        SaveState.Idle -> if (savedKey.isNotBlank()) {
                            Text("Key saved (${savedKey.trim().length} chars)")
                        }
                        SaveState.Editing -> Text("Typing…")
                        SaveState.Saved -> Text("Saved ✓")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                when (provider) {
                    AgentProvider.GEMINI ->
                        "Get a key from Google AI Studio. The app calls " +
                            "generativelanguage.googleapis.com directly."
                    AgentProvider.OPENCODE_ZEN ->
                        "Get a key from opencode.ai. The app calls " +
                            "opencode.ai/zen/v1 directly."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            var searchInput by remember(agentSettings.searchApiKey) {
                mutableStateOf(agentSettings.searchApiKey)
            }
            var showSearchKey by remember { mutableStateOf(false) }
            var searchSaveState by remember { mutableStateOf<SaveState>(SaveState.Idle) }
            // Debounced autosave, same pattern as the provider key above.
            LaunchedEffect(searchInput) {
                val trimmed = searchInput.trim()
                if (trimmed == agentSettings.searchApiKey.trim()) {
                    searchSaveState = SaveState.Idle
                    return@LaunchedEffect
                }
                searchSaveState = SaveState.Editing
                delay(800)
                assistantViewModel.updateSearchKey(searchInput)
                searchSaveState = SaveState.Saved
            }
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                label = { Text("Web search key (optional)") },
                placeholder = { Text("Brave Search key…") },
                singleLine = true,
                visualTransformation = if (showSearchKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showSearchKey = !showSearchKey }) {
                        Icon(
                            if (showSearchKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showSearchKey) "Hide key" else "Show key"
                        )
                    }
                },
                supportingText = {
                    when (searchSaveState) {
                        SaveState.Idle -> if (agentSettings.searchApiKey.isNotBlank()) {
                            Text("Key saved — Brave backend active")
                        }
                        SaveState.Editing -> Text("Typing…")
                        SaveState.Saved -> Text("Saved ✓")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Optional Brave Search key (brave.com/search/api). With a key the " +
                    "assistant searches via Brave; without one it uses free DuckDuckGo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (provider == AgentProvider.OPENCODE_ZEN) {
                var session by remember(agentSettings.sessionId) {
                    mutableStateOf(agentSettings.sessionId)
                }
                var sessionDirty by remember { mutableStateOf(false) }
                LaunchedEffect(session) {
                    if (!sessionDirty) return@LaunchedEffect
                    delay(800)
                    assistantViewModel.updateSessionId(session)
                    sessionDirty = false
                }
                OutlinedTextField(
                    value = session,
                    onValueChange = { session = it; sessionDirty = true },
                    label = { Text("Session id (advanced)") },
                    supportingText = { Text("Sent as x-opencode-session; blank omits it") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (saveState == SaveState.Saved) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    when {
                        savedKey.isBlank() -> "No key saved yet — chat will ask for one."
                        saveState == SaveState.Saved -> "Key saved. Pick a model in the chat tab."
                        else -> "Pick a model in the chat tab to start."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class SaveState { Idle, Editing, Saved }
