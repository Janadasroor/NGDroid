package com.jnd.ngdroid.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

data class WebResult(val title: String, val url: String, val snippet: String)

private val jsonLenient = Json { ignoreUnknownKeys = true }

private fun argString(argsJson: String, key: String): String? = runCatching {
    jsonLenient.parseToJsonElement(argsJson).jsonObject[key]?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun argInt(argsJson: String, key: String): Int? = runCatching {
    jsonLenient.parseToJsonElement(argsJson).jsonObject[key]?.jsonPrimitive?.intOrNull
}.getOrNull()

/** Minimal HTML entity unescape for search/fetch output. */
fun unescapeHtml(s: String): String {
    var out = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
    out = Regex("&#(\\d+);").replace(out) {
        it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value
    }
    return out
}

private fun stripTags(s: String): String = unescapeHtml(s.replace(Regex("<[^>]*>"), "")).trim()

/**
 * Pure parser for DuckDuckGo lite (`lite.duckduckgo.com/lite/`) result HTML.
 * Each result link is paired with the first `result-snippet` cell after it.
 * JVM-testable; network-free.
 */
fun parseDuckDuckGoLite(html: String, maxResults: Int = 5): List<WebResult> {
    if (maxResults <= 0) return emptyList()
    val linkRe = Regex("""<a\s+rel="nofollow"\s+href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    val snippetRe = Regex(
        """class=['"]result-snippet['"][^>]*>(.*?)</td>""",
        RegexOption.DOT_MATCHES_ALL
    )
    val links = linkRe.findAll(html).toList()
    val snippets = snippetRe.findAll(html).toList()
    val out = mutableListOf<WebResult>()
    for (link in links) {
        if (out.size >= maxResults) break
        val rawHref = unescapeHtml(link.groupValues[1]).trim()
        val url = resolveDuckHref(rawHref) ?: continue
        val title = stripTags(link.groupValues[2]).replace(Regex("\\s+"), " ")
        if (title.isBlank()) continue
        val snippet = snippets
            .firstOrNull { it.range.first > link.range.first }
            ?.let { stripTags(it.groupValues[1]).replace(Regex("\\s+"), " ") }
            .orEmpty()
        out.add(WebResult(title, url, snippet))
    }
    return out
}

/** Resolve a DDG lite href: unwrap `uddg` redirect, absolutize `//` links, drop DDG-internal ones. */
private fun resolveDuckHref(href: String): String? {
    if (href.isBlank()) return null
    val uddg = Regex("[?&]uddg=([^&]+)").find(href)?.groupValues?.get(1)
    if (uddg != null) {
        return runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrNull()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
    val absolute = if (href.startsWith("//")) "https:$href" else href
    if (!absolute.startsWith("http://") && !absolute.startsWith("https://")) return null
    if (Regex("^https?://([a-z0-9-]+\\.)*duckduckgo\\.com").containsMatchIn(absolute)) return null
    return absolute
}

/** Strip scripts/styles/tags from page HTML into readable collapsed text. Pure; JVM-testable. */
fun htmlToText(html: String, maxChars: Int = 4000): String {
    var t = html
    t = Regex(
        """<(script|style|noscript|header|footer|nav)[^>]*>.*?</\1>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    ).replace(t, " ")
    t = Regex("<!--[\\s\\S]*?-->").replace(t, " ")
    t = t.replace(Regex("<[^>]*>"), " ")
    t = unescapeHtml(t).replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
    t = t.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    return t.take(maxChars).trim()
}

class WebSearchTool(
    private val httpGet: HttpGet = HttpClients.okHttpGet(),
    /** Saved Brave Search key; blank selects the free DuckDuckGo backend. */
    private val searchKeyProvider: () -> String = { "" }
) : AgentTool {
    override val name: String = "web_search"
    override val description: String =
        "Search the web for current/external facts (datasheets, part specs, docs). " +
            "Input JSON: {\"query\": \"...\", \"count\": 5}. " +
            "Returns numbered titles, URLs and snippets; use fetch_url to read a result."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"query":{"type":"string"},"count":{"type":"integer"}},"required":["query"]}"""

    override suspend fun execute(argsJson: String): String {
        val query = argString(argsJson, "query")?.trim().orEmpty()
        if (query.isBlank()) return "ERROR: missing query"
        val count = argInt(argsJson, "count")?.coerceIn(1, 10) ?: 5
        return try {
            val key = searchKeyProvider().trim()
            if (key.isNotEmpty()) {
                try {
                    formatResults(braveSearch(query, count, key), query)
                } catch (e: Exception) {
                    // Bad/over-quota key degrades to free DDG instead of
                    // failing the whole turn.
                    val note = fallbackNote(e)
                    if (note != null) {
                        note + formatResults(duckSearch(query, count), query)
                    } else throw e
                }
            } else {
                formatResults(duckSearch(query, count), query)
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private fun formatResults(results: List<WebResult>, query: String): String {
        if (results.isEmpty()) return "No results for '$query'."
        return results.mapIndexed { i, r ->
            buildString {
                append("${i + 1}. ${r.title}\n   ${r.url}")
                if (r.snippet.isNotBlank()) append("\n   ${r.snippet}")
            }
        }.joinToString("\n").take(2000)
    }

    /** Keyed backend: Brave Search API. Throws on non-2xx (see HttpClients.okHttpGet). */
    private suspend fun braveSearch(query: String, count: Int, key: String): List<WebResult> {
        val url = "https://api.search.brave.com/res/v1/web/search?q=" +
            URLEncoder.encode(query, "UTF-8") + "&count=$count"
        // Tools run on the caller's dispatcher (main); network must hop to IO.
        val body = withContext(Dispatchers.IO) {
            httpGet(url, mapOf("X-Subscription-Token" to key))
        }
        return parseBraveSearch(body, count)
    }

    /** Free backend: DuckDuckGo lite HTML. */
    private suspend fun duckSearch(query: String, count: Int): List<WebResult> {
        val url = "https://lite.duckduckgo.com/lite/?q=" +
            URLEncoder.encode(query, "UTF-8")
        // Tools run on the caller's dispatcher (main); network must hop to IO.
        val body = withContext(Dispatchers.IO) { httpGet(url, emptyMap()) }
        return parseDuckDuckGoLite(body, count)
    }

    /**
     * Note prefix when a keyed search fails for a key/quota reason (worth
     * degrading to DuckDuckGo); null for real failures that must surface.
     * Brave rejects bad subscription tokens with HTTP 422, not 401.
     */
    private fun fallbackNote(e: Exception): String? {
        val msg = e.message.orEmpty()
        return when {
            "HTTP 401" in msg || "HTTP 403" in msg || "HTTP 422" in msg ->
                "Note: the saved search key was rejected; used DuckDuckGo instead.\n"
            "HTTP 429" in msg ->
                "Note: the search quota is exceeded; used DuckDuckGo instead.\n"
            else -> null
        }
    }
}

/**
 * Pure parser for Brave Search API JSON (`web.results[]` with
 * title/url/description). JVM-testable; network-free.
 */
fun parseBraveSearch(json: String, maxResults: Int = 5): List<WebResult> {
    if (maxResults <= 0) return emptyList()
    return runCatching {
        val root = jsonLenient.parseToJsonElement(json).jsonObject
        val results = root["web"]?.jsonObject?.get("results")?.jsonArray ?: return emptyList()
        results.take(maxResults).mapNotNull { el ->
            val obj = el.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
            val snippet = obj["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            WebResult(title, url, snippet)
        }
    }.getOrElse { emptyList() }
}

class FetchUrlTool(private val httpGet: HttpGet = HttpClients.okHttpGet()) : AgentTool {
    override val name: String = "fetch_url"
    override val description: String =
        "Fetch a web page and return its readable text (for reading search results, " +
            "datasheets, docs). Input JSON: {\"url\": \"https://...\"}."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}"""

    override suspend fun execute(argsJson: String): String {
        val url = argString(argsJson, "url")?.trim().orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "ERROR: only http(s) URLs are supported"
        }
        // Datasheets are usually PDFs: fail fast with guidance instead of
        // downloading binary and returning garbage text.
        val path = url.substringBefore("?").lowercase()
        if (path.endsWith(".pdf") || path.endsWith(".zip") ||
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
            path.endsWith(".gif") || path.endsWith(".mp4")
        ) {
            return "ERROR: that URL is a file download (e.g. PDF), not a readable " +
                "page — fetch the HTML product/doc page instead."
        }
        return try {
            val text = htmlToText(withContext(Dispatchers.IO) { httpGet(url, emptyMap()) })
            if (text.contains('�') && text.length < 200) {
                "ERROR: the page did not decode as readable text (likely a file " +
                    "download) — fetch the HTML version instead."
            } else if (text.isBlank()) "No readable text at $url." else text
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
