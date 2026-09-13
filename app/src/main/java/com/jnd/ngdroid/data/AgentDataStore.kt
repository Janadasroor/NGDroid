package com.jnd.ngdroid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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
            sessionId = prefs[Keys.SESSION_ID] ?: "spiceagent-01"
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

    suspend fun updateAll(settings: AgentSettings) {
        context.agentDataStore.edit {
            it[Keys.PROVIDER] = settings.provider.name
            it[Keys.GEMINI_KEY] = settings.geminiApiKey
            it[Keys.ZEN_KEY] = settings.zenApiKey
            it[Keys.SEARCH_KEY] = settings.searchApiKey
            it[Keys.SELECTED_MODEL] = settings.selectedModel
            it[Keys.SESSION_ID] = settings.sessionId
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
        }
    }
}
