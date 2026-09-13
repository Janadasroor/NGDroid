package com.jnd.ngdroid.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object HttpClients {
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

    fun okHttpGet(client: OkHttpClient): HttpGet = { url, headers ->
        val builder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
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
