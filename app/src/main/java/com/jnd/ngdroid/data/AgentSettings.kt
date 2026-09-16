package com.jnd.ngdroid.data

import com.jnd.ngdroid.agent.HostDefaults

enum class AgentProvider(val displayName: String) {
    GEMINI("Gemini"),
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    OPENCODE_ZEN("OpenCode Zen"),
    OPENCODE_GO("OpenCode Go"),
    OPENROUTER("OpenRouter")
}

/** All known built-in tool ids enabled — the default skill map. */
fun defaultSkills(): Map<String, Boolean> =
    BUILTIN_SKILL_IDS.associateWith { true }

data class AgentSettings(
    val provider: AgentProvider = AgentProvider.OPENCODE_ZEN,
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    val anthropicApiKey: String = "",
    val zenApiKey: String = "",
    val goApiKey: String = "",
    val openRouterApiKey: String = "",
    /** Optional Brave Search key. Blank = free DuckDuckGo backend. */
    val searchApiKey: String = "",
    /** Explicit user-picked model id. Blank = nothing chosen yet. Never defaulted in code. */
    val selectedModel: String = "",
    val sessionId: String = HostDefaults.DEFAULT_SESSION_ID,
    // ---- skills (built-in tool toggles, all on by default) ----
    // Single map keyed by tool id: adding a tool no longer touches this class.
    // Unknown ids default to on (see isSkillEnabled).
    val skills: Map<String, Boolean> = defaultSkills(),
    // ---- reasoning limits ----
    val maxIterations: Int = 12,
    val stubRetries: Int = 1,
    val autoPickFreeModel: Boolean = true,
    // ---- user-created skills (prompt snippets) ----
    val customSkills: List<CustomSkill> = emptyList()
) {
    fun activeApiKey(): String = apiKeyFor(provider)

    /** Saved key for one provider (blank = none). Trimmed. */
    fun apiKeyFor(provider: AgentProvider): String = when (provider) {
        AgentProvider.GEMINI -> geminiApiKey.trim()
        AgentProvider.OPENAI -> openaiApiKey.trim()
        AgentProvider.ANTHROPIC -> anthropicApiKey.trim()
        AgentProvider.OPENCODE_ZEN -> zenApiKey.trim()
        AgentProvider.OPENCODE_GO -> goApiKey.trim()
        AgentProvider.OPENROUTER -> openRouterApiKey.trim()
    }

    fun hasKey(): Boolean = activeApiKey().isNotEmpty()

    /** Built-in tool toggle lookup by tool id. Unknown ids default to on. */
    fun isSkillEnabled(toolId: String): Boolean = skills[toolId] ?: true

    fun withSkill(id: String, enabled: Boolean): AgentSettings =
        if (id in BUILTIN_SKILL_IDS || id in skills) copy(skills = skills + (id to enabled))
        else this

    fun enabledCustomSkills(): List<CustomSkill> =
        customSkills.filter { it.enabled && it.instructions.isNotBlank() }

    fun coercedMaxIterations(): Int = maxIterations.coerceIn(4, 20)
    fun coercedStubRetries(): Int = stubRetries.coerceIn(0, 3)

    /** Redacted: the generated data-class toString would print API keys into logs/crash reports. */
    override fun toString(): String =
        "AgentSettings(provider=$provider, selectedModel=${selectedModel.ifBlank { "<none>" }}, " +
            "sessionId=$sessionId, keys=[REDACTED], maxIterations=$maxIterations, " +
            "stubRetries=$stubRetries, customSkills=${customSkills.size})"
}
