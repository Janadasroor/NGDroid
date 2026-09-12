package com.jnd.ngdroid.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jnd.ngdroid.agent.AgentConfig
import com.jnd.ngdroid.agent.AgentEvent
import com.jnd.ngdroid.agent.AgentOrchestrator
import com.jnd.ngdroid.agent.ApplyNetlistTool
import com.jnd.ngdroid.agent.ChatMessage
import com.jnd.ngdroid.agent.ChatRole
import com.jnd.ngdroid.agent.GeminiProvider
import com.jnd.ngdroid.agent.GenerateNetlistTemplateTool
import com.jnd.ngdroid.agent.HttpClients
import com.jnd.ngdroid.agent.MapToolRegistry
import com.jnd.ngdroid.agent.SpiceAppBridge
import com.jnd.ngdroid.agent.ValidateNetlistTool
import com.jnd.ngdroid.agent.ZenProvider
import com.jnd.ngdroid.data.AgentDataStore
import com.jnd.ngdroid.data.AgentProvider
import com.jnd.ngdroid.data.AgentSettings
import com.jnd.ngdroid.data.ChatHistoryStore
import com.jnd.ngdroid.data.ChatSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatRoleUi { USER, ASSISTANT, SYSTEM }

data class ChatMsg(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRoleUi,
    val text: String
)

/** Bridge to the simulation screen: apply netlist text, read current, run sim. */
interface SimBridge {
    fun applyNetlist(text: String)
    fun currentNetlist(): String
    fun runSimulation()
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

    private var runJob: Job? = null
    private val history = mutableListOf<ChatMessage>()

    // ---- Chat history ----
    private val chatStore = ChatHistoryStore(application)
    private var cachedSessions: List<ChatSession> = emptyList()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                store.settingsFlow.collect { _settings.value = it }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                chatStore.chatsFlow.collect {
                    cachedSessions = it
                    _sessions.value = it
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                val id = chatStore.activeIdFlow.first()
                if (id != null) openChat(id, persistCurrent = false)
            } catch (_: Exception) { }
        }
        // Zen's /models is public: preload the free catalog so the picker
        // works out of the box, before any API key is entered.
        if (_settings.value.provider == AgentProvider.OPENCODE_ZEN) {
            refreshModels()
        }
    }

    /** Persist the working set into the session list (debounced). */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(600)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        try {
            val current = _messages.value
            val activeId = _activeChatId.value
            var list = cachedSessions.toMutableList()
            if (current.isEmpty()) {
                // Empty working set: just drop a stale active id, keep stored rows.
                if (activeId != null && list.none { it.id == activeId }) {
                    _activeChatId.value = null
                    chatStore.setActiveId(null)
                }
                return
            }
            val now = System.currentTimeMillis()
            val s = _settings.value
            if (activeId == null) {
                val firstUser = current.firstOrNull { it.role == ChatRoleUi.USER }?.text.orEmpty()
                val created = ChatSession(
                    title = buildTitle(firstUser),
                    providerName = s.provider.displayName,
                    model = s.selectedModel.trim(),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    messages = current.map { it.toStored() }
                )
                list.add(0, created)
                _activeChatId.value = created.id
                chatStore.setActiveId(created.id)
            } else {
                val idx = list.indexOfFirst { it.id == activeId }
                val updated = if (idx >= 0) {
                    list[idx].copy(
                        updatedAtMillis = now,
                        providerName = s.provider.displayName,
                        model = s.selectedModel.trim(),
                        messages = current.map { it.toStored() }
                    )
                } else {
                    val firstUser = current.firstOrNull { it.role == ChatRoleUi.USER }?.text.orEmpty()
                    ChatSession(
                        id = activeId,
                        title = buildTitle(firstUser),
                        providerName = s.provider.displayName,
                        model = s.selectedModel.trim(),
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        messages = current.map { it.toStored() }
                    )
                }
                if (idx >= 0) list[idx] = updated else list.add(0, updated)
            }
            cachedSessions = list.sortedByDescending { it.updatedAtMillis }
            _sessions.value = cachedSessions
            chatStore.saveChats(cachedSessions)
        } catch (_: Exception) { }
    }

    private fun rebuildHistoryFromMessages() {
        history.clear()
        for (m in _messages.value) {
            when (m.role) {
                ChatRoleUi.USER -> history.add(ChatMessage(ChatRole.USER, m.text))
                ChatRoleUi.ASSISTANT -> history.add(ChatMessage(ChatRole.ASSISTANT, m.text))
                ChatRoleUi.SYSTEM -> { /* tool progress lines: UI-only */ }
            }
        }
    }

    /** Start a fresh chat; persists the current one first when non-empty. */
    fun newChat() {
        if (_messages.value.isNotEmpty()) {
            viewModelScope.launch { persistNow() }
        }
        runJob?.cancel()
        runJob = null
        persistJob?.cancel()
        history.clear()
        _messages.value = emptyList()
        _statusLine.value = null
        _isThinking.value = false
        _activeChatId.value = null
        viewModelScope.launch {
            try { chatStore.setActiveId(null) } catch (_: Exception) { }
        }
    }

    /** Resume a saved chat: loads its transcript and rebuilds agent history. */
    fun openChat(id: String, persistCurrent: Boolean = true) {
        if (_isThinking.value) return
        if (persistCurrent && _messages.value.isNotEmpty() && _activeChatId.value != id) {
            viewModelScope.launch { persistNow() }
        }
        val session = cachedSessions.firstOrNull { it.id == id } ?: return
        runJob?.cancel()
        runJob = null
        persistJob?.cancel()
        _messages.value = session.messages.map { it.toUi() }
        rebuildHistoryFromMessages()
        _statusLine.value = null
        _isThinking.value = false
        _activeChatId.value = id
        viewModelScope.launch {
            try { chatStore.setActiveId(id) } catch (_: Exception) { }
        }
    }

    /** Delete a whole chat; clears the working set when it was active. */
    fun deleteChat(id: String) {
        viewModelScope.launch {
            try {
                val list = cachedSessions.filterNot { it.id == id }
                cachedSessions = list
                _sessions.value = list
                chatStore.saveChats(list)
                if (_activeChatId.value == id) {
                    runJob?.cancel()
                    runJob = null
                    persistJob?.cancel()
                    history.clear()
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
        if (provider == AgentProvider.OPENCODE_ZEN) refreshModels()
    }

    /** Save the API key for one provider (Settings screen). */
    fun updateKey(provider: AgentProvider, key: String) {
        _settings.value = when (provider) {
            AgentProvider.GEMINI -> _settings.value.copy(geminiApiKey = key)
            AgentProvider.OPENCODE_ZEN -> _settings.value.copy(zenApiKey = key)
        }
        viewModelScope.launch {
            try { store.setKey(provider, key) } catch (_: Exception) { }
        }
    }

    /** Persist both keys at once. */
    fun updateKeys(geminiKey: String, zenKey: String) {
        _settings.value = _settings.value.copy(geminiApiKey = geminiKey, zenApiKey = zenKey)
        viewModelScope.launch {
            try { store.setKeys(geminiKey, zenKey) } catch (_: Exception) { }
        }
    }

    fun updateSessionId(sessionId: String) {
        _settings.value = _settings.value.copy(sessionId = sessionId)
        viewModelScope.launch {
            try { store.setSessionId(sessionId) } catch (_: Exception) { }
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

    fun stopGenerating() {
        runJob?.cancel()
        runJob = null
        _isThinking.value = false
        _statusLine.value = null
    }

    private fun buildProvider(s: AgentSettings, key: String, model: String) = when (s.provider) {
        AgentProvider.GEMINI -> GeminiProvider(HttpClients.okHttpPost(), apiKey = key, model = model)
        AgentProvider.OPENCODE_ZEN -> ZenProvider(
            HttpClients.okHttpPost(),
            apiKey = key,
            model = model,
            sessionId = s.sessionId.ifBlank { ZenProvider.DEFAULT_SESSION_ID }
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
                // Free = live `-free` suffix (spark included: it works via /responses).
                val free = if (s.provider == AgentProvider.OPENCODE_ZEN) {
                    fetched.filter { it.trim().lowercase().endsWith("-free") }
                } else emptyList()
                _freeModels.value = rankModels(free)
                _models.value = rankModelsFreeFirst(fetched, free.toSet())
                if (fetched.isEmpty()) {
                    _modelsError.value = "Provider returned no models."
                } else {
                    // Auto-pick: first free model (Zen) when the user chose nothing yet.
                    if (s.selectedModel.isBlank() && free.isNotEmpty()) {
                        selectModel(rankModels(free).first())
                    } else if (s.selectedModel.isNotBlank() && s.selectedModel !in fetched) {
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

    fun sendMessage(text: String, simBridge: SimBridge) {
        val clean = text.trim()
        if (clean.isEmpty() || _isThinking.value) return
        val s = _settings.value
        val key = s.activeApiKey()
        append(ChatMsg(role = ChatRoleUi.USER, text = clean))
        val model = s.selectedModel.trim()
        if (model.isEmpty()) {
            append(
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "No model selected yet. Open the model menu above and pick one " +
                        "from the live ${s.provider.displayName} catalog."
                )
            )
            return
        }
        // Free Zen models work without any key (Bearer public + session header);
        // anything else needs the provider key from Settings.
        if (key.isEmpty() && zenNeedsKey(model)) {
            append(
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "This model needs your ${s.provider.displayName} API key. " +
                        "Open Settings → AI Assistant and paste your key — or pick a FREE model."
                )
            )
            return
        }
        runAgentTurn(clean, s, key, model, simBridge)
    }

    /**
     * Edit a user prompt end-to-end: truncate everything after it, replace its
     * text, then regenerate from that point. Drops any in-flight run.
     */
    fun editAndResend(msgId: String, newText: String, simBridge: SimBridge) {
        val clean = newText.trim()
        if (clean.isEmpty() || _isThinking.value) return
        val current = _messages.value
        val idx = current.indexOfFirst { it.id == msgId && it.role == ChatRoleUi.USER }
        if (idx < 0) return
        runJob?.cancel()
        runJob = null
        val truncated = truncateAfter(current, msgId).toMutableList()
        truncated[idx] = truncated[idx].copy(text = clean)
        _messages.value = truncated
        rebuildHistoryFromMessages()
        // Drop the trailing USER turn from agent history; runAgentTurn re-adds it.
        if (history.isNotEmpty() && history.last().role == ChatRole.USER) {
            history.removeAt(history.size - 1)
        }
        schedulePersist()
        val s = _settings.value
        val model = s.selectedModel.trim()
        if (model.isEmpty()) {
            append(
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "No model selected yet. Open the model menu above and pick one."
                )
            )
            schedulePersist()
            return
        }
        val key = s.activeApiKey()
        if (key.isEmpty() && zenNeedsKey(model)) {
            append(
                ChatMsg(
                    role = ChatRoleUi.ASSISTANT,
                    text = "This model needs your ${s.provider.displayName} API key. " +
                        "Open Settings → AI Assistant and paste your key — or pick a FREE model."
                )
            )
            schedulePersist()
            return
        }
        runAgentTurn(clean, s, key, model, simBridge)
    }

    /**
     * True when a Zen model id requires the user's own key: anything that is
     * not a free-tier (`-free`) id. Gemini always needs its key.
     */
    private fun zenNeedsKey(model: String): Boolean {
        if (_settings.value.provider != AgentProvider.OPENCODE_ZEN) return true
        return !model.trim().lowercase().endsWith("-free")
    }

    /** Regenerate the last answer: resend the most recent user prompt unchanged. */
    fun regenerate(simBridge: SimBridge) {
        if (_isThinking.value) return
        val lastUser = _messages.value.lastOrNull { it.role == ChatRoleUi.USER } ?: return
        editAndResend(lastUser.id, lastUser.text, simBridge)
    }

    private fun runAgentTurn(
        clean: String,
        s: AgentSettings,
        key: String,
        model: String,
        simBridge: SimBridge
    ) {
        runJob?.cancel()
        runJob = viewModelScope.launch {
            _isThinking.value = true
            _statusLine.value = "Contacting ${s.provider.displayName}…"
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
                val registry = MapToolRegistry().apply {
                    register(ValidateNetlistTool())
                    register(GenerateNetlistTemplateTool())
                    register(ApplyNetlistTool(bridge))
                }
                val agent = AgentOrchestrator(AgentConfig(maxIterations = 8), provider, registry)
                val answer = agent.run(clean, history.toList()) { event ->
                    when (event) {
                        is AgentEvent.ToolCallEvent -> {
                            append(ChatMsg(role = ChatRoleUi.SYSTEM, text = "Calling ${event.name}…"))
                            _statusLine.value = "Calling ${event.name}…"
                        }
                        is AgentEvent.Observation -> {
                            val snippet = if (event.output == "VALID") "VALID"
                            else "Error: ${event.output.take(220)}"
                            append(ChatMsg(role = ChatRoleUi.SYSTEM, text = "${event.toolName}: $snippet"))
                            _statusLine.value = null
                        }
                        is AgentEvent.Error -> {
                            _statusLine.value = event.message.take(160)
                        }
                        is AgentEvent.Message -> { /* final text handled via return value */ }
                    }
                }
                history.add(ChatMessage(ChatRole.USER, clean))
                history.add(ChatMessage(ChatRole.ASSISTANT, answer))
                append(ChatMsg(role = ChatRoleUi.ASSISTANT, text = answer.ifBlank { "(empty response)" }))
                schedulePersist()
            } catch (e: Exception) {
                append(ChatMsg(role = ChatRoleUi.ASSISTANT, text = "Error: ${e.message ?: e.javaClass.simpleName}"))
                schedulePersist()
            } finally {
                _isThinking.value = false
                _statusLine.value = null
            }
        }
    }

    private fun append(msg: ChatMsg) {
        _messages.value += msg
        schedulePersist()
    }
}
