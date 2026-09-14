package com.jnd.ngdroid.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jnd.ngdroid.agent.AgentConfig
import com.jnd.ngdroid.agent.AgentErrors
import com.jnd.ngdroid.agent.AgentEvent
import com.jnd.ngdroid.agent.AgentOrchestrator
import com.jnd.ngdroid.agent.ApplyNetlistTool
import com.jnd.ngdroid.agent.ChatMessage
import com.jnd.ngdroid.agent.ChatRole
import com.jnd.ngdroid.agent.GeminiProvider
import com.jnd.ngdroid.agent.GenerateNetlistTemplateTool
import com.jnd.ngdroid.agent.CurlFetchTool
import com.jnd.ngdroid.agent.DownloadFileTool
import com.jnd.ngdroid.agent.FetchUrlTool
import com.jnd.ngdroid.agent.ImageSearchTool
import com.jnd.ngdroid.agent.HttpClients
import com.jnd.ngdroid.agent.AnthropicProvider
import com.jnd.ngdroid.agent.GoProvider
import com.jnd.ngdroid.agent.MapToolRegistry
import com.jnd.ngdroid.agent.OpenRouterProvider
import com.jnd.ngdroid.agent.OpenAiProvider
import com.jnd.ngdroid.agent.ReadFileTool
import com.jnd.ngdroid.agent.ReadSkillTool
import com.jnd.ngdroid.agent.RunSimulationTool
import com.jnd.ngdroid.data.AndroidAssistantFileStore
import com.jnd.ngdroid.agent.SpiceAppBridge
import com.jnd.ngdroid.agent.ValidateNetlistTool
import com.jnd.ngdroid.agent.WebSearchTool
import com.jnd.ngdroid.agent.ZenProvider
import com.jnd.ngdroid.data.AgentDataStore
import com.jnd.ngdroid.data.AgentProvider
import com.jnd.ngdroid.data.AgentSettings
import com.jnd.ngdroid.data.AndroidUploadStore
import com.jnd.ngdroid.data.ChatHistoryStore
import com.jnd.ngdroid.data.CustomSkill
import com.jnd.ngdroid.data.newCustomSkill
import com.jnd.ngdroid.data.StoredAttachment
import com.jnd.ngdroid.data.describeUploads
import com.jnd.ngdroid.data.loadVisionImages
import com.jnd.ngdroid.agent.LlmImage
import com.jnd.ngdroid.data.ChatSession
import com.jnd.ngdroid.data.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class ChatRoleUi { USER, ASSISTANT, SYSTEM }

data class ChatMsg(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRoleUi,
    val text: String,
    val attachments: List<StoredAttachment> = emptyList()
)

/** Bridge to the simulation screen: apply netlist text, read current, run sim. */
interface SimBridge {
    fun applyNetlist(text: String)
    fun currentNetlist(): String
    fun runSimulation()
    fun snapshot(): String = ""
    suspend fun runAndReport(netlist: String?, timeoutMs: Long = 30000): String {
        if (!netlist.isNullOrBlank()) {
            try { applyNetlist(netlist) } catch (e: Exception) { return "ERROR: ${e.message}" }
        }
        return try {
            runSimulation()
            snapshot().ifBlank { "Simulation started" }
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }
}

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AgentDataStore(application)

    private val _messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val messages: StateFlow<List<ChatMsg>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _statusLine = MutableStateFlow<String?>(null)
    val statusLine: StateFlow<String?> = _statusLine.asStateFlow()

    private val _settings = MutableStateFlow(AgentSettings())
    val settings: StateFlow<AgentSettings> = _settings.asStateFlow()

    /** Live catalog from the provider's listModels(). Never hardcoded in app code. */
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()

    /** Free-tier subset of [models] (Zen `-free` ids), ranked first. Empty for Gemini. */
    private val _freeModels = MutableStateFlow<List<String>>(emptyList())
    val freeModels: StateFlow<List<String>> = _freeModels.asStateFlow()

    /**
     * Live per-chat state. Each chat keeps its own transcript, agent history
     * and run job, so switching chats never cancels another chat's work —
     * every chat completes in the background.
     */
    private class ChatRuntime(
        var messages: List<ChatMsg> = emptyList(),
        val history: MutableList<ChatMessage> = mutableListOf(),
        var job: Job? = null,
        var thinking: Boolean = false,
        var status: String? = null
    )
    private val runtimes = mutableMapOf<String, ChatRuntime>()
    private fun runtime(id: String): ChatRuntime = runtimes.getOrPut(id) { ChatRuntime() }

    /** Ids with a live run job — the drawer shows a green dot for these. */
    private val _workingIds = MutableStateFlow<Set<String>>(emptySet())
    val workingIds: StateFlow<Set<String>> = _workingIds.asStateFlow()

    private fun refreshWorkingIds() {
        _workingIds.value = runtimes.filterValues { it.job?.isActive == true }.keys.toSet()
    }

    // ---- Chat history ----
    private val chatStore = ChatHistoryStore(application)
    private var cachedSessions: List<ChatSession> = emptyList()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val persistJobs = mutableMapOf<String, Job?>()
    /** Serializes storage writes: concurrent background chats must not clobber each other. */
    private val persistMutex = Mutex()

    /**
     * True once the persisted settings arrived. The auto-pick below must wait
     * for it: picking a default from blank (not-yet-loaded) settings would
     * overwrite — and permanently lose — the user's saved model.
     */
    private var settingsLoaded = false

    /**
     * True once the resumed chat's own model was applied after cold start.
     * The settings collector would otherwise overwrite a restore done before
     * the persisted settings arrive, so the restore waits for both streams.
     */
    private var modelRestoreDone = false

    /** Online state for send guards and the offline banner. */
    private val netMonitor = NetworkMonitor(application)
    val isOnline: StateFlow<Boolean> = netMonitor.isOnline

    companion object {
        const val OFFLINE_NOTICE =
            "You're offline. Reconnect, then send again — nothing was sent."
    }

    init {
        netMonitor.start()
        viewModelScope.launch {
            try {
                store.settingsFlow.collect {
                    _settings.value = it
                    settingsLoaded = true
                    restoreActiveChatModel()
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                chatStore.chatsFlow.collect {
                    cachedSessions = it
                    _sessions.value = it
                    restoreActiveChatModel()
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                val id = chatStore.activeIdFlow.first()
                // Cold-start resume must not touch the model: the saved global
                // selection wins. Only an explicit drawer tap restores a chat's model.
                if (id != null) openChat(id, persistCurrent = false, restoreModel = false)
            } catch (_: Exception) { }
        }
        // Zen's /models is public: preload the free catalog so the picker
        // works out of the box, before any API key is entered.
        if (_settings.value.provider == AgentProvider.OPENCODE_ZEN) {
            refreshModels()
        }
    }

    override fun onCleared() {
        netMonitor.stop()
        super.onCleared()
    }

    /** Appends the offline notice once (no spam on repeated taps). Returns false. */
    private fun notifyOffline(): Boolean {
        val id = ensureActiveId()
        val rt = runtime(id)
        if (rt.messages.lastOrNull()?.text != OFFLINE_NOTICE) {
            appendToChat(id, ChatMsg(role = ChatRoleUi.ASSISTANT, text = OFFLINE_NOTICE))
        }
        return false
    }

    /**
     * Every chat owns a stable id from its first send: the session row is
     * created eagerly so background jobs always have a key to report under.
     */
    private fun ensureActiveId(): String {
        _activeChatId.value?.let { return it }
        val now = System.currentTimeMillis()
        val s = _settings.value
        val created = ChatSession(
            title = "New chat",
            providerName = s.provider.displayName,
            model = s.selectedModel.trim(),
            createdAtMillis = now,
            updatedAtMillis = now,
            messages = emptyList()
        )
        cachedSessions = (listOf(created) + cachedSessions)
            .sortedByDescending { it.updatedAtMillis }
        _sessions.value = cachedSessions
        _activeChatId.value = created.id
        viewModelScope.launch {
            try {
                persistMutex.withLock {
                    chatStore.setActiveId(created.id)
                    chatStore.saveChats(cachedSessions)
                }
            } catch (_: Exception) { }
        }
        return created.id
    }

    /** Append to one chat; mirrors into the visible list when it is active. */
    private fun appendToChat(id: String, msg: ChatMsg) {
        val rt = runtime(id)
        rt.messages = rt.messages + msg
        if (id == _activeChatId.value) _messages.value = rt.messages
        schedulePersistFor(id)
    }

    /**
     * Creates or refreshes the live streaming bubble; returns its id.
     * New lists are built (never mutated) so background readers stay safe.
     */
    private fun setLiveBubbleText(id: String, liveId: String?, text: String): String {
        val rt = runtime(id)
        if (liveId == null) {
            val msg = ChatMsg(role = ChatRoleUi.ASSISTANT, text = text)
            rt.messages = rt.messages + msg
            if (id == _activeChatId.value) _messages.value = rt.messages
            schedulePersistFor(id)
            return msg.id
        }
        rt.messages = rt.messages.map {
            if (it.id == liveId && it.role == ChatRoleUi.ASSISTANT) it.copy(text = text) else it
        }
        return liveId
    }

    /** Mirrors a chat's transcript into the visible list when it is active. */
    private fun mirrorMessages(id: String) {
        if (id == _activeChatId.value) _messages.value = runtime(id).messages
    }

    private fun setChatThinking(id: String, thinking: Boolean) {
        val rt = runtime(id)
        rt.thinking = thinking
        if (!thinking) rt.status = null
        if (id == _activeChatId.value) {
            _isThinking.value = thinking
            if (!thinking) _statusLine.value = null
        }
        refreshWorkingIds()
    }

    private fun setChatStatus(id: String, status: String?) {
        val rt = runtime(id)
        rt.status = status
        if (id == _activeChatId.value) _statusLine.value = status
    }

    /** Persist the visible working set (delegates to the active chat). */
    private fun schedulePersist() {
        _activeChatId.value?.let { schedulePersistFor(it) }
    }

    /** Persist one chat's runtime transcript (debounced per chat). */
    private fun schedulePersistFor(id: String) {
        persistJobs[id]?.cancel()
        persistJobs[id] = viewModelScope.launch {
            delay(600)
            try { persistChatNow(id) } catch (_: Exception) { }
        }
    }

    private suspend fun persistChatNow(id: String) = persistMutex.withLock {
        val rt = runtimes[id]
        val current = rt?.messages ?: if (id == _activeChatId.value) _messages.value else return
        var list = cachedSessions.toMutableList()
        if (current.isEmpty()) {
            // Empty working set: just drop a stale active id, keep stored rows.
            if (_activeChatId.value == id && list.none { it.id == id }) {
                _activeChatId.value = null
                chatStore.setActiveId(null)
            }
            return
        }
        val now = System.currentTimeMillis()
        val s = _settings.value
        val idx = list.indexOfFirst { it.id == id }
        val firstUser = current.firstOrNull { it.role == ChatRoleUi.USER }?.text.orEmpty()
        // Retitle bare rows once the first prompt lands ("New chat" → prompt).
        val title = buildTitle(firstUser)
        val updated = if (idx >= 0) {
            list[idx].copy(
                title = title,
                updatedAtMillis = now,
                providerName = s.provider.displayName,
                model = s.selectedModel.trim(),
                messages = current.map { it.toStored() }
            )
        } else {
            ChatSession(
                id = id,
                title = title,
                providerName = s.provider.displayName,
                model = s.selectedModel.trim(),
                createdAtMillis = now,
                updatedAtMillis = now,
                messages = current.map { it.toStored() }
            )
        }
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        cachedSessions = list.sortedByDescending { it.updatedAtMillis }
        _sessions.value = cachedSessions
        chatStore.saveChats(cachedSessions)
    }

    private fun historyFrom(messages: List<ChatMsg>): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        for (m in messages) {
            when (m.role) {
                // Past attachments keep a name ref so follow-ups stay coherent
                // without re-reading files; the live turn injects full content.
                ChatRoleUi.USER -> {
                    val ref = attachmentRefLine(m.attachments)
                    val text = if (ref.isEmpty()) m.text else "${m.text}\n$ref"
                    out.add(ChatMessage(ChatRole.USER, text))
                }
                ChatRoleUi.ASSISTANT -> out.add(ChatMessage(ChatRole.ASSISTANT, m.text))
                ChatRoleUi.SYSTEM -> { /* tool progress lines: UI-only */ }
            }
        }
        return out
    }

    /**
     * Start a fresh chat. Other chats keep working in the background —
     * nothing is cancelled; their green dots stay on in the drawer.
     */
    fun newChat() {
        _activeChatId.value?.let { schedulePersistFor(it) }
        _messages.value = emptyList()
        _statusLine.value = null
        _isThinking.value = false
        _activeChatId.value = null
        viewModelScope.launch {
            try { chatStore.setActiveId(null) } catch (_: Exception) { }
        }
    }

    /**
     * Cold-start follow-up: once settings and sessions both arrive, put the
     * resumed chat's own model back (same logic as a drawer-tap open).
     * Idempotent — after applying, or when nothing differs, later calls no-op.
     */
    private fun restoreActiveChatModel() {
        if (modelRestoreDone || !settingsLoaded || cachedSessions.isEmpty()) return
        val id = _activeChatId.value ?: return
        val session = cachedSessions.firstOrNull { it.id == id } ?: return
        modelRestoreDone = true
        val savedModel = session.model.trim()
        if (savedModel.isEmpty()) return
        val matchedProvider =
            AgentProvider.entries.firstOrNull { it.displayName == session.providerName }
        if (matchedProvider != null && matchedProvider != _settings.value.provider) {
            updateProvider(matchedProvider)
        }
        if (savedModel != _settings.value.selectedModel) {
            selectModel(savedModel)
        }
    }

    /**
     * Resume a saved chat: loads its transcript and live run state.
     * Never blocks on — and never cancels — other chats' background work.
     * A chat with a running job streams its progress here once opened.
     */
    fun openChat(id: String, persistCurrent: Boolean = true, restoreModel: Boolean = true) {
        if (id == _activeChatId.value) return
        val cur = _activeChatId.value
        if (persistCurrent && cur != null) schedulePersistFor(cur)
        val session = cachedSessions.firstOrNull { it.id == id } ?: return
        val rt = runtime(id)
        if (rt.messages.isEmpty()) {
            // First open since process start: hydrate from storage.
            rt.messages = session.messages.map { it.toUi() }
            rt.history.clear()
            rt.history.addAll(historyFrom(rt.messages))
        }
        _messages.value = rt.messages
        _isThinking.value = rt.thinking
        _statusLine.value = rt.status
        _activeChatId.value = id
        // Resume with the chat's own model: follow-ups should run on the same
        // model the conversation started with, not whatever is selected now.
        // Skipped on cold-start resume so the saved global selection stands.
        val savedModel = session.model.trim()
        if (restoreModel && savedModel.isNotEmpty()) {
            val matchedProvider =
                AgentProvider.entries.firstOrNull { it.displayName == session.providerName }
            if (matchedProvider != null && matchedProvider != _settings.value.provider) {
                updateProvider(matchedProvider)
            }
            if (savedModel != _settings.value.selectedModel) {
                selectModel(savedModel)
            }
        }
        viewModelScope.launch {
            try { chatStore.setActiveId(id) } catch (_: Exception) { }
        }
    }

    /** Delete a whole chat; cancels its background job too. Other chats are untouched. */
    fun deleteChat(id: String) {
        viewModelScope.launch {
            try {
                runtimes[id]?.job?.cancel()
                runtimes.remove(id)
                persistJobs[id]?.cancel()
                persistJobs.remove(id)
                refreshWorkingIds()
                val list = cachedSessions.filterNot { it.id == id }
                cachedSessions = list
                _sessions.value = list
                persistMutex.withLock { chatStore.saveChats(list) }
                if (_activeChatId.value == id) {
                    _messages.value = emptyList()
                    _statusLine.value = null
                    _isThinking.value = false
                    _activeChatId.value = null
                    chatStore.setActiveId(null)
                }
            } catch (_: Exception) { }
        }
    }

    /** Switch provider; clears models + selection (catalogs are per-provider). */
    fun updateProvider(provider: AgentProvider) {
        if (_settings.value.provider == provider) return
        _settings.value = _settings.value.copy(provider = provider, selectedModel = "")
        _models.value = emptyList()
        _modelsError.value = null
        _freeModels.value = emptyList()
        viewModelScope.launch {
            try {
                store.setProvider(provider)
                store.setSelectedModel("")
            } catch (_: Exception) { }
        }
        // Zen needs no key for /models: fetch immediately so free models show.
        // Keyed providers fetch too when a key is already saved.
        if (provider == AgentProvider.OPENCODE_ZEN || _settings.value.activeApiKey().isNotEmpty()) refreshModels()
    }

    /** Save the API key for one provider (Settings screen). */
    fun updateKey(provider: AgentProvider, key: String) {
        _settings.value = when (provider) {
            AgentProvider.GEMINI -> _settings.value.copy(geminiApiKey = key)
            AgentProvider.OPENAI -> _settings.value.copy(openaiApiKey = key)
            AgentProvider.ANTHROPIC -> _settings.value.copy(anthropicApiKey = key)
            AgentProvider.OPENCODE_ZEN -> _settings.value.copy(zenApiKey = key)
            AgentProvider.OPENCODE_GO -> _settings.value.copy(goApiKey = key)
            AgentProvider.OPENROUTER -> _settings.value.copy(openRouterApiKey = key)
        }
        viewModelScope.launch {
            try { store.setKey(provider, key) } catch (_: Exception) { }
        }
    }

    /** Persist all provider keys at once. */
    fun updateKeys(
        geminiKey: String, openaiKey: String, anthropicKey: String,
        zenKey: String, goKey: String, openRouterKey: String
    ) {
        _settings.value = _settings.value.copy(
            geminiApiKey = geminiKey, openaiApiKey = openaiKey,
            anthropicApiKey = anthropicKey, zenApiKey = zenKey,
            goApiKey = goKey, openRouterApiKey = openRouterKey
        )
        viewModelScope.launch {
            try {
                store.setKeys(geminiKey, openaiKey, anthropicKey, zenKey, goKey, openRouterKey)
            } catch (_: Exception) { }
        }
    }

    /** Save the optional web-search (Brave) key. Blank = free DuckDuckGo backend. */
    fun updateSearchKey(key: String) {
        _settings.value = _settings.value.copy(searchApiKey = key)
        viewModelScope.launch {
            try { store.setSearchKey(key) } catch (_: Exception) { }
        }
    }

    fun updateSessionId(sessionId: String) {
        _settings.value = _settings.value.copy(sessionId = sessionId)
        viewModelScope.launch {
            try { store.setSessionId(sessionId) } catch (_: Exception) { }
        }
    }

    /** Built-in skill toggle (maps to one agent tool). */
    fun updateSkill(id: String, enabled: Boolean) {
        _settings.value = _settings.value.withSkill(id, enabled)
        // withSkill returns same instance for unknown ids; still persist known ones.
        viewModelScope.launch {
            try { store.setSkill(id, enabled) } catch (_: Exception) { }
        }
    }

    fun updateMaxIterations(v: Int) {
        val coerced = v.coerceIn(4, 20)
        _settings.value = _settings.value.copy(maxIterations = coerced)
        viewModelScope.launch {
            try { store.setMaxIterations(coerced) } catch (_: Exception) { }
        }
    }

    fun updateStubRetries(v: Int) {
        val coerced = v.coerceIn(0, 3)
        _settings.value = _settings.value.copy(stubRetries = coerced)
        viewModelScope.launch {
            try { store.setStubRetries(coerced) } catch (_: Exception) { }
        }
    }

    fun updateAutoPick(v: Boolean) {
        _settings.value = _settings.value.copy(autoPickFreeModel = v)
        viewModelScope.launch {
            try { store.setAutoPick(v) } catch (_: Exception) { }
        }
    }

    /** Create a user skill; returns null when validation fails (caller shows the error). */
    fun addCustomSkill(name: String, description: String, instructions: String): String? {
        if (com.jnd.ngdroid.data.validateCustomSkill(name, description, instructions) != null) return null
        if (_settings.value.customSkills.size >= 50) return null
        val skill = newCustomSkill(name, description, instructions)
        _settings.value = _settings.value.copy(customSkills = _settings.value.customSkills + skill)
        viewModelScope.launch {
            try { store.setCustomSkills(_settings.value.customSkills) } catch (_: Exception) { }
        }
        return skill.id
    }

    /** Back-compat: description derived from instructions head. */
    fun addCustomSkill(name: String, instructions: String): String? =
        addCustomSkill(name, instructions.trim().take(200), instructions)

    fun updateCustomSkill(id: String, name: String, description: String, instructions: String): Boolean {
        if (com.jnd.ngdroid.data.validateCustomSkill(name, description, instructions) != null) return false
        val list = _settings.value.customSkills.map {
            if (it.id == id) it.copy(
                name = name.trim().take(40),
                description = description.trim().take(500),
                instructions = instructions.trim().take(4000)
            ) else it
        }
        _settings.value = _settings.value.copy(customSkills = list)
        viewModelScope.launch {
            try { store.setCustomSkills(list) } catch (_: Exception) { }
        }
        return true
    }

    fun deleteCustomSkill(id: String) {
        val list = _settings.value.customSkills.filterNot { it.id == id }
        _settings.value = _settings.value.copy(customSkills = list)
        viewModelScope.launch {
            try { store.setCustomSkills(list) } catch (_: Exception) { }
        }
    }

    fun setCustomSkillEnabled(id: String, enabled: Boolean) {
        val list = _settings.value.customSkills.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        _settings.value = _settings.value.copy(customSkills = list)
        viewModelScope.launch {
            try { store.setCustomSkills(list) } catch (_: Exception) { }
        }
    }

    /** User-picked model id from the live catalog. Blank clears the selection. */
    fun selectModel(id: String) {
        val clean = id.trim()
        _settings.value = _settings.value.copy(selectedModel = clean)
        viewModelScope.launch {
            try { store.setSelectedModel(clean) } catch (_: Exception) { }
        }
    }

    /** Stop the visible chat's run. Background chats keep working. */
    fun stopGenerating() {
        val id = _activeChatId.value ?: return
        val rt = runtimes[id] ?: return
        rt.job?.cancel()
        rt.job = null
        setChatThinking(id, false)
    }

    private fun buildProvider(s: AgentSettings, key: String, model: String) = when (s.provider) {
        AgentProvider.GEMINI -> GeminiProvider(
            HttpClients.okHttpPost(), apiKey = key, model = model,
            streamHttp = HttpClients.okHttpStream()
        )
        AgentProvider.OPENAI -> OpenAiProvider(
            HttpClients.okHttpPost(), apiKey = key, model = model,
            streamHttp = HttpClients.okHttpStream()
        )
        AgentProvider.ANTHROPIC -> AnthropicProvider(
            HttpClients.okHttpPost(), apiKey = key, model = model,
            streamHttp = HttpClients.okHttpStream()
        )
        AgentProvider.OPENCODE_GO -> GoProvider(
            HttpClients.okHttpPost(),
            apiKey = key,
            model = model,
            sessionId = s.sessionId.ifBlank { ZenProvider.DEFAULT_SESSION_ID },
            streamHttp = HttpClients.okHttpStream()
        )
        AgentProvider.OPENROUTER -> OpenRouterProvider(
            HttpClients.okHttpPost(), apiKey = key, model = model,
            streamHttp = HttpClients.okHttpStream()
        )
        AgentProvider.OPENCODE_ZEN -> ZenProvider(
            HttpClients.okHttpPost(),
            apiKey = key,
            model = model,
            sessionId = s.sessionId.ifBlank { ZenProvider.DEFAULT_SESSION_ID },
            streamHttp = HttpClients.okHttpStream()
        )
    }

    /**
     * Fetch the model catalog for the current provider (cloud-direct).
     * Zen's /models is public: fetched without a key, free (`-free`) ids ranked
     * first, and the first free model auto-selected when nothing is chosen.
     * Gemini still needs its key. No hardcoded fallbacks anywhere.
     */
    fun refreshModels() {
        if (_modelsLoading.value) return
        if (!netMonitor.isOnline.value) {
            _modelsError.value = "You're offline — reconnect to fetch models."
            return
        }
        val s = _settings.value
        val key = s.activeApiKey()
        val needsKey = s.provider != AgentProvider.OPENCODE_ZEN
        if (needsKey && key.isEmpty()) {
            _modelsError.value =
                "Add your ${s.provider.displayName} API key in Settings first."
            _models.value = emptyList()
            _freeModels.value = emptyList()
            return
        }
        viewModelScope.launch {
            _modelsLoading.value = true
            _modelsError.value = null
            try {
                val probe = buildProvider(s, key, model = "")
                val fetched = probe.listModels(key)
                // Free = live suffix (`-free` on Zen, `:free` variants on OpenRouter).
                val free = when (s.provider) {
                    AgentProvider.OPENCODE_ZEN ->
                        fetched.filter { it.trim().lowercase().endsWith("-free") }
                    AgentProvider.OPENROUTER ->
                        fetched.filter { it.trim().lowercase().endsWith(":free") }
                    else -> emptyList()
                }
                _freeModels.value = rankModels(free)
                _models.value = rankModelsFreeFirst(fetched, free.toSet())
                // Re-read: [s] may predate the settings restore that finished
                // while the network call was in flight.
                val current = _settings.value
                if (fetched.isEmpty()) {
                    _modelsError.value = "Provider returned no models."
                } else if (settingsLoaded) {
                    // Auto-pick: first working free model (Zen) when the user chose nothing yet.
                    if (current.selectedModel.isBlank() && free.isNotEmpty()) {
                        if (current.autoPickFreeModel) {
                            ZenProvider.autoDefault(free)?.let { selectModel(it) }
                        }
                    } else if (current.selectedModel.isNotBlank() && current.selectedModel !in fetched) {
                        // Stored selection vanished from the catalog — clear it to re-pick.
                        selectModel("")
                        _modelsError.value = "Saved model is no longer offered — pick a new one."
                    }
                }
            } catch (e: Exception) {
                _modelsError.value = (e.message ?: "Fetch failed").take(220)
                _models.value = emptyList()
                _freeModels.value = emptyList()
            } finally {
                _modelsLoading.value = false
            }
        }
    }

    /**
     * Sends a chat turn. Returns true when the turn started (caller clears
     * its input); false when nothing was sent (blank with no attachments,
     * this chat busy, or offline — offline also posts a one-time notice,
     * input is kept). Other chats' background runs are never affected.
     */
    fun sendMessage(
        text: String,
        simBridge: SimBridge,
        attachments: List<StoredAttachment> = emptyList()
    ): Boolean {
        val clean = text.trim()
        val files = attachments.take(MAX_ATTACHMENTS_PER_MESSAGE)
        if (clean.isEmpty() && files.isEmpty()) return false
        if (!netMonitor.isOnline.value) return notifyOffline()
        val id = ensureActiveId()
        if (runtime(id).thinking) return false
        val s = _settings.value
        val key = s.activeApiKey()
        appendToChat(id, ChatMsg(role = ChatRoleUi.USER, text = clean, attachments = files))
        val model = s.selectedModel.trim()
        if (model.isEmpty()) {
            appendToChat(
                id,
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "No model selected yet. Open the model menu above and pick one " +
                        "from the live ${s.provider.displayName} catalog."
                )
            )
            return true
        }
        // Free Zen models work without any key (Bearer public + session header);
        // anything else needs the provider key from Settings.
        if (key.isEmpty() && zenNeedsKey(model)) {
            appendToChat(
                id,
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "This model needs your ${s.provider.displayName} API key. " +
                        "Open Settings → AI Assistant and paste your key — or pick a FREE model."
                )
            )
            return true
        }
        runAgentTurn(id, clean, s, key, model, simBridge, files)
        return true
    }

    /**
     * Edit a user prompt end-to-end: truncate everything after it, replace its
     * text, then regenerate from that point. Drops this chat's in-flight run;
     * other chats are untouched. The prompt's attachments are kept.
     * Returns false when nothing was sent (offline keeps the draft open).
     */
    fun editAndResend(msgId: String, newText: String, simBridge: SimBridge): Boolean {
        val clean = newText.trim()
        val id = _activeChatId.value ?: return false
        val rt = runtime(id)
        if (rt.thinking) return false
        if (!netMonitor.isOnline.value) return notifyOffline()
        val idx = rt.messages.indexOfFirst { it.id == msgId && it.role == ChatRoleUi.USER }
        if (idx < 0) return false
        // File-only prompts may have empty text; only block empty text with no files.
        val kept = rt.messages[idx].attachments
        if (clean.isEmpty() && kept.isEmpty()) return false
        rt.job?.cancel()
        rt.job = null
        val truncated = truncateAfter(rt.messages, msgId).toMutableList()
        truncated[idx] = truncated[idx].copy(text = clean)
        val files = truncated[idx].attachments
        rt.messages = truncated
        _messages.value = truncated
        rt.history.clear()
        rt.history.addAll(historyFrom(truncated))
        // Drop the trailing USER turn from agent history; runAgentTurn re-adds it.
        if (rt.history.isNotEmpty() && rt.history.last().role == ChatRole.USER) {
            rt.history.removeAt(rt.history.size - 1)
        }
        schedulePersistFor(id)
        val s = _settings.value
        val model = s.selectedModel.trim()
        if (model.isEmpty()) {
            appendToChat(
                id,
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "No model selected yet. Open the model menu above and pick one."
                )
            )
            return true
        }
        val key = s.activeApiKey()
        if (key.isEmpty() && zenNeedsKey(model)) {
            appendToChat(
                id,
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "This model needs your ${s.provider.displayName} API key. " +
                        "Open Settings → AI Assistant and paste your key — or pick a FREE model."
                )
            )
            return true
        }
        runAgentTurn(id, clean, s, key, model, simBridge, files)
        return true
    }

    /**
     * True when the model needs the provider key: everything except free
     * Zen (`-free`) ids — the keyed clouds and the Go subscription always
     * need their key.
     */
    private fun zenNeedsKey(model: String): Boolean {
        if (_settings.value.provider != AgentProvider.OPENCODE_ZEN) return true
        return !model.trim().lowercase().endsWith("-free")
    }

    /** Regenerate the last answer: resend the most recent user prompt unchanged. */
    fun regenerate(simBridge: SimBridge) {
        val id = _activeChatId.value ?: return
        if (runtime(id).thinking) return
        val lastUser = _messages.value.lastOrNull { it.role == ChatRoleUi.USER } ?: return
        editAndResend(lastUser.id, lastUser.text, simBridge)
    }

    private fun runAgentTurn(
        id: String,
        clean: String,
        s: AgentSettings,
        key: String,
        model: String,
        simBridge: SimBridge,
        attachments: List<StoredAttachment> = emptyList()
    ) {
        val rt = runtime(id)
        rt.job?.cancel()
        rt.job = viewModelScope.launch {
            setChatThinking(id, true)
            setChatStatus(id, "Contacting ${s.provider.displayName}…")
            // Attached images/docs are read on-device and appended to the
            // prompt as text; raster images ALSO ride as vision payloads.
            val files = attachments.take(MAX_ATTACHMENTS_PER_MESSAGE)
            if (files.isNotEmpty()) {
                setChatStatus(id, "Reading ${files.size} attached file${if (files.size == 1) "" else "s"}…")
            }
            val enriched = try {
                if (files.isEmpty()) clean
                else buildAgentUserText(
                    clean.ifBlank { "(no typed prompt — see the attached files)" },
                    describeUploads(AndroidUploadStore(getApplication()), files)
                )
            } catch (_: Exception) {
                clean
            }
            val userImages: List<LlmImage> = try {
                if (files.isEmpty()) emptyList()
                else loadVisionImages(AndroidUploadStore(getApplication()), files)
            } catch (_: Exception) {
                emptyList()
            }
            // Live streaming bubble id (declared outside try so the catch
            // path can finalize the same bubble instead of doubling it).
            var liveId: String? = null
            try {
                val provider = buildProvider(s, key, model)
                val bridge = object : SpiceAppBridge {
                    override fun applyNetlist(text: String): String {
                        simBridge.applyNetlist(text)
                        return "Applied ${text.lines().size} lines to editor"
                    }
                    override fun currentNetlist(): String = try {
                        simBridge.currentNetlist()
                    } catch (e: Exception) { "ERROR: ${e.message}" }
                    override fun runSimulation(): String = try {
                        simBridge.runSimulation()
                        "Simulation started"
                    } catch (e: Exception) { "ERROR: ${e.message}" }
                }
                val fileStore = AndroidAssistantFileStore(getApplication())
                val customSnapshot = s.enabledCustomSkills()
                val registry = MapToolRegistry().apply {
                    if (s.isSkillEnabled("validate_netlist")) register(ValidateNetlistTool())
                    if (s.isSkillEnabled("netlist_template")) register(GenerateNetlistTemplateTool())
                    if (s.isSkillEnabled("apply_netlist")) register(ApplyNetlistTool(bridge))
                    if (s.isSkillEnabled("run_simulation")) register(
                        RunSimulationTool { netlist, timeoutMs -> simBridge.runAndReport(netlist, timeoutMs) }
                    )
                    if (s.isSkillEnabled("web_search")) register(WebSearchTool(searchKeyProvider = { s.searchApiKey }))
                    if (s.isSkillEnabled("image_search")) register(ImageSearchTool(searchKeyProvider = { s.searchApiKey }))
                    if (s.isSkillEnabled("fetch_url")) register(FetchUrlTool())
                    if (s.isSkillEnabled("curl_fetch")) register(CurlFetchTool())
                    if (s.isSkillEnabled("download_file")) register(DownloadFileTool(HttpClients.okHttpBytes(), fileStore))
                    if (s.isSkillEnabled("read_file")) register(ReadFileTool(fileStore))
                    if (customSnapshot.isNotEmpty()) register(ReadSkillTool { customSnapshot })
                }
                val agent = AgentOrchestrator(
                    AgentConfig(
                        maxIterations = s.coercedMaxIterations(),
                        maxStubRetries = s.coercedStubRetries()
                    ),
                    provider,
                    registry
                )
                val systemPrompt = com.jnd.ngdroid.data.skillCatalogPrompt(
                    com.jnd.ngdroid.agent.SPICE_SYSTEM,
                    customSnapshot
                )
                // Raw provider failures become short friendly sentences (no JSON/URLs).
                // Pending tool args let observations embed their source URL/counts
                // (fetch reads, search totals) for the thinking summary.
                val pendingArgs = ArrayDeque<Pair<String, String>>()
                // Live streaming bubble: created on the first text delta, then
                // refreshed (UI mirrored at ~8Hz so markdown keeps up).
                var lastPushMs = 0L
                val answer = agent.run(
                    enriched,
                    rt.history.toList(),
                    { event ->
                    when (event) {
                        is AgentEvent.Partial -> {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (liveId == null) {
                                liveId = setLiveBubbleText(id, null, event.text)
                                lastPushMs = now
                            } else {
                                setLiveBubbleText(id, liveId, event.text)
                                if (now - lastPushMs >= 120) {
                                    mirrorMessages(id)
                                    lastPushMs = now
                                }
                            }
                        }
                        is AgentEvent.ToolCallEvent -> {
                            pendingArgs.add(event.name to event.argsJson)
                            val label = toolCallLabel(event.name, event.argsJson)
                            appendToChat(id, ChatMsg(role = ChatRoleUi.SYSTEM, text = label))
                            setChatStatus(id, label)
                        }
                        is AgentEvent.Observation -> {
                            val idx = pendingArgs.indexOfFirst { it.first == event.toolName }
                            val args = if (idx >= 0) pendingArgs.removeAt(idx).second else ""
                            val line = summarizeObservation(event.toolName, event.output, args)
                            appendToChat(id, ChatMsg(role = ChatRoleUi.SYSTEM, text = line))
                            setChatStatus(id, null)
                        }
                        is AgentEvent.Error -> {
                            setChatStatus(id, AgentErrors.format(event.message, model).take(140))
                        }
                        is AgentEvent.Message -> { /* final text handled via return value */ }
                    }
                    },
                    errorFormatter = { AgentErrors.format(it, model) },
                    systemPrompt = systemPrompt,
                    userImages = userImages
                )
                rt.history.add(ChatMessage(ChatRole.USER, enriched))
                rt.history.add(ChatMessage(ChatRole.ASSISTANT, answer))
                // The live bubble (if any) becomes the final answer so the
                // turn never renders twice; otherwise append as before.
                val finalText = answer.ifBlank { "(empty response)" }
                if (liveId != null) {
                    setLiveBubbleText(id, liveId, finalText)
                    mirrorMessages(id)
                } else {
                    appendToChat(id, ChatMsg(role = ChatRoleUi.ASSISTANT, text = finalText))
                }
            } catch (e: Exception) {
                val friendly = AgentErrors.format(e.message, model)
                if (liveId != null) {
                    setLiveBubbleText(id, liveId, friendly)
                    mirrorMessages(id)
                } else {
                    appendToChat(id, ChatMsg(role = ChatRoleUi.ASSISTANT, text = friendly))
                }
            } finally {
                rt.job = null
                setChatThinking(id, false)
                // Flush this chat to storage NOW (debounce may never fire for a
                // background chat), then drop its in-memory transcript when it
                // is not visible — storage holds the truth for reopening.
                try { persistChatNow(id) } catch (_: Exception) { }
                if (id != _activeChatId.value) {
                    runtimes.remove(id)
                    refreshWorkingIds()
                }
            }
        }
        refreshWorkingIds()
    }
}
