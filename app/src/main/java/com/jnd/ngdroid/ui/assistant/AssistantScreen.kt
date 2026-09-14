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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.jnd.ngdroid.agent.ChatSegment
import com.jnd.ngdroid.agent.extractBareImageUrls
import com.jnd.ngdroid.agent.splitChatSegments
import com.jnd.ngdroid.agent.formatFileSize
import com.jnd.ngdroid.data.AgentProvider
import com.jnd.ngdroid.data.AndroidUploadStore
import com.jnd.ngdroid.data.AttachmentKind
import com.jnd.ngdroid.data.StoredAttachment
import com.jnd.ngdroid.ui.assistant.ChatFileCards
import com.jnd.ngdroid.ui.assistant.extractFileLinks
import com.jnd.ngdroid.ui.assistant.extractLocalFileNames
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
    var pendingAttachments by remember { mutableStateOf<List<StoredAttachment>>(emptyList()) }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sizes = LocalAppSizes.current
    val appContext = context.applicationContext
    val uploadStore = remember(appContext) { AndroidUploadStore(appContext) }

    // Storage picker -> app-private uploads copy so the agent can read the
    // files without holding SAF permissions. Capped at 4 per message, 15 MB each.
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val remaining = MAX_ATTACHMENTS_PER_MESSAGE - pendingAttachments.size
        if (remaining <= 0) {
            Toast.makeText(context, "Max $MAX_ATTACHMENTS_PER_MESSAGE files per message", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (uris.size > remaining) {
            Toast.makeText(context, "Only $remaining more file(s) allowed (max $MAX_ATTACHMENTS_PER_MESSAGE)", Toast.LENGTH_SHORT).show()
        }
        val added = mutableListOf<StoredAttachment>()
        var error: String? = null
        for (uri in uris.take(remaining)) {
            try {
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri, FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                added.add(uploadStore.saveFromUri(uri))
            } catch (e: Exception) {
                error = e.message?.take(140) ?: "Couldn't read that file"
                break
            }
        }
        if (added.isNotEmpty()) {
            pendingAttachments = (pendingAttachments + added).take(MAX_ATTACHMENTS_PER_MESSAGE)
        }
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

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
        if ((clean.isEmpty() && pendingAttachments.isEmpty()) || isThinking) return
        // Offline (or otherwise unsent): keep the input so nothing is lost.
        if (assistantViewModel.sendMessage(clean, currentBridge(), pendingAttachments)) {
            input = ""
            pendingAttachments = emptyList()
        }
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
                    pendingAttachments = emptyList()
                    scope.launch { drawerState.close() }
                },
                onOpenChat = {
                    assistantViewModel.openChat(it)
                    pendingAttachments = emptyList()
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
                        onClick = {
                            assistantViewModel.newChat()
                            pendingAttachments = emptyList()
                        },
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
                                    shape = LocalButtonShape.current,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (msg.attachments.isNotEmpty()) {
                                            AttachmentRefRow(msg.attachments)
                                            Spacer(Modifier.height(8.dp))
                                        }
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
                                                enabled = draft.trim().isNotEmpty() || msg.attachments.isNotEmpty(),
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
                                        Column(
                                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (msg.attachments.isNotEmpty()) {
                                                UserAttachmentList(
                                                    attachments = msg.attachments,
                                                    onOpen = { openUpload(context, it) }
                                                )
                                            }
                                            if (msg.text.isNotBlank()) {
                                                Text(
                                                    msg.text,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            } else if (msg.attachments.isEmpty()) {
                                                Text(
                                                    "(empty)",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                        }
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
                                    steps = doneSteps
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            var viewerUrl by remember(msg.text) { mutableStateOf<String?>(null) }
                            // Markdown images render as full-width cards in place
                            // (inline placeholders overlap surrounding text); tap
                            // opens the zoom/save/share viewer.
                            val segments = remember(msg.text) { splitChatSegments(msg.text) }
                            segments.forEach { seg ->
                                when (seg) {
                                    is ChatSegment.Text ->
                                        if (seg.text.isNotBlank()) {
                                            AssistantMarkdownWithMath(
                                                seg.text,
                                                onImageClick = { viewerUrl = it }
                                            )
                                        }
                                    is ChatSegment.Image -> {
                                        Spacer(Modifier.height(8.dp))
                                        ChatImageCard(
                                            url = seg.url,
                                            alt = seg.alt
                                        ) { viewerUrl = it }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                            // Bare image URLs (not markdown images): thumbnail
                            // strip; tap opens the zoom/save viewer.
                            val bareUrls = remember(msg.text) { extractBareImageUrls(msg.text) }
                            if (bareUrls.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                ChatImageStrip(urls = bareUrls) { viewerUrl = it }
                            }
                            viewerUrl?.let { fullUrl ->
                                ImageViewerDialog(url = fullUrl) { viewerUrl = null }
                            }
                            // Downloadable docs/archives + agent-saved local files.
                            val fileLinks = remember(msg.text) { extractFileLinks(msg.text) }
                            val localFiles = remember(msg.text) { extractLocalFileNames(msg.text) }
                            if (fileLinks.isNotEmpty() || localFiles.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                ChatFileCards(remote = fileLinks, local = localFiles)
                            }
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
                            title = liveThinkingTitle(statusLine, toolSteps),
                            steps = toolSteps,
                            isLive = true
                        )
                    }
                }
            }
        }

        // ---- Input bar: shaped prompt widget with image/doc uploads ----
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pendingAttachments.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pendingAttachments.forEach { a ->
                            PendingAttachmentChip(
                                attachment = a,
                                onRemove = {
                                    pendingAttachments = pendingAttachments.filterNot { it.id == a.id }
                                }
                            )
                        }
                    }
                    Text(
                        "${pendingAttachments.size}/$MAX_ATTACHMENTS_PER_MESSAGE attached • 15 MB max each • sent as text to the agent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(
                        onClick = { pickFiles.launch(arrayOf("*/*")) },
                        enabled = !isThinking && pendingAttachments.size < MAX_ATTACHMENTS_PER_MESSAGE,
                        modifier = Modifier.size(sizes.inputButton)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach image or document",
                            tint = if (!isThinking && pendingAttachments.size < MAX_ATTACHMENTS_PER_MESSAGE) {
                                MaterialTheme.colorScheme.primary
                            } else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = LocalButtonShape.current,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        TextField(
                            value = input,
                            onValueChange = { input = normalizeChatInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    if (pendingAttachments.isEmpty()) "Ask about circuits…"
                                    else "Ask about the attached files…"
                                )
                            },
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
                            enabled = input.isNotBlank() || pendingAttachments.isNotEmpty(),
                            modifier = Modifier.size(sizes.inputButton)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
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
 * Prompt-attachment UI: pending chips above the input, name refs in edit mode,
 * and tappable file rows inside user bubbles. Shapes follow
 * [LocalButtonShape] so ROUNDED/PILL/SQUARE applies to the prompt widget too.
 */

private fun attachmentIcon(kind: AttachmentKind) = when (kind) {
    AttachmentKind.IMAGE -> Icons.Default.Image
    AttachmentKind.DOC -> Icons.Default.Description
    AttachmentKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun openUpload(context: android.content.Context, a: StoredAttachment) {
    try {
        val file = java.io.File(a.localPath)
        if (a.localPath.isBlank() || !file.isFile) {
            Toast.makeText(context, "File no longer on this device — re-attach it", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val mime = context.contentResolver.getType(uri) ?: a.mimeType.ifBlank { "*/*" }
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(view, "Open ${a.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't open file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun PendingAttachmentChip(
    attachment: StoredAttachment,
    onRemove: () -> Unit
) {
    Surface(
        shape = LocalButtonShape.current,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                attachmentIcon(attachment.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    attachment.name.ifBlank { "file" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (attachment.sizeBytes > 0) {
                    Text(
                        formatFileSize(attachment.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove ${attachment.name}",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentRefRow(attachments: List<StoredAttachment>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            attachmentRefLine(attachments),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UserAttachmentList(
    attachments: List<StoredAttachment>,
    onOpen: (StoredAttachment) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        attachments.forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(a) }
            ) {
                Icon(
                    attachmentIcon(a.kind),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    a.name.ifBlank { "file" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (a.sizeBytes > 0) {
                    Text(
                        formatFileSize(a.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
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
 * Thinking row: tappable card with a live spinner (while running) or grouped
 * search summary (`Found 20 pages • Read 4 pages`) with tappable stacked site
 * icons. Tapping an icon opens that site's page dialog; expanding shows the
 * grouped summary plus the per-tool activity timeline.
 */
@Composable
private fun ThinkingRow(
    title: String,
    steps: List<ChatMsg>,
    isLive: Boolean = false,
    emptyHint: String = "Working through your request…"
) {
    var expanded by remember { mutableStateOf(false) }
    var hostDialog by remember(steps) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val summary = remember(steps) { parseSearchSummary(steps) }
    val headerHosts = remember(summary) {
        (summary.foundHosts + summary.foundSamples.map { it.host } +
            summary.readOk.map { it.host } + summary.imageHosts).distinct().take(8)
    }
    // Fully failed turns (no pages found/read, every step an error) render
    // the header in the error tone so failures read as failures collapsed.
    val headerError = remember(steps, summary) {
        steps.isNotEmpty() && !summary.hasSearch && steps.all { isErrorStep(it.text) }
    }
    fun openPage(url: String) = openUrl(context, url)

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                if (isLive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (headerError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (headerHosts.isNotEmpty()) {
                    StackedSiteIcons(
                        hosts = headerHosts,
                        iconSize = 24.dp,
                        onHostClick = { hostDialog = it }
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse thinking" else "Expand thinking",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (steps.isEmpty()) {
                        Text(
                            emptyHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (summary.hasSearch) {
                            SearchSummaryBlock(
                                summary,
                                onHostClick = { hostDialog = it },
                                onOpenPage = ::openPage
                            )
                            HorizontalDivider()
                        }
                        Text(
                            "Activity • ${steps.size} steps",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            steps.forEach { step ->
                                ToolStepRow(step.text)
                            }
                        }
                    }
                }
            }
        }
    }
    hostDialog?.let { host ->
        HostPagesDialog(
            host = host,
            pages = pagesForHost(summary, host),
            onDismiss = { hostDialog = null },
            onOpenPage = ::openPage
        )
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
