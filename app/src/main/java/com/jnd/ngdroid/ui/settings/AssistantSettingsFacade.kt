package com.jnd.ngdroid.ui.settings

import androidx.compose.runtime.Composable
import com.jnd.ngdroid.data.AgentProvider
import com.jnd.ngdroid.data.AgentSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow settings contract the settings UI needs. Implemented by
 * `AssistantViewModel`; the composables below only see this facade, so they
 * stay previewable/testable without the full agent stack.
 */
interface AssistantSettingsFacade {
    val settings: StateFlow<AgentSettings>
    fun updateProvider(provider: AgentProvider)
    fun updateKey(provider: AgentProvider, key: String)
    fun updateSearchKey(key: String)
    fun updateSessionId(sessionId: String)
    fun updateSkill(id: String, enabled: Boolean)
    fun addCustomSkill(name: String, description: String, instructions: String): String?
    fun updateCustomSkill(id: String, name: String, description: String, instructions: String): Boolean
    fun deleteCustomSkill(id: String)
    fun setCustomSkillEnabled(id: String, enabled: Boolean)
    fun updateMaxIterations(v: Int)
    fun updateStubRetries(v: Int)
    fun updateAutoPick(v: Boolean)
}

/** Preview renderer for skill markdown (host supplies the chat markdown engine). */
typealias SkillPreviewRenderer = @Composable (String) -> Unit
