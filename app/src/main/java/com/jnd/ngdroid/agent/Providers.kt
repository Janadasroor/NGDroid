package com.jnd.ngdroid.agent

enum class ProviderId {
    GEMINI,
    OPENCODE_ZEN
}

/**
 * Provider configuration (cloud-direct only).
 *
 * No baseUrl: providers always hit their cloud endpoints
 * (Gemini -> generativelanguage.googleapis.com, Zen -> https://opencode.ai/zen/v1).
 * Provide the user's own API key. `model` overrides the provider default.
 * `sessionId` overrides the Zen `x-opencode-session` header value;
 * null = provider default, blank = omit the header.
 */
data class ProviderConfig(
    val providerId: ProviderId,
    val apiKey: String,
    val model: String? = null,
    /** Free-tier session header value. Null = provider default. Blank = omit header. */
    val sessionId: String? = null
)

/** Simple in-memory store for API keys + per-provider model overrides. */
class InMemoryKeyStore {
    private val keys = mutableMapOf<ProviderId, String>()
    private val models = mutableMapOf<ProviderId, String>()

    fun getKey(id: ProviderId): String? = keys[id]

    fun setKey(id: ProviderId, key: String) {
        keys[id] = key
    }

    fun getModel(id: ProviderId): String? = models[id]

    fun setModel(id: ProviderId, model: String) {
        models[id] = model
    }

    fun resolveModel(config: ProviderConfig, defaultModel: String): String {
        config.model?.let { return it }
        models[config.providerId]?.let { return it }
        return defaultModel
    }
}
