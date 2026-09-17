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

/**
 * Pure thinking-row helpers (no Android imports — JVM-testable).
 *
 * Tool progress is stored as [ChatRoleUi.SYSTEM] messages inline in the
 * transcript; the chat body hides them and thinking expanders surface them.
 */

/** SYSTEM steps of the still-running turn: everything after the last USER message. */
fun liveThinkingSteps(messages: List<ChatMsg>): List<ChatMsg> {
    val lastUser = messages.indexOfLast { it.role == ChatRoleUi.USER }
    if (lastUser < 0) return emptyList()
    return messages.drop(lastUser + 1).filter { it.role == ChatRoleUi.SYSTEM }
}

/**
 * Groups finished SYSTEM steps per answer: steps between a USER message and
 * the ASSISTANT message that follows it, keyed by that assistant message id.
 * Turns without tool activity map to an empty list (no expander shown).
 */
fun stepsByAssistant(messages: List<ChatMsg>): Map<String, List<ChatMsg>> {
    val out = mutableMapOf<String, List<ChatMsg>>()
    var pending = mutableListOf<ChatMsg>()
    var inTurn = false
    for (m in messages) {
        when (m.role) {
            ChatRoleUi.USER -> {
                pending = mutableListOf()
                inTurn = true
            }
            ChatRoleUi.SYSTEM -> if (inTurn) pending.add(m)
            ChatRoleUi.ASSISTANT -> {
                out[m.id] = pending.toList()
                pending = mutableListOf()
                inTurn = false
            }
        }
    }
    return out
}

enum class ToolKind { VALIDATE, APPLY, RUN, GENERATE, SEARCH, READ, DOWNLOAD }

private fun kindOf(text: String): ToolKind? {
    val s = text.lowercase()
    return when {
        "validat" in s -> ToolKind.VALIDATE
        "apply" in s -> ToolKind.APPLY
        "run" in s || "simulat" in s -> ToolKind.RUN
        "generat" in s || "template" in s -> ToolKind.GENERATE
        "download" in s -> ToolKind.DOWNLOAD
        "search" in s -> ToolKind.SEARCH
        "fetch" in s || "curl" in s || "read" in s -> ToolKind.READ
        else -> null
    }
}

/** One web result for the thinking summary. */
data class FoundPage(val title: String, val url: String, val host: String)

/** One page read (fetch/curl success or failure). */
data class ReadPage(val url: String, val host: String, val ok: Boolean)

/** Grouped search activity parsed from SYSTEM steps. Pure; JVM-testable. */
data class SearchSummary(
    val foundTotal: Int = 0,
    val foundHosts: List<String> = emptyList(),
    val foundSamples: List<FoundPage> = emptyList(),
    val readPages: List<ReadPage> = emptyList(),
    val imagesTotal: Int = 0,
    val imageHosts: List<String> = emptyList()
) {
    val readOk: List<ReadPage> get() = readPages.filter { it.ok }
    val hasSearch: Boolean get() = foundTotal > 0 || readPages.isNotEmpty() || imagesTotal > 0
}

/** Host of an http(s) URL without www., lowercase. Pure. */
fun hostOfUrl(url: String): String {
    val host = Regex("""https?://([^/:\s]+)""").find(url.trim())?.groupValues?.get(1)
        .orEmpty().lowercase()
    return host.removePrefix("www.")
}

private fun cleanUrl(raw: String): String =
    raw.trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'', '"')

/** All http(s) URLs in text, order-preserving, deduped. Pure. */
fun extractUrls(text: String): List<String> {
    val out = mutableListOf<String>()
    for (m in Regex("""https?://[^\s)<>\]"'"]+""").findAll(text)) {
        val url = cleanUrl(m.value)
        if (url.isNotBlank() && url !in out) out.add(url)
    }
    return out
}

/** Numbered web results (`1. Title` + URL on the next line). Pure. */
fun parseNumberedResults(output: String): List<FoundPage> {
    val out = mutableListOf<FoundPage>()
    val re = Regex("""(?m)^\s*\d+\.\s+([^\n]+?)\s*\n\s*(https?://[^\s]+)""")
    for (m in re.findAll(output)) {
        val url = cleanUrl(m.groupValues[2])
        if (!url.startsWith("http")) continue
        val host = hostOfUrl(url)
        if (host.isEmpty()) continue
        out.add(FoundPage(m.groupValues[1].trim().take(120), url, host))
    }
    return out
}

/** Image results (`1. Title` + `Image: url`). Pure. */
fun parseImageResults(output: String): List<FoundPage> {
    val out = mutableListOf<FoundPage>()
    val titles = Regex("""(?m)^\s*\d+\.\s+([^\n]+)""").findAll(output).map { it.groupValues[1].trim() }.toList()
    val images = Regex("""Image:\s*(https?://[^\s]+)""").findAll(output).map { cleanUrl(it.groupValues[1]) }.toList()
    for (i in images.indices) {
        val url = images[i]
        if (!url.startsWith("http")) continue
        val host = hostOfUrl(url)
        if (host.isEmpty()) continue
        out.add(FoundPage(titles.getOrNull(i).orEmpty().take(120).ifBlank { host }, url, host))
    }
    return out
}

/** URL from tool args JSON (`"url"`, or first URL inside a curl `"command"`). Pure. */
fun argsUrl(argsJson: String): String {
    Regex(""""url"\s*:\s*"([^"]+)"""").find(argsJson)?.let { return cleanUrl(it.groupValues[1]) }
    Regex(""""command"\s*:\s*"((?:\\.|[^"\\])*)"""").find(argsJson)?.let { m ->
        val unescaped = m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        Regex("""https?://[^\s"'\\]+""").find(unescaped)?.let { return cleanUrl(it.value) }
    }
    Regex("""https?://[^\s"'\\]+""").find(argsJson)?.let { return cleanUrl(it.value) }
    return ""
}

/** Search query from tool args JSON (`"query"`). Pure. */
fun argsQuery(argsJson: String): String =
    Regex(""""query"\s*:\s*"((?:\\.|[^"\\])*)"""").find(argsJson)
        ?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
        ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()

/** File name hint from tool args JSON (`fileName`/`file_name`/`name`/`path`). Pure. */
fun argsFileName(argsJson: String): String {
    for (key in listOf("fileName", "file_name", "name", "path")) {
        Regex(""""$key"\s*:\s*"([^"]+)"""").find(argsJson)?.let {
            val name = it.groupValues[1].trim().substringAfterLast('/').trim()
            if (name.isNotEmpty()) return name.take(60)
        }
    }
    return ""
}

/** `snake_case` tool id to Title Case (`list_files` → `List Files`). Pure. */
fun humanizeTool(toolName: String): String =
    toolName.trim().trimEnd('…').split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { "Tool" }

/**
 * Friendly live action for a tool call (`Searching “tl494”…`, `Reading ti.com…`,
 * `Downloading x.pdf…`). Shown while the tool runs. Pure; JVM-testable.
 */
fun toolCallLabel(toolName: String, argsJson: String = ""): String {
    return when (toolName) {
        "web_search" -> {
            val q = argsQuery(argsJson).take(42)
            if (q.isNotBlank()) "Searching “$q”…".take(80) else "Searching…"
        }
        "image_search" -> {
            val q = argsQuery(argsJson).take(36)
            if (q.isNotBlank()) "Searching images “$q”…".take(80) else "Searching images…"
        }
        "fetch_url", "curl_fetch" -> {
            val host = hostOfUrl(argsUrl(argsJson))
            if (host.isNotBlank()) "Reading $host…" else "Reading page…"
        }
        "download_file" -> {
            val name = argsFileName(argsJson).ifBlank { hostOfUrl(argsUrl(argsJson)) }
            if (name.isNotBlank()) "Downloading $name…" else "Downloading file…"
        }
        "read_file" -> {
            val name = argsFileName(argsJson)
            if (name.isNotBlank()) "Reading $name…" else "Listing files…"
        }
        "list_files" -> "Listing files…"
        "read_skill" -> {
            val name = Regex(""""skill"\s*:\s*"([^"]+)"""").find(argsJson)?.groupValues?.get(1).orEmpty()
            if (name.isNotBlank()) "Loading skill “${name.take(32)}”…" else "Loading skill…"
        }
        "validate_netlist" -> "Validating…"
        "netlist_template" -> "Loading template…"
        "apply_netlist" -> "Applying…"
        else -> "${humanizeTool(toolName)}…"
    }
}

/** Tool kind for timeline icons. Pure. */
fun toolStepKind(text: String): ToolKind? = kindOf(text)

/**
 * Short friendly title for one SYSTEM timeline row (`Found 5 pages`,
 * `Read ti.com`, `Netlist valid`). Falls back to the raw line trimmed. Pure.
 */
fun toolStepTitle(text: String): String {
    val t = text.trim()
    val lower = t.lowercase()
    // Live call lines are already friendly — keep them, minus the ellipsis.
    if (lower.startsWith("searching") || lower.startsWith("reading") ||
        lower.startsWith("downloading") || lower.startsWith("validating") ||
        lower.startsWith("applying") || lower.startsWith("loading template") ||
        lower.startsWith("listing")
    ) return t
    if (lower.startsWith("calling ")) return thinkingTitle(t, null)
    if (lower.startsWith("contacting")) return t.take(80)
    val scope = Regex("""“([^”]{1,32})”""").find(t)?.groupValues?.get(1)?.let { " for “$it”" }.orEmpty()
    Regex("""found (\d+) pages?""").find(lower)?.let { return "Found ${it.groupValues[1]} pages$scope" }
    Regex("""found (\d+) images?""").find(lower)?.let { return "Found ${it.groupValues[1]} images$scope" }
    if (lower.startsWith("fetch_url: failed") || lower.startsWith("curl_fetch: failed")) {
        val url = Regex("""failed\s+(https?://[^\s—]+)""").find(t)?.groupValues?.get(1).orEmpty()
        val host = hostOfUrl(url)
        return if (host.isNotBlank()) "Couldn't read $host" else "Read failed"
    }
    if (lower.startsWith("fetch_url: read") || lower.startsWith("curl_fetch: read")) {
        val url = Regex("""read\s+(https?://[^\s—]+)""").find(t)?.groupValues?.get(1).orEmpty()
        val host = hostOfUrl(url)
        return if (host.isNotBlank()) "Read $host" else "Read page"
    }
    if (lower.startsWith("download_file:")) {
        val saved = Regex("""saved (\S+)""").find(t)?.groupValues?.get(1)
        return if (!saved.isNullOrBlank()) "Saved $saved" else "Saved file"
    }
    if (lower.startsWith("read_file:")) {
        return if ("no downloaded files" in lower) "No saved files yet" else "Read file"
    }
    if ("validate_netlist: valid" in lower || t.trim() == "VALID") return "Netlist valid"
    if ("invalid" in lower) return "Validation issues"
    if (lower.startsWith("apply_netlist:")) return "Applied to editor"
    if (lower.startsWith("netlist_template:") || "template" in lower.take(30)) return "Loaded template"
    return t.replace(Regex("\\s+"), " ").take(80)
}

private fun snippetOf(output: String, maxChars: Int = 220): String {
    val oneLine = output.replace(Regex("\\s+"), " ").trim()
    return oneLine.take(maxChars)
}

/**
 * SYSTEM line for one tool observation. Prefixes carry counts/hosts/URLs so
 * the thinking summary survives the 220-char UI truncation. Pure.
 */
fun summarizeObservation(toolName: String, output: String, argsJson: String = ""): String {
    val clean = output.trim()
    if (clean.startsWith("ERROR") || clean.startsWith("INVALID")) {
        val url = argsUrl(argsJson)
        val detail = snippetOf(clean.removePrefix("ERROR:").removePrefix("INVALID:").trim())
        return if (url.isNotBlank() && (toolName == "fetch_url" || toolName == "curl_fetch")) {
            "$toolName: failed $url — $detail"
        } else "$toolName: $detail"
    }
    return when (toolName) {
        "web_search" -> {
            val results = parseNumberedResults(clean).ifEmpty {
                extractUrls(clean).map { FoundPage(hostOfUrl(it), it, hostOfUrl(it)) }
                    .filter { it.host.isNotEmpty() }
            }
            val query = argsQuery(argsJson).take(48)
            val scope = if (query.isNotBlank()) " “$query”" else ""
            if (results.isEmpty()) "web_search$scope: ${snippetOf(clean)}"
            else {
                val hosts = results.map { it.host }.distinct().take(3)
                "web_search$scope: found ${results.size} pages (${hosts.joinToString(", ")}): ${snippetOf(clean)}"
            }
        }
        "image_search" -> {
            val images = parseImageResults(clean)
            val query = argsQuery(argsJson).take(42)
            val scope = if (query.isNotBlank()) " “$query”" else ""
            if (images.isEmpty()) "image_search$scope: ${snippetOf(clean)}"
            else {
                val hosts = images.map { it.host }.distinct().take(3)
                "image_search$scope: found ${images.size} images (${hosts.joinToString(", ")}): ${snippetOf(clean)}"
            }
        }
        "fetch_url", "curl_fetch" -> {
            val url = argsUrl(argsJson).ifBlank { extractUrls(clean).firstOrNull().orEmpty() }
            if (url.isBlank()) "$toolName: ${snippetOf(clean)}"
            else "$toolName: read $url — ${snippetOf(clean)}"
        }
        else -> if (clean == "VALID") "$toolName: VALID" else "$toolName: ${snippetOf(clean)}"
    }
}

/** Parse grouped search activity from SYSTEM steps. Pure. */
fun parseSearchSummary(steps: List<ChatMsg>): SearchSummary {
    var foundTotal = 0
    val foundHosts = mutableListOf<String>()
    val samples = mutableListOf<FoundPage>()
    val reads = mutableListOf<ReadPage>()
    var imagesTotal = 0
    val imageHosts = mutableListOf<String>()
    for (step in steps) {
        val text = step.text.trim()
        val lower = text.lowercase()
        when {
            lower.startsWith("web_search") -> {
                Regex("""found (\d+) pages?""").find(lower)?.groupValues?.get(1)
                    ?.toIntOrNull()?.let { foundTotal += it }
                Regex("""\(([^)]+)\)""").find(text)?.groupValues?.get(1)
                    ?.split(',')?.map { it.trim().lowercase().removePrefix("www.") }
                    ?.filter { it.isNotEmpty() && '.' in it }?.let { hosts ->
                        for (h in hosts) if (h !in foundHosts) foundHosts.add(h)
                    }
                for (p in parseNumberedResults(text)) {
                    if (samples.none { it.url == p.url }) samples.add(p)
                    if (p.host !in foundHosts) foundHosts.add(p.host)
                }
                if (foundTotal == 0) {
                    // Legacy unprefixed lines: count URLs directly.
                    val urls = extractUrls(text)
                    foundTotal += urls.size
                    for (u in urls) {
                        val h = hostOfUrl(u)
                        if (h.isNotEmpty() && h !in foundHosts) foundHosts.add(h)
                    }
                }
            }
            lower.startsWith("image_search") -> {
                Regex("""found (\d+) images?""").find(lower)?.groupValues?.get(1)
                    ?.toIntOrNull()?.let { imagesTotal += it }
                for (p in parseImageResults(text)) {
                    if (p.host !in imageHosts) imageHosts.add(p.host)
                }
                if (imagesTotal == 0 && "no images found" !in lower) {
                    val n = extractUrls(text).size
                    imagesTotal += n
                }
            }
            lower.startsWith("fetch_url:") || lower.startsWith("curl_fetch:") -> {
                val ok = !("failed" in lower.take(60) || lower.contains("error:"))
                val url = Regex("""(?:read|failed)\s+(https?://[^\s—]+)""").find(text)
                    ?.groupValues?.get(1)?.let(::cleanUrl)
                    ?: extractUrls(text).firstOrNull().orEmpty()
                if (url.isNotBlank()) {
                    val host = hostOfUrl(url)
                    if (reads.none { it.url == url }) reads.add(ReadPage(url, host, ok))
                } else if (reads.isEmpty() || ok) {
                    // Unparseable but successful read still counts without an icon.
                    reads.add(ReadPage("", "", ok))
                }
            }
        }
    }
    return SearchSummary(
        foundTotal = foundTotal,
        foundHosts = foundHosts.take(8),
        foundSamples = samples.take(8),
        readPages = reads,
        imagesTotal = imagesTotal,
        imageHosts = imageHosts.take(8)
    )
}

private fun plural(n: Int, one: String, many: String): String =
    if (n == 1) "1 $one" else "$n $many"

/** Compact summary title (`Found 20 pages • Read 4 pages`), or null when no search ran. Pure. */
fun searchSummaryTitle(summary: SearchSummary): String? {
    if (!summary.hasSearch) return null
    val parts = mutableListOf<String>()
    if (summary.foundTotal > 0) parts.add("Found ${plural(summary.foundTotal, "page", "pages")}")
    if (summary.readOk.isNotEmpty()) parts.add("Read ${plural(summary.readOk.size, "page", "pages")}")
    if (summary.imagesTotal > 0) parts.add(plural(summary.imagesTotal, "image", "images"))
    return parts.ifEmpty { null }?.joinToString(" • ")
}

/** Favicon for a host via Google S2 (Coil loads it; initial letter is the fallback). Pure. */
fun faviconUrl(host: String): String =
    "https://www.google.com/s2/favicons?domain=${host.trim().lowercase()}&sz=64"

/** Supporting detail for a timeline row (text after the `—`/`:` prefix). Pure. */
fun toolStepDetail(text: String, maxChars: Int = 140): String {
    val t = text.trim()
    val afterDash = t.substringAfter("—", "").trim()
    if (afterDash.isNotEmpty()) return afterDash.take(maxChars)
    val lower = t.lowercase()
    for (prefix in listOf("web_search", "image_search", "fetch_url", "curl_fetch", "download_file", "read_file")) {
        if (lower.startsWith(prefix)) {
            val rest = t.substringAfter(":", "").trim()
            // Drop the machine prefix (`found N pages (…)`, `read <url>`) when a
            // human snippet follows a second separator.
            val second = rest.substringAfter(": ", "").trim()
            val detail = if (second.isNotEmpty() && second != rest) second else rest
            return detail.take(maxChars)
        }
    }
    return ""
}

/**
 * True when a finished ASSISTANT bubble is an error notice, not an answer,
 * so the chat body can render it in the error tone (red) instead of the
 * normal black text. Matches only the app's own guard/error templates —
 * never generic words like "error", so a normal SPICE explanation that
 * mentions errors stays black. Pure.
 */
fun isChatError(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    val lower = t.lowercase()
    if (t.startsWith("ERROR") || t.startsWith("INVALID")) return true
    return when {
        lower.startsWith("no model selected yet") -> true
        lower.startsWith("this model needs your") -> true
        lower.startsWith("add your ") && "api key in settings first" in lower -> true
        lower.startsWith("you're offline") || lower.startsWith("you are offline") -> true
        lower.startsWith("no connection.") -> true
        lower.startsWith("free-tier limit reached.") -> true
        lower.startsWith("session handshake failed.") -> true
        lower.startsWith("the provider is having issues") -> true
        lower.startsWith("openai error:") -> true
        lower.startsWith("anthropic error:") -> true
        lower.startsWith("gateway error:") -> true
        "credit balance" in lower -> true
        "invalid x-api-key" in lower -> true
        "overloaded" in lower -> true
        // Raw provider error bodies (no "OpenAI error:" prefix — e.g. quota
        // JSON surfaced via AgentErrors.extractMessage).
        "no credits remaining" in lower -> true
        "insufficient credits" in lower -> true
        "current quota" in lower -> true
        "insufficient_quota" in lower || "insufficient quota" in lower -> true
        "credit_balance" in lower -> true
        "add credits" in lower -> true
        "rate limit" in lower || "ratelimit" in lower || "too many requests" in lower -> true
        "invalid api key" in lower || "incorrect api key" in lower || "invalid_api_key" in lower -> true
        "unauthorized" in lower || "authentication failed" in lower -> true
        "model is unavailable" in lower || "model_not_found" in lower -> true
        "context_length_exceeded" in lower || "maximum context length" in lower -> true
        "failed to connect" in lower || "unable to resolve host" in lower || "timed out" in lower -> true
        lower.startsWith("provider returned no models.") -> true
        lower.startsWith("saved model is no longer offered") -> true
        "failed to answer. retry" in lower -> true
        "is temporarily unavailable on the free tier" in lower -> true
        "needs your api key (settings" in lower -> true
        "isn't supported on this route" in lower -> true
        "pick another free model" in lower -> true
        "pick a free model" in lower -> true
        "pick another model from the menu above" in lower -> true
        "pick a new one" in lower -> true
        else -> false
    }
}

/** True when a SYSTEM line reports a failure (red timeline tone). Pure. */
fun isErrorStep(text: String): Boolean {
    val t = text.trim()
    if (t.startsWith("ERROR") || t.startsWith("INVALID")) return true
    val lower = t.lowercase()
    return when {
        lower.startsWith("fetch_url: failed") || lower.startsWith("curl_fetch: failed") -> true
        lower.startsWith("couldn't read") || lower.startsWith("read failed") -> true
        lower.startsWith("validation issues") || "invalid" in lower -> true
        "provider error" in lower || "final-answer error" in lower -> true
        else -> false
    }
}

/** One page belonging to a host for the host dialog. Pure. */
data class HostPage(val title: String, val url: String)

/** Found + read pages for one host, deduped by URL. Pure. */
fun pagesForHost(summary: SearchSummary, host: String): List<HostPage> {
    val clean = host.lowercase().removePrefix("www.")
    if (clean.isEmpty()) return emptyList()
    val out = mutableListOf<HostPage>()
    for (p in summary.foundSamples) {
        if (p.host == clean && out.none { it.url == p.url }) {
            out.add(HostPage(p.title.ifBlank { p.url }, p.url))
        }
    }
    for (r in summary.readOk) {
        if (r.host == clean && r.url.isNotBlank() && out.none { it.url == r.url }) {
            out.add(HostPage(r.url, r.url))
        }
    }
    return out
}

/**
 * Dynamic thinking title. While a turn runs, the live [status] (e.g.
 * "Calling validate_netlist…") maps to a friendly verb ("Validating…");
 * once it clears, the last step maps to past tense ("Validated").
 * Unrecognized text (provider names, errors) passes through trimmed.
 */
fun thinkingTitle(status: String?, lastStep: String?): String {
    if (!status.isNullOrBlank()) {
        val s = status.trim()
        if ("contacting" in s.lowercase()) return s
        return when (kindOf(s)) {
            ToolKind.VALIDATE -> "Validating…"
            ToolKind.APPLY -> "Applying…"
            ToolKind.RUN -> "Running…"
            ToolKind.GENERATE -> "Generating…"
            ToolKind.SEARCH -> "Searching…"
            ToolKind.READ -> "Reading…"
            ToolKind.DOWNLOAD -> "Downloading…"
            null -> s.take(80)
        }
    }
    if (!lastStep.isNullOrBlank()) {
        return when (kindOf(lastStep)) {
            ToolKind.VALIDATE -> "Validated"
            ToolKind.APPLY -> "Applied"
            ToolKind.RUN -> "Ran"
            ToolKind.GENERATE -> "Generated"
            ToolKind.SEARCH -> "Searched"
            ToolKind.READ -> "Read"
            ToolKind.DOWNLOAD -> "Downloaded"
            null -> "Thought process"
        }
    }
    return "Thinking…"
}

/** Collapsed title for a finished answer: search turns show `Found N • Read M`, else `Action • N steps`. */
fun pastThinkingTitle(steps: List<ChatMsg>): String {
    searchSummaryTitle(parseSearchSummary(steps))?.let { return it }
    val base = thinkingTitle(null, steps.lastOrNull()?.text)
    return "$base • ${steps.size} steps"
}

/**
 * Live title while a turn runs: searching/reading turns show progress
 * (`Searching… • Found 12 • Read 3`), other tools keep their verb.
 */
fun liveThinkingTitle(status: String?, steps: List<ChatMsg>): String {
    val summary = parseSearchSummary(steps)
    val summaryTitle = searchSummaryTitle(summary)
    if (!status.isNullOrBlank()) {
        val s = status.trim()
        if ("contacting" in s.lowercase()) return s
        return when (kindOf(s)) {
            ToolKind.VALIDATE -> "Validating…"
            ToolKind.APPLY -> "Applying…"
            ToolKind.RUN -> "Running…"
            ToolKind.GENERATE -> "Generating…"
            ToolKind.DOWNLOAD -> if (summaryTitle != null) "Downloading… • $summaryTitle" else "Downloading…"
            ToolKind.SEARCH, ToolKind.READ ->
                if (summaryTitle != null) "${thinkingTitle(s, null)} • $summaryTitle"
                else thinkingTitle(s, null)
            null -> if (summaryTitle != null && ("calling" in s.lowercase())) summaryTitle else s.take(80)
        }
    }
    if (summaryTitle != null) return summaryTitle
    return thinkingTitle(null, steps.lastOrNull()?.text)
}
