package com.jnd.ngdroid.data

enum class AgentProvider(val displayName: String) {
    GEMINI("Gemini"),
    OPENCODE_ZEN("OpenCode Zen")
}

data class AgentSettings(
    val provider: AgentProvider = AgentProvider.OPENCODE_ZEN,
    val geminiApiKey: String = "",
    val zenApiKey: String = "",
    /** Optional Brave Search key. Blank = free DuckDuckGo backend. */
    val searchApiKey: String = "",
    /** Explicit user-picked model id. Blank = nothing chosen yet. Never defaulted in code. */
    val selectedModel: String = "",
    val sessionId: String = "spiceagent-01"
) {
    fun activeApiKey(): String = when (provider) {
        AgentProvider.GEMINI -> geminiApiKey.trim()
        AgentProvider.OPENCODE_ZEN -> zenApiKey.trim()
    }

    fun hasKey(): Boolean = activeApiKey().isNotEmpty()
}
