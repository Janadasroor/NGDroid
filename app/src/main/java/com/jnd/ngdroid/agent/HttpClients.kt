package com.jnd.ngdroid.agent

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
object HttpClients {
    /** Browser UA: bot-protection (Wikimedia, Brave, DDG) 403s OkHttp/Coil default UAs. */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

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
}
