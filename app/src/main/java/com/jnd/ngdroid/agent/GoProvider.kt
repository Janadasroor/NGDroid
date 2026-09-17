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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenCode Go provider, cloud-direct (`https://opencode.ai/zen/go/v1`).
 *
 * Go is the low-cost subscription catalog of open coding models. It serves
 * each model family on a different route (per opencode.ai/docs/go):
 * - `/responses`: grok-*, gpt-*, muse-spark-*
 * - `/messages` (Anthropic-compatible): qwen*, minimax-*
 * - `/chat/completions` (OpenAI-compatible): everything else
 * Auth mirrors Zen (`Bearer` key + `x-opencode-session`); the Anthropic
 * route uses the same key as `x-api-key`. No free tier: a subscription
 * key is always required.
 */
class GoProvider(
    private val http: HttpPost,
    private val apiKey: String,
    private val model: String = "kimi-k3",
    private val sessionId: String? = ZenProvider.DEFAULT_SESSION_ID,
    /** SSE transport; null = non-streaming chat() with a single partial. */
    private val streamHttp: HttpStream? = null
) : LlmProvider {

    override val id: String = "opencode-go"
    override val displayName: String = "OpenCode Go"
    override val defaultModel: String = "kimi-k3"

    override suspend fun chat(req: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val requested = ZenProvider.normalizeModelId(model)
        // The Zen-shaped delegate routes /responses vs /chat/completions itself.
        if (isMessagesModel(requested)) {
            AnthropicProvider(http, apiKey, requested, GO_BASE).chat(req)
        } else {
            ZenProvider(http, apiKey, requested, sessionId, GO_BASE).chat(req)
        }
    }

    override suspend fun streamChat(req: LlmRequest, onPartial: (String) -> Unit): LlmResponse =
        withContext(Dispatchers.IO) {
            val stream = streamHttp ?: return@withContext super.streamChat(req, onPartial)
            val requested = ZenProvider.normalizeModelId(model)
            if (isMessagesModel(requested)) {
                AnthropicProvider(http, apiKey, requested, GO_BASE, stream).streamChat(req, onPartial)
            } else {
                ZenProvider(http, apiKey, requested, sessionId, GO_BASE, stream).streamChat(req, onPartial)
            }
        }

    override suspend fun listModels(apiKey: String): List<String> =
        ZenProvider(http, apiKey, "", sessionId, GO_BASE).listModels(apiKey)

    companion object {
        /** Cloud Go API base — the only endpoint used. No localhost/shim. */
        const val GO_BASE = "https://opencode.ai/zen/go/v1"

        /**
         * True for Go models served on the Anthropic-compatible `/messages`
         * route (qwen*, minimax-* per the Go endpoint table). Pure.
         */
        fun isMessagesModel(id: String): Boolean {
            val low = id.trim().lowercase()
            return low.startsWith("qwen") || low.startsWith("minimax-")
        }
    }
}
