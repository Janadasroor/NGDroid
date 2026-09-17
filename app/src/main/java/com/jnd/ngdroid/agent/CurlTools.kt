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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val curlJson = Json { ignoreUnknownKeys = true }

data class CurlRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val raw: Boolean = false,
    val maxChars: Int = 4000
)

/**
 * Tokenize a curl command respecting single/double quotes.
 * Stops at shell chaining tokens (`|`, `>`, `;`, `&&`) — no shell is executed.
 */
fun tokenizeCurl(command: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var inSingle = false
    var inDouble = false
    var i = 0
    fun flush() {
        if (cur.isNotEmpty()) {
            out.add(cur.toString())
            cur.clear()
        }
    }
    while (i < command.length) {
        val c = command[i]
        when {
            inSingle -> if (c == '\'') inSingle = false else cur.append(c)
            inDouble -> when (c) {
                '"' -> inDouble = false
                '\\' -> if (i + 1 < command.length) {
                    i++
                    cur.append(command[i])
                } else cur.append(c)
                else -> cur.append(c)
            }
            c == '\'' -> inSingle = true
            c == '"' -> inDouble = true
            c == '\\' && i + 1 < command.length -> {
                i++
                cur.append(command[i])
            }
            c.isWhitespace() -> flush()
            c == '|' || c == ';' || c == '>' || c == '<' -> {
                flush()
                // Rest is shell piping/redirect — ignore, no shell is run.
                break
            }
            c == '&' && i + 1 < command.length && command[i + 1] == '&' -> {
                flush()
                break
            }
            else -> cur.append(c)
        }
        i++
    }
    flush()
    return out
}

private val silentFlags = setOf(
    "-L", "--location", "--location-trusted",
    "-s", "--silent", "-S", "--show-error",
    "-k", "--insecure", "--compressed",
    "-N", "--no-buffer", "--get"
)

private val valueFlagsSkip = setOf(
    "-m", "--max-time", "--connect-timeout",
    "--max-filesize", "--limit-rate", "--retry"
)

private val unsupportedFlags = setOf(
    "-X", "--request",
    "-d", "--data", "--data-raw", "--data-binary", "--data-ascii", "--data-urlencode",
    "-F", "--form", "--form-string",
    "-o", "--output", "-O", "--remote-name", "--remote-header-name",
    "-T", "--upload-file", "--upload",
    "-x", "--proxy", "--proxy-header",
    "--cookie", "--cookie-jar", "-c", "-b"
)

/**
 * Parse a safe GET-only subset of curl. Pure; JVM-testable.
 * Returns Result with CurlRequest or failure message for [CurlFetchTool.execute].
 */
fun parseCurlCommand(command: String): Result<CurlRequest> {
    val tokens = tokenizeCurl(command.trim())
    if (tokens.isEmpty() || !tokens[0].equals("curl", ignoreCase = true)) {
        return Result.failure(IllegalArgumentException("not a curl command"))
    }
    val headers = mutableMapOf<String, String>()
    var url: String? = null
    var raw = false
    var i = 1
    while (i < tokens.size) {
        val t = tokens[i]
        fun nextArg(flag: String): String? {
            val inline = flag.plus("=").let { p ->
                if (t.startsWith(p)) t.removePrefix(p) else null
            }
            if (inline != null) return inline
            if (t == flag) return tokens.getOrNull(i + 1)
            return null
        }
        when {
            t in silentFlags -> i++
            t.matches(Regex("-[a-zA-Z]+")) && t.drop(1).all { "-$it" in silentFlags } -> i++
            t in valueFlagsSkip -> i += 2
            t == "--raw" -> {
                raw = true
                i++
            }
            t == "-H" || t == "--header" || t.startsWith("--header=") -> {
                val arg = nextArg(if (t.startsWith("--header=")) "--header" else t)
                    ?: return Result.failure(IllegalArgumentException("flag '$t' needs a value"))
                val name = arg.substringBefore(":").trim()
                val value = arg.substringAfter(":", "").trim()
                if (name.isEmpty() || value.isEmpty() || !name.matches(Regex("[A-Za-z0-9-]+"))) {
                    return Result.failure(IllegalArgumentException("bad header '$arg' (want 'Name: value')"))
                }
                if (headers.size >= 10) {
                    return Result.failure(IllegalArgumentException("too many headers (max 10)"))
                }
                if (value.length > 2048) {
                    return Result.failure(IllegalArgumentException("header value too long"))
                }
                headers[name] = value
                i += if (t.contains("=") || t.startsWith("--header=")) 1 else 2
            }
            t == "-A" || t == "--user-agent" || t.startsWith("--user-agent=") -> {
                val arg = nextArg(if (t.startsWith("--user-agent=")) "--user-agent" else t)
                    ?: return Result.failure(IllegalArgumentException("flag '$t' needs a value"))
                headers["User-Agent"] = arg.take(512)
                i += if (t.contains("=")) 1 else 2
            }
            t in unsupportedFlags || (t.startsWith("-") && unsupportedFlags.any { u ->
                t == u || (u.startsWith("--") && t.startsWith("$u="))
            }) -> {
                return Result.failure(
                    IllegalArgumentException(
                        "unsupported flag '$t': curl_fetch is GET-only (no -X/-d/-F/-o/--proxy)"
                    )
                )
            }
            t.startsWith("-") -> {
                return Result.failure(
                    IllegalArgumentException(
                        "unsupported flag '$t': supported flags are -L -sS -k -H/--header -A/--user-agent --max-time --raw"
                    )
                )
            }
            t.startsWith("http://") || t.startsWith("https://") -> {
                if (url == null) url = t.trimEnd(';', ',', ')')
                i++
            }
            else -> i++
        }
    }
    val finalUrl = url
        ?: return Result.failure(IllegalArgumentException("no http(s) URL found in curl command"))
    return Result.success(CurlRequest(finalUrl, headers.toMap(), raw))
}

class CurlFetchTool(private val httpGet: HttpGet = HttpClients.okHttpGet()) : AgentTool {
    override val name: String = "curl_fetch"
    override val description: String =
        "Curl-compatible GET fetch (safe subset, no shell). " +
            "Use for direct URL fetches like `curl -L https://...`. " +
            "Input JSON: {\"command\": \"curl -L https://...\", \"raw\": false, \"maxChars\": 4000} " +
            "or {\"url\": \"https://...\", \"headers\": {\"Accept\": \"...\"}, \"raw\": true}. " +
            "GET-only; supported flags: -L -sS -k -H/--header -A/--user-agent --max-time --raw. " +
            "raw=true returns the raw body (for JSON APIs); otherwise returns readable text."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{""" +
            """"command":{"type":"string"},"url":{"type":"string"},""" +
            """"headers":{"type":"object"},"raw":{"type":"boolean"},"maxChars":{"type":"integer"}""" +
            """}}"""

    override suspend fun execute(argsJson: String): String {
        val trimmed = argsJson.trim()
        // Allow the model to pass a bare `curl ...` string instead of JSON.
        val fromBare = if (trimmed.startsWith("curl", ignoreCase = true)) trimmed else null
        val command = fromBare
            ?: runCatching {
                curlJson.parseToJsonElement(argsJson).jsonObject["command"]
                    ?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.trim().startsWith("curl", ignoreCase = true) }

        var headers = emptyMap<String, String>()
        var raw = false
        var maxChars = 4000
        var url: String? = null

        if (command != null) {
            val parsed = parseCurlCommand(command)
            val req = parsed.getOrElse { return "ERROR: ${it.message}" }
            url = req.url
            headers = req.headers
            raw = req.raw
            // JSON overrides for raw/maxChars/extra headers still apply.
            runCatching { curlJson.parseToJsonElement(argsJson).jsonObject }.getOrNull()?.let { obj ->
                obj["raw"]?.jsonPrimitive?.booleanOrNull?.let { raw = it }
                obj["maxChars"]?.jsonPrimitive?.intOrNull?.let {
                    maxChars = it.coerceIn(500, 12000)
                }
                obj["headers"]?.jsonObject?.let { h ->
                    val extra = h.entries.mapNotNull { (k, v) ->
                        val value = v.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                        if (!k.matches(Regex("[A-Za-z0-9-]+")) || value.length > 2048) null
                        else k to value
                    }.toMap()
                    if (extra.isNotEmpty()) headers = headers + extra
                }
            }
        } else {
            val obj = runCatching {
                curlJson.parseToJsonElement(argsJson).jsonObject
            }.getOrNull() ?: return "ERROR: pass {\"command\": \"curl -L https://...\"} " +
                "or {\"url\": \"https://...\"}"
            url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim()
            obj["raw"]?.jsonPrimitive?.booleanOrNull?.let { raw = it }
            obj["maxChars"]?.jsonPrimitive?.intOrNull?.let {
                maxChars = it.coerceIn(500, 12000)
            }
            obj["headers"]?.jsonObject?.let { h ->
                headers = h.entries.mapNotNull { (k, v) ->
                    val value = v.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                    if (!k.matches(Regex("[A-Za-z0-9-]+")) || value.length > 2048) null
                    else k to value
                }.toMap()
            }
        }

        val finalUrl = url?.trim().orEmpty()
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            return "ERROR: only http(s) URLs are supported"
        }
        // Same binary guard as fetch_url: don't download files as text.
        val path = finalUrl.substringBefore("?").lowercase()
        if (!raw && (path.endsWith(".pdf") || path.endsWith(".zip") ||
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".mp4"))
        ) {
            return "ERROR: that URL is a file download (e.g. PDF), not a readable " +
                "page — fetch the HTML product/doc page instead, or use raw:true for APIs."
        }
        return try {
            val body = withContext(Dispatchers.IO) { httpGet(finalUrl, headers) }
            if (raw) {
                if (body.isBlank()) "No content at $finalUrl." else body.take(maxChars)
            } else {
                val text = htmlToText(body, maxChars)
                if (text.contains('�') && text.length < 200) {
                    "ERROR: the page did not decode as readable text (likely a file " +
                        "download) — fetch the HTML version instead, or use raw:true for APIs."
                } else if (text.isBlank()) "No readable text at $finalUrl." else text
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
