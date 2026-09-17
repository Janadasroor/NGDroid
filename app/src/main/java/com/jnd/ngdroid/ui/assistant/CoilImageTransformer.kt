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

package com.jnd.ngdroid.ui.assistant

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jnd.ngdroid.agent.HttpClients
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.model.ImageTransformer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Coil 3-backed image loader for assistant markdown (`![alt](https://…)`),
 * so responses can show circuit diagrams, plots and photos inline.
 * Uses the official mikepenz coil3 transformer.
 */
val AssistantImageTransformer: ImageTransformer = Coil3ImageTransformerImpl

/**
 * App chat image loader: OkHttp transport (the markdown coil3 module brings
 * no network fetcher, so remote images rendered blank) with a browser
 * User-Agent (bot protection 403s OkHttp/Dalvik default UAs, e.g. Wikimedia
 * thumbs). Installed as the Coil singleton in [MainActivity][com.jnd.ngdroid.MainActivity].
 */
fun newChatImageLoader(context: Context): ImageLoader {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", HttpClients.BROWSER_USER_AGENT)
                    .build()
            )
        }
        .build()
    return ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
        .crossfade(true)
        .build()
}
