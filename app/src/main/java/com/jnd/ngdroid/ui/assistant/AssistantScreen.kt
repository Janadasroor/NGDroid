package com.jnd.ngdroid.ui.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.jnd.ngdroid.data.AgentProvider
import com.jnd.ngdroid.data.AgentSettings
import com.jnd.ngdroid.ui.theme.LocalAppSizes
import com.jnd.ngdroid.ui.theme.LocalButtonShape
import com.jnd.ngdroid.ui.theme.LocalDialogShape
import com.jnd.ngdroid.ui.util.LockOrientationWhileShown

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    assistantViewModel: AssistantViewModel,
    onApplyNetlist: (String) -> Unit,
    onApplyAndRun: (String) -> Unit,
    onNavigateToEditor: (() -> Unit)? = null,
    onNavigateToPlot: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val messages by assistantViewModel.messages.collectAsState()
    val isThinking by assistantViewModel.isThinking.collectAsState()
    val isOnline by assistantViewModel.isOnline.collectAsState()
    val statusLine by assistantViewModel.statusLine.collectAsState()
    val settings by assistantViewModel.settings.collectAsState()
    val models by assistantViewModel.models.collectAsState()
    val modelsLoading by assistantViewModel.modelsLoading.collectAsState()
    val freeModels by assistantViewModel.freeModels.collectAsState()
    val sessions by assistantViewModel.sessions.collectAsState()
    val activeChatId by assistantViewModel.activeChatId.collectAsState()
    val workingIds by assistantViewModel.workingIds.collectAsState()
    var input by remember { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var editingMsgId by remember { mutableStateOf<String?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var pendingBridge by remember { mutableStateOf<SimBridge?>(null) }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sizes = LocalAppSizes.current

    // Tool progress (SYSTEM) lives only inside the thinking
    // expander, never as separate rows in the message list.
    val visibleMessages = remember(messages) {
        messages.filter { it.role != ChatRoleUi.SYSTEM }
    }
    val toolSteps = remember(messages) { liveThinkingSteps(messages) }
    val pastSteps = remember(messages) { stepsByAssistant(messages) }

    LaunchedEffect(settings.provider) {
        // Fresh provider -> fetch its catalog so the dropdown is never stale.
        assistantViewModel.refreshModels()
    }

    LaunchedEffect(visibleMessages.size, isThinking) {
        if (visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.size - 1)
    }

    fun currentBridge(): SimBridge {
        pendingBridge?.let { return it }
        val bridge = object : SimBridge {
            override fun applyNetlist(text: String) = onApplyNetlist(text)
            override fun currentNetlist(): String = ""
            override fun runSimulation() { /* explicit run via buttons */ }
        }
        pendingBridge = bridge
        return bridge
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || isThinking) return
        // Offline (or otherwise unsent): keep the input so nothing is lost.
        if (assistantViewModel.sendMessage(clean, currentBridge())) input = ""
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistoryDrawer(
                sessions = sessions,
                activeChatId = activeChatId,
                providerName = settings.provider.displayName,
                workingIds = workingIds,
                onNewChat = {
                    assistantViewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onOpenChat = {
                    assistantViewModel.openChat(it)
                    scope.launch { drawerState.close() }
                },
                onDeleteChat = { deleteTargetId = it }
            )
        }
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Header: drawer + static provider/model label + new chat ----
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Saved chats")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // Model picker only: the provider lives next to each
                        // model inside the picker, not as a separate title.
                        ModelDropdownRow(
                            settings = settings,
                            models = models,
                            freeModels = freeModels,
                            modelsLoading = modelsLoading,
                            menuExpanded = modelMenuExpanded,
                            onMenuExpandedChange = {
                                modelMenuExpanded = it
                                if (it) assistantViewModel.refreshModels()
                            },
                            onSelectModel = {
                                assistantViewModel.selectModel(it)
                                modelMenuExpanded = false
                            },
                            onBrowseAll = {
                                modelMenuExpanded = false
                                showModels = true
                            }
                        )
                        if (!isOnline) {
                            Text(
                                "Offline — messages won't send",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = { assistantViewModel.newChat() },
                        enabled = messages.isNotEmpty() || sessions.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                }
            }
        }

        // ---- Messages ----
        if (messages.isEmpty() && !isThinking) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(sizes.contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(sizes.emptyIcon)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(sizes.emptyIconInner),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "What circuit shall we design?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "I generate validated SPICE netlists and can apply them to the editor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val suggestions = listOf(
                            "RC low-pass 1kHz" to "Design an RC low-pass with 1kHz cutoff",
                            "RLC resonator" to "Design an RLC resonator",
                            "Diode rectifier" to "Design a diode rectifier with smoothing",
                            "555 timer" to "Design a 555 timer astable oscillator"
                        )
                        suggestions.forEach { (label, prompt) ->
                            AssistChip(
                                onClick = { send(prompt) },
                                label = { Text(label) },
                                shape = LocalButtonShape.current,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = sizes.contentPadding, vertical = sizes.contentPadding),
                verticalArrangement = Arrangement.spacedBy(sizes.messageSpacing)
            ) {
                items(visibleMessages, key = { it.id }) { msg ->
                    when (msg.role) {
                        ChatRoleUi.USER -> {
                            val isEditing = editingMsgId == msg.id
                            if (isEditing) {
                                var draft by remember(msg.id, msg.text) {
                                    mutableStateOf(msg.text)
                                }
                                Surface(
                                    tonalElevation = 2.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        OutlinedTextField(
                                            value = draft,
                                            onValueChange = { draft = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 8,
                                            label = { Text("Edit prompt") }
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    if (assistantViewModel.editAndResend(
                                                        msg.id, draft, currentBridge()
                                                    )
                                                    ) editingMsgId = null
                                                },
                                                enabled = draft.trim().isNotEmpty(),
                                                shape = LocalButtonShape.current
                                            ) { Text("Send") }
                                            OutlinedButton(
                                                onClick = { editingMsgId = null },
                                                shape = LocalButtonShape.current
                                            ) {
                                                Text("Cancel")
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    if (!isThinking) {
                                        IconButton(
                                            onClick = { editingMsgId = msg.id },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit prompt",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                                        modifier = Modifier.fillMaxWidth(sizes.bubbleMaxFraction)
                                    ) {
                                        Text(
                                            msg.text,
                                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                        ChatRoleUi.SYSTEM -> { /* folded into ThinkingRow expander */ }
                        ChatRoleUi.ASSISTANT -> Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Response",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isThinking) {
                                    IconButton(
                                        onClick = {
                                            assistantViewModel.regenerate(currentBridge())
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Regenerate response",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            // Finished thought record: stays expandable after the run,
                            // so tool activity can be reviewed again later.
                            val doneSteps = pastSteps[msg.id].orEmpty()
                            if (doneSteps.isNotEmpty()) {
                                ThinkingRow(
                                    title = pastThinkingTitle(doneSteps),
                                    steps = doneSteps.map { it.text }
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            AssistantMarkdownWithMath(msg.text)
                            val blocks = remember(msg.text) { extractCodeBlocks(msg.text) }
                            blocks.forEach { block ->
                                Spacer(Modifier.height(10.dp))
                                val isNetlist = remember(block) { isNetlistBlock(block) }
                                CodeBlockCard(
                                    code = block,
                                    isNetlist = isNetlist,
                                    onCopy = {
                                        copyToClipboard(context, block)
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    },
                                    onApply = {
                                        onApplyNetlist(block)
                                        onNavigateToEditor?.invoke()
                                        Toast.makeText(context, "Applied to editor", Toast.LENGTH_SHORT).show()
                                    },
                                    onApplyAndRun = {
                                        onApplyAndRun(block)
                                        onNavigateToPlot?.invoke()
                                    }
                                )
                            }
                            // Finished response only: icons-only copy + share of the
                            // whole response text, tucked at the bottom of the bubble.
                            if (!isThinking) {
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, msg.text)
                                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy response",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, msg.text)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(share, "Share response")
                                            )
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share response",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (isThinking) {
                    item {
                        ThinkingRow(
                            title = thinkingTitle(statusLine, toolSteps.lastOrNull()?.text),
                            steps = toolSteps.map { it.text }
                        )
                    }
                }
            }
        }

        // ---- Input bar ----
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .imePadding(),
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = normalizeChatInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ask about circuits…") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(input) }),
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                AnimatedVisibility(
                    visible = isThinking,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FilledIconButton(
                        onClick = { assistantViewModel.stopGenerating() },
                        modifier = Modifier.size(sizes.inputButton)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                if (!isThinking) {
                    FilledIconButton(
                        onClick = { send(input) },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(sizes.inputButton)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    if (showModels) {
        ModelsDialog(
            assistantViewModel = assistantViewModel,
            onDismiss = { showModels = false }
        )
    }

    if (deleteTargetId != null) {
        LockOrientationWhileShown()
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            shape = LocalDialogShape.current,
            title = { Text("Delete chat?") },
            text = { Text("This removes the saved conversation permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTargetId?.let { assistantViewModel.deleteChat(it) }
                        deleteTargetId = null
                    },
                    shape = LocalButtonShape.current
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTargetId = null },
                    shape = LocalButtonShape.current
                ) { Text("Cancel") }
            }
        )
    }
    } // ModalNavigationDrawer
}

/**
 * Model dropdown fed ONLY by the live provider catalog ([models]).
 * No hardcoded entries: empty catalog -> "Select model" placeholder that opens the browser.
 */
@Composable
private fun ModelDropdownRow(
    settings: AgentSettings,
    models: List<String>,
    freeModels: List<String>,
    modelsLoading: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onBrowseAll: () -> Unit
) {
    val selected = settings.selectedModel.trim()
    val hasSelection = selected.isNotEmpty()
    val label = when {
        hasSelection -> selected
        modelsLoading -> "Loading models…"
        models.isEmpty() -> "Select model"
        else -> "Select model (${models.size})"
    }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onMenuExpandedChange(!menuExpanded) }
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (modelsLoading) {
                Spacer(Modifier.width(4.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = "Select model",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            if (models.isEmpty() && !modelsLoading) {
                DropdownMenuItem(
                    text = { Text("No models yet — browse all") },
                    onClick = onBrowseAll
                )
            } else {
                if (freeModels.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "FREE (${freeModels.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        enabled = false,
                        onClick = {}
                    )
                }
                models.take(14).forEach { id ->
                    val isSelected = selected == id
                    val isFree = freeModels.any { it == id }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    id,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    buildString {
                                        append(settings.provider.displayName)
                                        if (isFree) append(" • FREE")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFree) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = { onSelectModel(id) }
                    )
                }
                if (models.size > 14) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Browse all ${models.size}…") },
                        onClick = onBrowseAll
                    )
                }
            }
        }
    }
}

/**
 * Thinking row: no spinner — an expand button toggles the
 * working details (tool steps). Collapsed shows the dynamic title
 * ("Validating…", "Applying…") or the finished summary ("Validated • 4 steps").
 */
@Composable
private fun ThinkingRow(
    title: String,
    steps: List<String>,
    emptyHint: String = "Working through your request…"
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse thinking" else "Expand thinking",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 40.dp, end = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (steps.isEmpty()) {
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    steps.forEach { step ->
                        Text(
                            step,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    code: String,
    isNetlist: Boolean,
    onCopy: () -> Unit,
    onApply: () -> Unit,
    onApplyAndRun: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isNetlist) "SPICE netlist" else "Code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    code.lines().take(8).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier.padding(10.dp)
                )
            }
            if (code.lines().size > 8) {
                Text(
                    "… ${code.lines().size} lines total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            // Only validated netlists get Apply/Run: other code stays copy-only
            // so prose or non-SPICE snippets can't overwrite the editor.
            if (isNetlist) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onApply,
                        shape = LocalButtonShape.current
                    ) { Text("Apply") }
                    FilledTonalButton(
                        onClick = onApplyAndRun,
                        shape = LocalButtonShape.current
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Apply & Run")
                    }
                }
            }
        }
    }
}

/**
 * Models browser: auto-fetches the live catalog for the current provider when opened,
 * with search. Selecting a model saves it as the explicit selection. No hardcoded models.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsDialog(
    assistantViewModel: AssistantViewModel,
    onDismiss: () -> Unit
) {
    LockOrientationWhileShown()
    val settings by assistantViewModel.settings.collectAsState()
    val models by assistantViewModel.models.collectAsState()
    val freeModels by assistantViewModel.freeModels.collectAsState()
    val loading by assistantViewModel.modelsLoading.collectAsState()
    val error by assistantViewModel.modelsError.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { assistantViewModel.refreshModels() }

    val visible = remember(models, query) { filterModels(models, query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = LocalDialogShape.current,
        title = { Text("Choose model") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    buildString {
                        append(settings.provider.displayName)
                        if (freeModels.isNotEmpty()) append(" • ${freeModels.size} free")
                        append(" • tap to select")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search models") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { assistantViewModel.refreshModels() },
                        enabled = !loading,
                        shape = LocalButtonShape.current
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (loading) "Fetching…" else "Refresh")
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider()
                if (freeModels.isNotEmpty() && query.isBlank()) {
                    Text(
                        "FREE — no key needed to list, key needed to chat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visible, key = { it }) { id ->
                        val selected = settings.selectedModel.trim() == id
                        val isFree = freeModels.any { it == id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    assistantViewModel.selectModel(id)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    buildString {
                                        append(settings.provider.displayName)
                                        if (isFree) append(" • FREE")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFree) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (visible.isEmpty() && !loading) {
                        item {
                            Text(
                                if (models.isEmpty()) "No models yet — check connection, then Refresh."
                                else "No models match \"$query\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

internal fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("netlist", text))
    } catch (_: Exception) { }
}
