package com.jnd.ngdroid.agent

/**
 * Turns raw provider failures ("HTTP 400 for https://…: {"error":…}}")
 * into short human sentences. No URLs, no JSON in user-visible text.
 * Pure — JVM-testable.
 */
object AgentErrors {

    private val messageField =
        Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Best-effort human message buried in [raw] (gateway JSON or plain text). */
    fun extractMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val m = messageField.find(raw)
        if (m != null) {
            return m.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", " ")
                .trim()
        }
        // Plain text: drop URLs and wrapper prefixes like "Provider error:" / "HTTP 400 …:".
        return raw
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("^((Provider error|Error)\\s*:\\s*)?(HTTP \\d+[^\\n:]*:\\s*)?"), "")
            .replace(Regex("[{}\"]"), "")
            .trim()
    }

    /**
     * Friendly one-to-two sentence error for [model]. Never returns blank,
     * never leaks URLs or JSON.
     */
    fun format(raw: String?, model: String): String {
        val name = model.trim().ifEmpty { "The model" }
        val msg = extractMessage(raw)
        if (msg.isEmpty()) {
            return "$name failed to answer. Retry, or pick another model from the menu above."
        }
        val low = msg.lowercase()
        return when {
            "model is unavailable" in low ->
                "$name is temporarily unavailable on the free tier. " +
                    "Open the model menu above and pick another free model."
            "rate limit" in low || "freeusagelimit" in low || "429" in low -> {
                // Keyless free models share one anonymous quota lane, which can
                // be exhausted for a single model while others (and the CLI's
                // authenticated lane) still work. Say which, and offer the key.
                val id = model.trim().lowercase()
                val anonymous = id.endsWith("-free") || id.endsWith(":free")
                if (anonymous) {
                    "The anonymous free lane for $name is exhausted right now " +
                        "(other free models may still work). Wait a bit and retry, " +
                        "pick another free model — or add your provider API key in " +
                        "Settings → AI Assistant for your own quota, same as the CLI uses."
                } else {
                    "Free-tier limit reached. Wait a minute, then retry — or pick another free model."
                }
            }
            "missing api key" in low || "autherror" in low ||
                "unauthorized" in low || "invalid api key" in low || "401" in low ->
                "This model needs your API key (Settings → AI Assistant) — or pick a FREE model instead."
            "missingsessionid" in low ->
                "Session handshake failed. Retry — if it keeps happening, reselect the model."
            "is not supported" in low || "modelerror" in low ->
                "$name isn't supported on this route. Pick another model from the menu above."
            "unable to resolve host" in low || "failed to connect" in low ||
                "timeout" in low || "network is unreachable" in low || "no address associated" in low ->
                "No connection. Check your internet and retry."
            "http 5" in low || "internal server error" in low || "bad gateway" in low ||
                "service unavailable" in low || "upstream error" in low ->
                "The provider is having issues (server error). Retry in a bit."
            // Zen gateway wraps an upstream 400 with no detail, e.g.
            // "Error from provider (Console): Upstream request failed: [400]
            // Provider returned error" — the model route flaked, not the app.
            "upstream request failed" in low || "provider returned error" in low ->
                "$name's upstream route failed this request (gateway 400). " +
                    "Tap Regenerate to retry — or pick another free model."
            // Hung SSE stream tripped the stall watchdog in HttpClients.
            "stalled" in low ->
                "The AI stream stalled mid-reply (connection went quiet). " +
                    "Tap Regenerate to retry — or pick another free model."
            else -> msg.take(200)
        }
    }
}
