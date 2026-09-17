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

/**
 * OpenRouter provider, cloud-direct (`https://openrouter.ai/api/v1`).
 *
 * Fully OpenAI-compatible (chat/completions, nested tools[].function,
 * vision image_url parts, `{data[].id}` models list), so this reuses
 * [OpenAiProvider] with a different base URL plus the attribution header.
 * One key serves 400+ models; `:free`-suffixed variants cost nothing.
 * Default is the `openrouter/free` router, which picks a capable free
 * model (vision/tool-aware) per request.
 */
class OpenRouterProvider(
    http: HttpPost,
    apiKey: String,
    model: String = "openrouter/free",
    streamHttp: HttpStream? = null
) : OpenAiProvider(
    http = http,
    apiKey = apiKey,
    model = model,
    streamHttp = streamHttp,
    baseUrl = OPENROUTER_BASE,
    extraHeaders = mapOf("X-Title" to "NGDroid"),
    id = "openrouter",
    displayName = "OpenRouter",
    defaultModel = "openrouter/free"
) {
    companion object {
        const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
    }
}
