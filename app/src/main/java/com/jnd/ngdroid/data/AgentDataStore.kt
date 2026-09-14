package com.jnd.ngdroid.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agentDataStore by preferencesDataStore(name = "ngdroid_agent")

class AgentDataStore(private val context: Context) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("agent_provider")
        val GEMINI_KEY = stringPreferencesKey("gemini_key")
        val ZEN_KEY = stringPreferencesKey("zen_key")
        val SEARCH_KEY = stringPreferencesKey("search_key")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SESSION_ID = stringPreferencesKey("agent_session")
        val SKILL_VALIDATE = booleanPreferencesKey("skill_validate_netlist")
        val SKILL_TEMPLATE = booleanPreferencesKey("skill_netlist_template")
        val SKILL_APPLY = booleanPreferencesKey("skill_apply_netlist")
        val SKILL_WEB = booleanPreferencesKey("skill_web_search")
        val SKILL_FETCH = booleanPreferencesKey("skill_fetch_url")
        val SKILL_CURL = booleanPreferencesKey("skill_curl_fetch")
        val SKILL_IMAGE = booleanPreferencesKey("skill_image_search")
        val SKILL_DOWNLOAD = booleanPreferencesKey("skill_download_file")
        val SKILL_READ = booleanPreferencesKey("skill_read_file")
        val MAX_ITER = intPreferencesKey("agent_max_iter")
        val STUB_RETRIES = intPreferencesKey("agent_stub_retries")
        val AUTO_PICK = booleanPreferencesKey("agent_auto_pick")
        val CUSTOM_SKILLS = stringPreferencesKey("agent_custom_skills")
    }

    val settingsFlow: Flow<AgentSettings> = context.agentDataStore.data.map { prefs ->
        val providerStr = prefs[Keys.PROVIDER] ?: AgentProvider.OPENCODE_ZEN.name
        AgentSettings(
            provider = try { AgentProvider.valueOf(providerStr) } catch (_: Exception) { AgentProvider.OPENCODE_ZEN },
            geminiApiKey = prefs[Keys.GEMINI_KEY] ?: "",
            zenApiKey = prefs[Keys.ZEN_KEY] ?: "",
            searchApiKey = prefs[Keys.SEARCH_KEY] ?: "",
            // Migrate legacy "model_override" value forward once, then stop reading it.
            selectedModel = prefs[Keys.SELECTED_MODEL]
                ?: prefs[stringPreferencesKey("model_override")]
                ?: "",
            sessionId = prefs[Keys.SESSION_ID] ?: "spiceagent-01",
            skillValidateNetlist = prefs[Keys.SKILL_VALIDATE] ?: true,
            skillNetlistTemplate = prefs[Keys.SKILL_TEMPLATE] ?: true,
            skillApplyNetlist = prefs[Keys.SKILL_APPLY] ?: true,
            skillWebSearch = prefs[Keys.SKILL_WEB] ?: true,
            skillFetchUrl = prefs[Keys.SKILL_FETCH] ?: true,
            skillCurlFetch = prefs[Keys.SKILL_CURL] ?: true,
            skillImageSearch = prefs[Keys.SKILL_IMAGE] ?: true,
            skillDownloadFile = prefs[Keys.SKILL_DOWNLOAD] ?: true,
            skillReadFile = prefs[Keys.SKILL_READ] ?: true,
            maxIterations = prefs[Keys.MAX_ITER] ?: 12,
            stubRetries = prefs[Keys.STUB_RETRIES] ?: 1,
            autoPickFreeModel = prefs[Keys.AUTO_PICK] ?: true,
            customSkills = prefs[Keys.CUSTOM_SKILLS]?.let { CustomSkillCodec.decode(it) } ?: emptyList()
        )
    }

    suspend fun setProvider(provider: AgentProvider) {
        context.agentDataStore.edit { it[Keys.PROVIDER] = provider.name }
    }

    suspend fun setKey(provider: AgentProvider, key: String) {
        context.agentDataStore.edit {
            when (provider) {
                AgentProvider.GEMINI -> it[Keys.GEMINI_KEY] = key
                AgentProvider.OPENCODE_ZEN -> it[Keys.ZEN_KEY] = key
            }
        }
    }

    suspend fun setKeys(geminiKey: String, zenKey: String) {
        context.agentDataStore.edit {
            it[Keys.GEMINI_KEY] = geminiKey
            it[Keys.ZEN_KEY] = zenKey
        }
    }

    suspend fun setSearchKey(key: String) {
        context.agentDataStore.edit { it[Keys.SEARCH_KEY] = key }
    }

    suspend fun setSelectedModel(modelId: String) {
        context.agentDataStore.edit { it[Keys.SELECTED_MODEL] = modelId }
    }

    suspend fun setSessionId(sessionId: String) {
        context.agentDataStore.edit { it[Keys.SESSION_ID] = sessionId }
    }

    suspend fun setSkill(id: String, enabled: Boolean) {
        context.agentDataStore.edit {
            when (id) {
                "validate_netlist" -> it[Keys.SKILL_VALIDATE] = enabled
                "netlist_template" -> it[Keys.SKILL_TEMPLATE] = enabled
                "apply_netlist" -> it[Keys.SKILL_APPLY] = enabled
                "web_search" -> it[Keys.SKILL_WEB] = enabled
                "fetch_url" -> it[Keys.SKILL_FETCH] = enabled
                "curl_fetch" -> it[Keys.SKILL_CURL] = enabled
                "image_search" -> it[Keys.SKILL_IMAGE] = enabled
                "download_file" -> it[Keys.SKILL_DOWNLOAD] = enabled
                "read_file" -> it[Keys.SKILL_READ] = enabled
            }
        }
    }

    suspend fun setMaxIterations(v: Int) {
        context.agentDataStore.edit { it[Keys.MAX_ITER] = v.coerceIn(4, 20) }
    }

    suspend fun setStubRetries(v: Int) {
        context.agentDataStore.edit { it[Keys.STUB_RETRIES] = v.coerceIn(0, 3) }
    }

    suspend fun setAutoPick(v: Boolean) {
        context.agentDataStore.edit { it[Keys.AUTO_PICK] = v }
    }

    suspend fun setCustomSkills(skills: List<CustomSkill>) {
        context.agentDataStore.edit { it[Keys.CUSTOM_SKILLS] = CustomSkillCodec.encode(skills.take(50)) }
    }

    suspend fun updateAll(settings: AgentSettings) {
        context.agentDataStore.edit {
            it[Keys.PROVIDER] = settings.provider.name
            it[Keys.GEMINI_KEY] = settings.geminiApiKey
            it[Keys.ZEN_KEY] = settings.zenApiKey
            it[Keys.SEARCH_KEY] = settings.searchApiKey
            it[Keys.SELECTED_MODEL] = settings.selectedModel
            it[Keys.SESSION_ID] = settings.sessionId
            it[Keys.SKILL_VALIDATE] = settings.skillValidateNetlist
            it[Keys.SKILL_TEMPLATE] = settings.skillNetlistTemplate
            it[Keys.SKILL_APPLY] = settings.skillApplyNetlist
            it[Keys.SKILL_WEB] = settings.skillWebSearch
            it[Keys.SKILL_FETCH] = settings.skillFetchUrl
            it[Keys.SKILL_CURL] = settings.skillCurlFetch
            it[Keys.SKILL_IMAGE] = settings.skillImageSearch
            it[Keys.SKILL_DOWNLOAD] = settings.skillDownloadFile
            it[Keys.SKILL_READ] = settings.skillReadFile
            it[Keys.MAX_ITER] = settings.maxIterations.coerceIn(4, 20)
            it[Keys.STUB_RETRIES] = settings.stubRetries.coerceIn(0, 3)
            it[Keys.AUTO_PICK] = settings.autoPickFreeModel
            it[Keys.CUSTOM_SKILLS] = CustomSkillCodec.encode(settings.customSkills)
        }
    }

    suspend fun clearAll() {
        context.agentDataStore.edit {
            it.remove(Keys.PROVIDER)
            it.remove(Keys.GEMINI_KEY)
            it.remove(Keys.ZEN_KEY)
            it.remove(Keys.SEARCH_KEY)
            it.remove(Keys.SELECTED_MODEL)
            it.remove(stringPreferencesKey("model_override"))
            it.remove(Keys.SESSION_ID)
            it.remove(Keys.SKILL_VALIDATE)
            it.remove(Keys.SKILL_TEMPLATE)
            it.remove(Keys.SKILL_APPLY)
            it.remove(Keys.SKILL_WEB)
            it.remove(Keys.SKILL_FETCH)
            it.remove(Keys.SKILL_CURL)
            it.remove(Keys.SKILL_IMAGE)
            it.remove(Keys.SKILL_DOWNLOAD)
            it.remove(Keys.SKILL_READ)
            it.remove(Keys.MAX_ITER)
            it.remove(Keys.STUB_RETRIES)
            it.remove(Keys.AUTO_PICK)
            it.remove(Keys.CUSTOM_SKILLS)
        }
    }
}
