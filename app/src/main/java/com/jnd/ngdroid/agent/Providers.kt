/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid.agent

enum class ProviderId {
    GEMINI,
    OPENAI,
    ANTHROPIC,
    OPENCODE_ZEN,
    OPENCODE_GO,
    OPENROUTER
}

/**
 * Provider configuration (cloud endpoints only — no baseUrl override).
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
