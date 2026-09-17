package com.jnd.ngdroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay

/**
 * AI Assistant provider + API key setup. Lives in the Settings tab:
 * pick a provider, paste its key — saved to DataStore automatically.
 * Model picking stays in the chat screen (live catalog, never hardcoded).
 */
@Composable
fun AiAssistantSettingsCard(
    assistant: AssistantSettingsFacade,
    renderPreview: SkillPreviewRenderer,
    modifier: Modifier = Modifier
) {
    val agentSettings by assistant.settings.collectAsState()
    val provider = agentSettings.provider
    val savedKey = when (provider) {
        AgentProvider.GEMINI -> agentSettings.geminiApiKey
        AgentProvider.OPENAI -> agentSettings.openaiApiKey
        AgentProvider.ANTHROPIC -> agentSettings.anthropicApiKey
        AgentProvider.OPENCODE_ZEN -> agentSettings.zenApiKey
        AgentProvider.OPENCODE_GO -> agentSettings.goApiKey
        AgentProvider.OPENROUTER -> agentSettings.openRouterApiKey
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
        assistant.updateKey(provider, keyInput)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgentProvider.entries.forEach { entry ->
                    FilterChip(
                        selected = provider == entry,
                        onClick = { assistant.updateProvider(entry) },
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
                    AgentProvider.OPENAI ->
                        "Get a key from platform.openai.com. The app calls " +
                            "api.openai.com/v1 directly — listing is free, chat needs credits."
                    AgentProvider.ANTHROPIC ->
                        "Get a key from console.anthropic.com. The app calls " +
                            "api.anthropic.com/v1 directly — listing is free, chat needs credits."
                    AgentProvider.OPENCODE_ZEN ->
                        "Get a key from opencode.ai (free). The app calls " +
                            "opencode.ai/zen/v1 directly — free models run on your quota."
                    AgentProvider.OPENCODE_GO ->
                        "Subscribe to Go at opencode.ai ($10/mo) and paste the key. " +
                            "The app calls opencode.ai/zen/go/v1 directly."
                    AgentProvider.OPENROUTER ->
                        "Get a key at openrouter.ai/keys. One key serves 400+ models — " +
                            ":free variants cost nothing."
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
                assistant.updateSearchKey(searchInput)
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
                    assistant.updateSessionId(session)
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

            HorizontalDivider()
            AgentSkillsSection(
                agentSettings = agentSettings,
                onToggle = { id, on -> assistant.updateSkill(id, on) },
                onReset = {
                    com.jnd.ngdroid.data.BUILTIN_SKILL_IDS.forEach {
                        assistant.updateSkill(it, true)
                    }
                }
            )

            HorizontalDivider()
            CustomSkillsSection(
                skills = agentSettings.customSkills,
                renderPreview = renderPreview,
                onAdd = { name, description, instructions ->
                    assistant.addCustomSkill(name, description, instructions) != null
                },
                onUpdate = { id, name, description, instructions ->
                    assistant.updateCustomSkill(id, name, description, instructions)
                },
                onDelete = { assistant.deleteCustomSkill(it) },
                onToggle = { id, on -> assistant.setCustomSkillEnabled(id, on) }
            )

            HorizontalDivider()
            AgentAdvancedSection(
                agentSettings = agentSettings,
                onMaxIter = { assistant.updateMaxIterations(it) },
                onStub = { assistant.updateStubRetries(it) },
                onAutoPick = { assistant.updateAutoPick(it) }
            )

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

@Composable
private fun AgentSkillsSection(
    agentSettings: com.jnd.ngdroid.data.AgentSettings,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit
) {
    val groups = com.jnd.ngdroid.data.BUILTIN_SKILLS.groupBy { it.group }
    val onCount = com.jnd.ngdroid.data.BUILTIN_SKILLS.count { agentSettings.isSkillEnabled(it.id) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Agent skills", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "$onCount of ${com.jnd.ngdroid.data.BUILTIN_SKILLS.size} on",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onReset) { Text("All on") }
        }
        Text(
            "Turn tools off to make the agent faster or offline-friendly. SPICE validation stays recommended.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        groups.forEach { (group, skills) ->
            Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            skills.forEach { skill ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(skill.title, style = MaterialTheme.typography.bodySmall)
                        Text(
                            skill.blurb,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = agentSettings.isSkillEnabled(skill.id),
                        onCheckedChange = { onToggle(skill.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomSkillsSection(
    skills: List<com.jnd.ngdroid.data.CustomSkill>,
    renderPreview: SkillPreviewRenderer,
    onAdd: (String, String, String) -> Boolean,
    onUpdate: (String, String, String, String) -> Boolean,
    onDelete: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.jnd.ngdroid.data.CustomSkill?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Custom skills", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (skills.isEmpty()) "none yet" else "${skills.count { it.enabled }} on",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("New")
            }
        }
        Text(
            "SKILL.md style: description picks when it loads, body loads on demand via read_skill.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (skills.isEmpty()) {
            Text(
                "No custom skills — tap New to create one.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        skills.forEach { skill ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(skill.name, style = MaterialTheme.typography.bodySmall)
                    Text(
                        skill.description.ifBlank { skill.instructions }.take(140),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                IconButton(onClick = { editing = skill; showDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${skill.name}")
                }
                IconButton(onClick = { onDelete(skill.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${skill.name}")
                }
                Switch(
                    checked = skill.enabled,
                    onCheckedChange = { onToggle(skill.id, it) }
                )
            }
        }
    }
    if (showDialog) {
        CustomSkillDialog(
            initial = editing,
            renderPreview = renderPreview,
            onDismiss = { showDialog = false },
            onSave = { name, description, instructions ->
                val ok = if (editing == null) onAdd(name, description, instructions)
                else onUpdate(editing!!.id, name, description, instructions)
                if (ok) showDialog = false
                ok
            }
        )
    }
}

@Composable
private fun CustomSkillDialog(
    initial: com.jnd.ngdroid.data.CustomSkill?,
    renderPreview: SkillPreviewRenderer,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Boolean
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description ?: "") }
    var instructions by remember(initial?.id) { mutableStateOf(initial?.instructions ?: "") }
    var preview by remember(initial?.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Same SKILL.md the agent loads (frontmatter + title + body), rendered with
    // the same chat markdown engine so What-You-See matches the assistant bubble.
    val previewMarkdown = remember(name, description, instructions) {
        com.jnd.ngdroid.data.previewSkillMarkdown(name, description, instructions)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New skill" else "Edit skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !preview,
                        onClick = { preview = false },
                        label = { Text("Write") }
                    )
                    FilterChip(
                        selected = preview,
                        onClick = { preview = true },
                        label = { Text("Preview") }
                    )
                }
                if (!preview) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Power ratings") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it; error = null },
                        label = { Text("Description (when to use)") },
                        placeholder = { Text("e.g. Use when explaining power or picking parts") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it; error = null },
                        label = { Text("Instructions (SKILL.md body)") },
                        placeholder = { Text("Steps the agent follows once the skill loads") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        renderPreview(previewMarkdown)
                    }
                }
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = com.jnd.ngdroid.data.validateCustomSkill(name, description, instructions)
                if (err != null) {
                    error = err
                    return@TextButton
                }
                if (!onSave(name.trim(), description.trim(), instructions.trim())) {
                    error = "Could not save — check limits and retry."
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AgentAdvancedSection(
    agentSettings: com.jnd.ngdroid.data.AgentSettings,
    onMaxIter: (Int) -> Unit,
    onStub: (Int) -> Unit,
    onAutoPick: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reasoning limits", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Higher limits solve harder tasks but cost more time and tokens.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Max steps (${agentSettings.maxIterations})")
            Slider(
                value = agentSettings.maxIterations.toFloat(),
                onValueChange = { onMaxIter(it.toInt()) },
                valueRange = 4f..20f,
                steps = 15,
                modifier = Modifier.fillMaxWidth(0.5f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stub retries (${agentSettings.stubRetries})")
            Slider(
                value = agentSettings.stubRetries.toFloat(),
                onValueChange = { onStub(it.toInt()) },
                valueRange = 0f..3f,
                steps = 2,
                modifier = Modifier.fillMaxWidth(0.5f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-pick free model", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Choose the first free model when none is selected.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = agentSettings.autoPickFreeModel, onCheckedChange = onAutoPick)
        }
        OutlinedButton(
            onClick = {
                onMaxIter(12)
                onStub(1)
                onAutoPick(true)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reset reasoning defaults") }
    }
}

private enum class SaveState { Idle, Editing, Saved }
