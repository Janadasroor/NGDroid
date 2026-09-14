package com.jnd.ngdroid.data

enum class AgentProvider(val displayName: String) {
    GEMINI("Gemini"),
    OPENAI("OpenAI"),
    OPENCODE_ZEN("OpenCode Zen")
}

data class AgentSettings(
    val provider: AgentProvider = AgentProvider.OPENCODE_ZEN,
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    val zenApiKey: String = "",
    /** Optional Brave Search key. Blank = free DuckDuckGo backend. */
    val searchApiKey: String = "",
    /** Explicit user-picked model id. Blank = nothing chosen yet. Never defaulted in code. */
    val selectedModel: String = "",
    val sessionId: String = "spiceagent-01",
    // ---- skills (built-in tool toggles, all on by default) ----
    val skillValidateNetlist: Boolean = true,
    val skillNetlistTemplate: Boolean = true,
    val skillApplyNetlist: Boolean = true,
    val skillWebSearch: Boolean = true,
    val skillFetchUrl: Boolean = true,
    val skillCurlFetch: Boolean = true,
    val skillImageSearch: Boolean = true,
    val skillDownloadFile: Boolean = true,
    val skillReadFile: Boolean = true,
    // ---- reasoning limits ----
    val maxIterations: Int = 12,
    val stubRetries: Int = 1,
    val autoPickFreeModel: Boolean = true,
    // ---- user-created skills (prompt snippets) ----
    val customSkills: List<CustomSkill> = emptyList()
) {
    fun activeApiKey(): String = when (provider) {
        AgentProvider.GEMINI -> geminiApiKey.trim()
        AgentProvider.OPENAI -> openaiApiKey.trim()
        AgentProvider.OPENCODE_ZEN -> zenApiKey.trim()
    }

    fun hasKey(): Boolean = activeApiKey().isNotEmpty()

    /** Built-in tool toggle lookup by tool id. Unknown ids default to on. */
    fun isSkillEnabled(toolId: String): Boolean = when (toolId) {
        "validate_netlist" -> skillValidateNetlist
        "netlist_template" -> skillNetlistTemplate
        "apply_netlist" -> skillApplyNetlist
        "web_search" -> skillWebSearch
        "fetch_url" -> skillFetchUrl
        "curl_fetch" -> skillCurlFetch
        "image_search" -> skillImageSearch
        "download_file" -> skillDownloadFile
        "read_file" -> skillReadFile
        else -> true
    }

    fun withSkill(id: String, enabled: Boolean): AgentSettings = when (id) {
        "validate_netlist" -> copy(skillValidateNetlist = enabled)
        "netlist_template" -> copy(skillNetlistTemplate = enabled)
        "apply_netlist" -> copy(skillApplyNetlist = enabled)
        "web_search" -> copy(skillWebSearch = enabled)
        "fetch_url" -> copy(skillFetchUrl = enabled)
        "curl_fetch" -> copy(skillCurlFetch = enabled)
        "image_search" -> copy(skillImageSearch = enabled)
        "download_file" -> copy(skillDownloadFile = enabled)
        "read_file" -> copy(skillReadFile = enabled)
        else -> this
    }

    fun enabledCustomSkills(): List<CustomSkill> =
        customSkills.filter { it.enabled && it.instructions.isNotBlank() }

    fun coercedMaxIterations(): Int = maxIterations.coerceIn(4, 20)
    fun coercedStubRetries(): Int = stubRetries.coerceIn(0, 3)
}
