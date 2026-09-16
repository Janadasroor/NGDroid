package com.jnd.ngdroid.ui.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import java.io.File

/** MIME filter for the Documents attach option (images go through Photos). */
private val DOCUMENT_MIMES = arrayOf(
    "application/pdf",
    "text/*",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    assistantViewModel: AssistantViewModel,
    onApplyNetlist: (String) -> Unit,
    onApplyAndRun: (String) -> Unit,
    onSnapshot: () -> String = { "" },
    onRunAndReport: suspend (String?, Long) -> String = { _, _ -> "Simulation started" },
    onRenderPlot: suspend (List<String>?) -> com.jnd.ngdroid.agent.ToolResult =
        { _ -> com.jnd.ngdroid.agent.ToolResult("ERROR: plot render unavailable") },
    onCurrentNetlist: () -> String = { "" },
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
    val allCatalogs by assistantViewModel.allCatalogs.collectAsState()
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
    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remaining = MAX_ATTACHMENTS_PER_MESSAGE - pendingAttachments.size
        if (remaining <= 0) {
            Toast.makeText(context, "Max $MAX_ATTACHMENTS_PER_MESSAGE files per message", Toast.LENGTH_SHORT).show()
            return
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
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) addPickedUris(uris)
    }
    val pickDocs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) addPickedUris(uris)
    }
    var showAttachSheet by remember { mutableStateOf(false) }
    var cameraOutFile by remember { mutableStateOf<File?>(null) }
    var cameraOutUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val file = cameraOutFile
        val uri = cameraOutUri
        cameraOutFile = null
        cameraOutUri = null
        if (ok && uri != null) {
            // Already inside uploads: register the copy, drop the temp original.
            addPickedUris(listOf(uri))
            runCatching { file?.delete() }
        } else {
            runCatching { file?.delete() }
            if (!ok) Toast.makeText(context, "Photo discarded", Toast.LENGTH_SHORT).show()
        }
    }
    fun launchCamera() {
        if (pendingAttachments.size >= MAX_ATTACHMENTS_PER_MESSAGE) {
            Toast.makeText(context, "Max $MAX_ATTACHMENTS_PER_MESSAGE files per message", Toast.LENGTH_SHORT).show()
            return
        }
        val result = runCatching {
            val dir = File(appContext.filesDir, "uploads").apply { mkdirs() }
            val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                appContext, "${appContext.packageName}.fileprovider", file
            )
            cameraOutFile = file
            cameraOutUri = uri
            takePhoto.launch(uri)
        }
        if (result.isFailure) {
            cameraOutFile = null
            cameraOutUri = null
            Toast.makeText(context, "No camera app found", Toast.LENGTH_LONG).show()
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
            override fun currentNetlist(): String = try { onCurrentNetlist() } catch (_: Exception) { "" }
            override fun runSimulation() { onApplyAndRun("") }
            override fun snapshot(): String = onSnapshot()
            override suspend fun runAndReport(netlist: String?, timeoutMs: Long): String =
                onRunAndReport(netlist, timeoutMs)
            override suspend fun renderPlot(requested: List<String>?): com.jnd.ngdroid.agent.ToolResult =
                onRenderPlot(requested)
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
                            catalogs = allCatalogs,
                            modelsLoading = modelsLoading,
                            menuExpanded = modelMenuExpanded,
                            onMenuExpandedChange = {
                                modelMenuExpanded = it
                                if (it) assistantViewModel.refreshAllModels()
                            },
                            onSelectModelAcross = { provider, id ->
                                assistantViewModel.selectModelAcross(provider, id)
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
                            // Failed turns (missing key/model, quota, offline…)
                            // render in the error tone so they read as failures.
                            val msgIsError = remember(msg.text) { isChatError(msg.text) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (msgIsError) "Response failed" else "Response",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msgIsError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                onImageClick = { viewerUrl = it },
                                                error = msgIsError
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

        // ---- Follow-ups: one-tap next steps after a finished answer ----
        val lastVisible = visibleMessages.lastOrNull()
        if (messages.isNotEmpty() && !isThinking &&
            lastVisible?.role == ChatRoleUi.ASSISTANT && !isChatError(lastVisible.text)
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sizes.contentPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Simulate it" to "Simulate the current netlist and summarize the results",
                    "Explain results" to "Explain the last simulation results in plain language",
                    "Sweep values" to "Propose a component sweep to explore around the current design",
                    "Check convergence" to "Check the netlist for convergence risks and suggest fixes"
                ).forEach { (label, prompt) ->
                    AssistChip(
                        onClick = { send(prompt) },
                        label = { Text(label) },
                        shape = LocalButtonShape.current
                    )
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
                        onClick = { showAttachSheet = true },
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

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Attach",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                AttachOptionRow(
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    title = "Camera",
                    subtitle = "Take a photo",
                    onClick = {
                        showAttachSheet = false
                        launchCamera()
                    }
                )
                AttachOptionRow(
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    title = "Photos",
                    subtitle = "Choose images from the gallery",
                    onClick = {
                        showAttachSheet = false
                        pickPhotos.launch(arrayOf("image/*"))
                    }
                )
                AttachOptionRow(
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    title = "Documents",
                    subtitle = "PDFs, text and office files",
                    onClick = {
                        showAttachSheet = false
                        pickDocs.launch(DOCUMENT_MIMES)
                    }
                )
                Spacer(Modifier.height(28.dp))
            }
        }
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
private fun AttachOptionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
 * Professional model picker fed by ALL fetched provider catalogs.
 * Pill button (active provider icon + name + FREE badge) opens a menu with its own
 * search engine, ALL/FREE filter chips, and provider-grouped free-first rows —
 * every provider with a saved key (plus public Zen) in one list. Picking a row
 * from another provider switches to it. No hardcoded entries.
 */
@Composable
private fun ModelDropdownRow(
    settings: AgentSettings,
    models: List<String>,
    freeModels: List<String>,
    catalogs: List<ProviderCatalog>,
    modelsLoading: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onSelectModelAcross: (AgentProvider, String) -> Unit,
    onBrowseAll: () -> Unit
) {
    val selected = settings.selectedModel.trim()
    val hasSelection = selected.isNotEmpty()
    val isZen = settings.provider == AgentProvider.OPENCODE_ZEN
    val selectedFree = hasSelection && freeModels.any { it == selected }
    var menuQuery by remember(menuExpanded) { mutableStateOf("") }
    var menuTier by remember(menuExpanded) { mutableStateOf(ModelTierFilter.ALL) }
    val allEntries = remember(catalogs, menuQuery, menuTier) {
        searchAllCatalogs(catalogs, menuQuery, menuTier)
    }
    val menuVisible = remember(allEntries) { allEntries.take(30) }
    val totalModels = remember(catalogs) { catalogs.sumOf { it.models.size } }
    val totalFree = remember(catalogs) { catalogs.sumOf { it.freeModels.size } }
    val label = when {
        hasSelection -> selected
        modelsLoading -> "Loading models…"
        models.isEmpty() -> "Select model"
        else -> "Select model (${models.size})"
    }
    Box {
        Surface(
            onClick = { onMenuExpandedChange(!menuExpanded) },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isZen) Icons.Default.Cloud else Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append(settings.provider.displayName.uppercase())
                            append(" • MODEL")
                            if (freeModels.isNotEmpty()) append(" • ${freeModels.size} FREE")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = if (hasSelection) FontFamily.Monospace else null,
                            color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (selectedFree) {
                            Spacer(Modifier.width(6.dp))
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
                if (modelsLoading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Select model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) },
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            if (catalogs.isEmpty() && !modelsLoading) {
                DropdownMenuItem(
                    text = { Text("No models yet — add a key in Settings, then reopen") },
                    onClick = onBrowseAll
                )
            } else {
                OutlinedTextField(
                    value = menuQuery,
                    onValueChange = { menuQuery = it },
                    placeholder = { Text("Search $totalModels models…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (menuQuery.isNotEmpty()) {
                            IconButton(onClick = { menuQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    FilterChip(
                        selected = menuTier == ModelTierFilter.ALL,
                        onClick = { menuTier = ModelTierFilter.ALL },
                        label = { Text("All $totalModels") }
                    )
                    FilterChip(
                        selected = menuTier == ModelTierFilter.FREE,
                        onClick = { menuTier = ModelTierFilter.FREE },
                        label = { Text("Free $totalFree") },
                        leadingIcon = if (menuTier == ModelTierFilter.FREE) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                menuVisible.forEachIndexed { index, entry ->
                    if (index == 0 || menuVisible[index - 1].provider != entry.provider) {
                        val isCurrent = entry.provider == settings.provider
                        Text(
                            buildString {
                                append(entry.provider.displayName.uppercase())
                                if (isCurrent) append(" • CURRENT")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    val id = entry.id
                    val isSelected = selected == id && settings.provider == entry.provider
                    val isFree = entry.free
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            modelFamily(id).take(1).uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        id,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        buildString {
                                            append(modelFamily(id))
                                            append(" • ${entry.provider.displayName}")
                                            if (isFree) append(" • FREE")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isFree) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isFree) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = "Free model",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                        onClick = { onSelectModelAcross(entry.provider, id) }
                    )
                }
                if (menuVisible.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No match for \"$menuQuery\"") },
                        enabled = false,
                        onClick = {}
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = {
                        Text(
                            if (allEntries.size > menuVisible.size) "Browse all ${allEntries.size}…"
                            else "Browse all $totalModels…",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = onBrowseAll
                )
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
 * Professional models browser: live catalog only, with a real search engine
 * (multi-token + family match), ALL/FREE/KEYED tiers, free-first sections,
 * provider header card, and badge rows. No hardcoded models.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsDialog(
    assistantViewModel: AssistantViewModel,
    onDismiss: () -> Unit
) {
    LockOrientationWhileShown()
    val settings by assistantViewModel.settings.collectAsState()
    val catalogs by assistantViewModel.allCatalogs.collectAsState()
    val loading by assistantViewModel.modelsLoading.collectAsState()
    val error by assistantViewModel.modelsError.collectAsState()
    var query by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf(ModelTierFilter.ALL) }

    LaunchedEffect(Unit) { assistantViewModel.refreshAllModels() }

    val entries = remember(catalogs, query, tier) {
        searchAllCatalogs(catalogs, query, tier)
    }
    val totalModels = remember(catalogs) { catalogs.sumOf { it.models.size } }
    val totalFree = remember(catalogs) { catalogs.sumOf { it.freeModels.size } }
    // Provider-ordered groups (current provider first, as published).
    val groups = remember(catalogs, entries) {
        val byProvider = entries.groupBy { it.provider }
        catalogs.mapNotNull { c -> byProvider[c.provider]?.let { c.provider to it } }
    }
    val selectedId = settings.selectedModel.trim()
    val isZen = settings.provider == AgentProvider.OPENCODE_ZEN

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = LocalDialogShape.current,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isZen) Icons.Default.Cloud else Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Choose model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$totalModels models • $totalFree free • ${catalogs.size} providers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or family: spark, gpt, flash…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = tier == ModelTierFilter.ALL,
                        onClick = { tier = ModelTierFilter.ALL },
                        label = { Text("All $totalModels") }
                    )
                    FilterChip(
                        selected = tier == ModelTierFilter.FREE,
                        onClick = { tier = ModelTierFilter.FREE },
                        label = { Text("Free $totalFree") },
                        leadingIcon = {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                    FilterChip(
                        selected = tier == ModelTierFilter.KEYED,
                        onClick = { tier = ModelTierFilter.KEYED },
                        label = { Text("Keyed ${totalModels - totalFree}") }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { assistantViewModel.refreshAllModels() },
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
                    Text(
                        if (query.isBlank()) "${entries.size} shown across ${groups.size} providers"
                        else "${entries.size} match \"${query.trim().take(24)}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (error != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                error!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { assistantViewModel.refreshAllModels() }) { Text("Retry") }
                        }
                    }
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    groups.forEach { (provider, rows) ->
                        val isCurrent = provider == settings.provider
                        item(key = "hdr:${provider.name}") {
                            Text(
                                buildString {
                                    append(provider.displayName.uppercase())
                                    append(" • ${rows.size}")
                                    if (isCurrent) append(" • CURRENT")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                        items(rows, key = { "r:${provider.name}:${it.id}" }) { entry ->
                            ModelBrowserRow(
                                id = entry.id,
                                providerName = provider.displayName,
                                isFree = entry.free,
                                selected = selectedId == entry.id && settings.provider == provider,
                                onSelect = {
                                    assistantViewModel.selectModelAcross(provider, entry.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                    if (entries.isEmpty() && !loading) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (catalogs.isEmpty()) "No models yet — add provider keys in Settings, then Refresh."
                                    else "No models match \"$query\" — try fewer words.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

@Composable
private fun ModelBrowserRow(
    id: String,
    providerName: String,
    isFree: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    modelFamily(id).take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                id,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${modelFamily(id)} • $providerName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isFree) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = onSelect,
                        enabled = true,
                        label = {
                            Text("FREE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("netlist", text))
    } catch (_: Exception) { }
}
