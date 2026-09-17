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

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
object HttpClients {
    /** Browser UA: bot-protection (Wikimedia, Brave, DDG) 403s OkHttp/Coil default UAs. */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /** Max silence on a live stream before it counts as stalled (token flow never pauses this long). */
    const val STREAM_STALL_SECONDS = 120L

    /** Bounded client for model-catalog GETs (never hang the picker spinner forever). */
    fun listModelsClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Default OkHttp-backed HttpPost. Throws on non-2xx with body snippet. */
    fun okHttpPost(): HttpPost = okHttpPost(client)

    /** Default OkHttp-backed HttpGet with browser UA (some search endpoints block bots). */
    fun okHttpGet(): HttpGet = okHttpGet(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    )

    /** OkHttp-backed HttpGet with an in-memory cookie jar (multi-step flows). */
    fun okHttpGetWithCookies(): HttpGet {
        val jar = object : CookieJar {
            private val store = mutableMapOf<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                store[url.host].orEmpty()
        }
        return okHttpGet(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cookieJar(jar)
                .build()
        )
    }

    fun okHttpGet(client: OkHttpClient): HttpGet = { url, headers ->
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .get()
        for ((k, v) in headers) builder.header(k, v)
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} for $url: ${body.take(500)}")
            }
            body
        }
    }

    /** Byte fetcher behind download_file: follows redirects, caps at 20 MB. */
    fun okHttpBytes(): HttpBytes = okHttpBytes(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    )

    fun okHttpBytes(client: OkHttpClient): HttpBytes = { url, headers ->
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .get()
        for ((k, v) in headers) builder.header(k, v)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val peek = runCatching { resp.body?.string().orEmpty().take(500) }.getOrDefault("")
                throw IllegalStateException("HTTP ${resp.code} for $url: $peek")
            }
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (bytes.size > MAX_DOWNLOAD_BYTES) {
                throw IllegalStateException(
                    "file is too large (${bytes.size} bytes, max $MAX_DOWNLOAD_BYTES)"
                )
            }
            val contentType = resp.header("Content-Type").orEmpty()
            val finalUrl = resp.request.url.toString()
            FetchedFile(bytes, contentType, finalUrl)
        }
    }

    fun okHttpPost(client: OkHttpClient): HttpPost = { url, headers, bodyJson ->
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val builder = Request.Builder().url(url).post(bodyJson.toRequestBody(mediaType))
        for ((k, v) in headers) builder.header(k, v)
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} for $url: ${body.take(500)}")
            }
            body
        }
    }

    /** Default SSE-backed HttpStream (no read timeout: streams stay open). */
    fun okHttpStream(): HttpStream = okHttpStream(client)

    fun okHttpStream(client: OkHttpClient): HttpStream = { url, headers, bodyJson, onEvent ->
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(bodyJson.toRequestBody(mediaType))
        for ((k, v) in headers) builder.header(k, v)
        val streamClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
        streamClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val peek = runCatching { resp.body?.string().orEmpty().take(500) }.getOrDefault("")
                throw IllegalStateException("HTTP ${resp.code} for $url: $peek")
            }
            val source = resp.body?.source()
                ?: throw IllegalStateException("Empty stream for $url")
            // Stall watchdog: readTimeout(0) keeps streams open, but a
            // gateway that goes quiet forever would hang the run (blocking
            // reads aren't cancellable) — any 120 s gap fails loudly.
            source.timeout().timeout(STREAM_STALL_SECONDS, TimeUnit.SECONDS)
            // Frame-level parsing only; providers interpret event/data.
            val buf = StringBuilder()
            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) {
                        parseSseBlock(buf.toString())?.let(onEvent)
                        buf.clear()
                    } else {
                        buf.append(line).append('\n')
                    }
                }
            } catch (e: SocketTimeoutException) {
                throw IllegalStateException(
                    "AI stream stalled (no data for $STREAM_STALL_SECONDS s). " +
                        "Retry; if it repeats, pick another model."
                )
            }
            parseSseBlock(buf.toString())?.let(onEvent)
        }
    }
}
