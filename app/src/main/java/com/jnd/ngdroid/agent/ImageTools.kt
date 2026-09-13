package com.jnd.ngdroid.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

private val imageJson = Json { ignoreUnknownKeys = true }

data class ImageResult(
    val title: String,
    val imageUrl: String,
    val pageUrl: String,
    val thumbnailUrl: String = "",
    val source: String = ""
)

/**
 * Pure parser for Brave Image Search API JSON (`results[]` with
 * title/url/thumbnail{src}/properties{url}/source). JVM-testable.
 */
fun parseBraveImageSearch(json: String, maxResults: Int = 5): List<ImageResult> {
    if (maxResults <= 0) return emptyList()
    return runCatching {
        val root = imageJson.parseToJsonElement(json).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()
        results.take(maxResults).mapNotNull { el ->
            val obj = el.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val pageUrl = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val imageUrl = obj["properties"]?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isBlank() || imageUrl.isBlank()) return@mapNotNull null
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                return@mapNotNull null
            }
            val thumbnail = obj["thumbnail"]?.jsonObject
                ?.get("src")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val source = obj["source"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            ImageResult(title, imageUrl, pageUrl, thumbnail, source)
        }
    }.getOrElse { emptyList() }
}

/**
 * Pure parser for DuckDuckGo `i.js` image JSON (`results[]` with
 * image/thumbnail/title/url). JVM-testable; network-free.
 */
fun parseDuckImageJson(json: String, maxResults: Int = 5): List<ImageResult> {
    if (maxResults <= 0) return emptyList()
    return runCatching {
        val root = imageJson.parseToJsonElement(json).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()
        results.take(maxResults).mapNotNull { el ->
            val obj = el.jsonObject
            val imageUrl = obj["image"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                return@mapNotNull null
            }
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()
                .orEmpty().replace(Regex("\\s+"), " ")
            val pageUrl = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            ImageResult(
                title = title.ifBlank { imageUrl.substringAfterLast('/').substringBefore('?') },
                imageUrl = imageUrl,
                pageUrl = pageUrl,
                thumbnailUrl = thumbnail
            )
        }
    }.getOrElse { emptyList() }
}

/** Extract the `vqd` search token from DDG image-search page HTML. Pure. */
fun extractVqd(html: String): String? {
    val quoted = Regex("""vqd=(["'])(.+?)\1""").find(html)?.groupValues?.get(2)
    if (!quoted.isNullOrBlank()) return quoted
    return Regex("""vqd=([A-Za-z0-9\-_]+)""").find(html)?.groupValues?.get(1)
}

/**
 * Pure parser for Wikimedia Commons API search JSON
 * (`query.pages[].title/imageinfo[0]{url,thumburl,responsiveUrls}/descriptionurl`).
 * SVG originals resolve to their PNG preview (image loaders decode raster).
 */
fun parseCommonsApi(json: String, maxResults: Int = 5): List<ImageResult> {
    if (maxResults <= 0) return emptyList()
    return runCatching {
        val root = imageJson.parseToJsonElement(json).jsonObject
        val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
            ?: return emptyList()
        pages.values.mapNotNull { it.jsonObject }.sortedBy { page ->
            page["index"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE
        }.take(maxResults).mapNotNull { page ->
            val title = page["title"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.removePrefix("File:").orEmpty()
            val info = page["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@mapNotNull null
            val original = info["url"]?.jsonPrimitive?.contentOrNull
                ?.trim()?.substringBefore('?').orEmpty()
            if (!original.startsWith("http://") && !original.startsWith("https://")) {
                return@mapNotNull null
            }
            val thumb = info["thumburl"]?.jsonPrimitive?.contentOrNull
                ?.trim()?.substringBefore('?').orEmpty()
            val large = info["responsiveUrls"]?.jsonObject
                ?.get("2")?.jsonPrimitive?.contentOrNull
                ?.trim()?.substringBefore('?').orEmpty()
            // Image loaders decode raster formats: SVGs use the PNG preview.
            val imageUrl = if (original.substringBefore('?').lowercase().endsWith(".svg")) {
                (large.ifBlank { thumb }).ifBlank { original }
            } else original
            if (title.isBlank()) return@mapNotNull null
            ImageResult(
                title = title,
                imageUrl = imageUrl,
                pageUrl = info["descriptionurl"]?.jsonPrimitive?.contentOrNull
                    ?.trim()?.substringBefore('?')
                    ?: page["descriptionurl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                thumbnailUrl = thumb,
                source = "Wikimedia Commons"
            )
        }
    }.getOrElse { emptyList() }
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico")

private fun cleanImageUrl(raw: String): String? {
    var url = raw.trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'', '"')
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "")
        .lowercase()
    if (ext !in imageExtensions) return null
    return url
}

/**
 * Collect direct image URLs from message text: markdown `![alt](url)` first,
 * then bare http(s) image URLs. Deduped, order-preserving, capped. Pure.
 */
fun extractImageUrls(text: String, maxImages: Int = 6): List<String> {
    if (maxImages <= 0) return emptyList()
    val out = mutableListOf<String>()
    val markdownRe = Regex("""!\[[^\]]*]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)""")
    for (match in markdownRe.findAll(text)) {
        val url = cleanImageUrl(match.groupValues[1]) ?: continue
        if (url !in out) out.add(url)
        if (out.size >= maxImages) return out
    }
    val bareRe = Regex("""https?://[^\s)<>\]"'"]+""")
    for (match in bareRe.findAll(text)) {
        val url = cleanImageUrl(match.value) ?: continue
        if (url !in out) out.add(url)
        if (out.size >= maxImages) break
    }
    return out
}

private fun imageArgString(argsJson: String, key: String): String? = runCatching {
    imageJson.parseToJsonElement(argsJson).jsonObject[key]?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun imageArgInt(argsJson: String, key: String): Int? = runCatching {
    imageJson.parseToJsonElement(argsJson).jsonObject[key]?.jsonPrimitive?.intOrNull
}.getOrNull()

class ImageSearchTool(
    private val httpGet: HttpGet = HttpClients.okHttpGet(),
    private val cookieHttpGet: HttpGet = HttpClients.okHttpGetWithCookies(),
    /** Saved Brave Search key; blank selects the free DuckDuckGo backend. */
    private val searchKeyProvider: () -> String = { "" }
) : AgentTool {
    override val name: String = "image_search"
    override val description: String =
        "Search the web for images (circuit diagrams, pinouts, scope traces, photos). " +
            "Input JSON: {\"query\": \"...\", \"count\": 5}. " +
            "Returns numbered titles with direct image URLs and source pages; " +
            "show a useful image with markdown ![title](image-url)."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"query":{"type":"string"},"count":{"type":"integer"}},"required":["query"]}"""

    override suspend fun execute(argsJson: String): String {
        val query = imageArgString(argsJson, "query")?.trim().orEmpty()
        if (query.isBlank()) return "ERROR: missing query"
        val count = imageArgInt(argsJson, "count")?.coerceIn(1, 10) ?: 5
        return try {
            val key = searchKeyProvider().trim()
            if (key.isNotEmpty()) {
                try {
                    formatResults(braveImages(query, count, key))
                } catch (e: Exception) {
                    val note = fallbackNote(e)
                    if (note != null) {
                        note + freeImages(query, count)
                    } else throw e
                }
            } else {
                freeImages(query, count)
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Free chain: DuckDuckGo first, Wikimedia Commons when DDG blocks
     * bots/rate-limits (its `i.js` endpoint 403s datacenter IPs).
     */
    private suspend fun freeImages(query: String, count: Int): String {
        return try {
            formatResults(duckImages(query, count))
        } catch (e: Exception) {
            "Note: general image search is rate-limited right now; showing " +
                "freely-licensed results from Wikimedia Commons instead.\n" +
                formatResults(commonsImages(query, count))
        }
    }

    private fun formatResults(results: List<ImageResult>): String {
        if (results.isEmpty()) {
            return "No images found. Try a more specific query (part number + " +
                "'pinout', 'schematic', 'waveform')."
        }
        val body = results.mapIndexed { i, r ->
            buildString {
                append("${i + 1}. ${r.title}\n   Image: ${r.imageUrl}")
                if (r.pageUrl.isNotBlank()) {
                    append("\n   Page: ${r.pageUrl}")
                    if (r.source.isNotBlank()) append(" (${r.source})")
                }
            }
        }.joinToString("\n").take(2000)
        return "$body\nTo show an image in chat, reply with ![title](image-url)."
    }

    /** Keyed backend: Brave Image Search API. Throws on non-2xx. */
    private suspend fun braveImages(query: String, count: Int, key: String): List<ImageResult> {
        val url = "https://api.search.brave.com/res/v1/images/search?q=" +
            URLEncoder.encode(query, "UTF-8") + "&count=$count"
        val body = withContext(Dispatchers.IO) {
            httpGet(url, mapOf("X-Subscription-Token" to key))
        }
        return parseBraveImageSearch(body, count)
    }

    /**
     * Free backend: DuckDuckGo image search (two-step: fetch the search page
     * for its `vqd` token, then query `i.js`). Best-effort — DDG may reject
     * bots/rate-limit; the error then surfaces honestly.
     */
    private suspend fun duckImages(query: String, count: Int): List<ImageResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val page = withContext(Dispatchers.IO) {
            cookieHttpGet(
                "https://duckduckgo.com/?q=$encoded&iar=images&iax=images&ia=images",
                emptyMap()
            )
        }
        val vqd = extractVqd(page)
            ?: throw IllegalStateException("image search is unavailable right now (no search token)")
        val json = withContext(Dispatchers.IO) {
            cookieHttpGet(
                "https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded" +
                    "&vqd=${URLEncoder.encode(vqd, "UTF-8")}&f=,,,,,&p=1",
                mapOf("Referer" to "https://duckduckgo.com/")
            )
        }
        return parseDuckImageJson(json, count)
    }

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

    /** Free structured fallback: Wikimedia Commons API (no key, no scraping). */
    private suspend fun commonsImages(query: String, count: Int): List<ImageResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
            "&generator=search&gsrsearch=$encoded&gsrlimit=$count&gsrnamespace=6" +
            "&prop=imageinfo&iiprop=url%7Csize&iiurlwidth=320"
        val body = withContext(Dispatchers.IO) {
            httpGet(
                url,
                mapOf(
                    "Accept" to "application/json",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        }
        return parseCommonsApi(body, count)
    }
}
