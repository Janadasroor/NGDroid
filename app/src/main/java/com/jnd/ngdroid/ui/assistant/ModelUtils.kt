package com.jnd.ngdroid.ui.assistant

/**
 * Sort a live model catalog: de-duplicated, trimmed, alphabetical.
 * The catalog comes from the provider's listModels() — nothing is hardcoded.
 * Pure + JVM-testable.
 */
fun rankModels(models: List<String>): List<String> =
    models.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { it.lowercase() }

/** Case-insensitive substring filter for the models search box. Pure + JVM-testable. */
fun filterModels(models: List<String>, query: String): List<String> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return models
    return models.filter { it.lowercase().contains(q) }
}

/**
 * Rank a catalog with a free-tier subset first (each alphabetical), then the rest.
 * [freeIds] is computed live from the catalog (e.g. Zen `-free` suffix) — never hardcoded.
 * Pure + JVM-testable.
 */
fun rankModelsFreeFirst(models: List<String>, freeIds: Set<String>): List<String> {
    val distinct = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (distinct.isEmpty()) return emptyList()
    val free = distinct.filter { it in freeIds }.sortedBy { it.lowercase() }
    val rest = (distinct - free.toSet()).sortedBy { it.lowercase() }
    return free + rest
}

/** Model tier filter for the professional picker. Pure. */
enum class ModelTierFilter { ALL, FREE, KEYED }

/**
 * Short family label derived from the model id (`muse-spark-*` → Spark,
 * `gpt-*` → GPT, `grok-*` → Grok, `gemini-*` → Gemini, `nemotron-*` → Nemotron…).
 * Falls back to the vendor prefix before `-`, else "Other". Pure.
 */
fun modelFamily(id: String): String {
    val low = id.trim().lowercase()
    if (low.isEmpty()) return "Other"
    if (low.startsWith("muse-spark-")) return "Spark"
    if (low.startsWith("gpt-")) return "GPT"
    if (low.startsWith("grok-")) return "Grok"
    if (low.startsWith("gemini-")) return "Gemini"
    if (low.startsWith("nemotron-")) return "Nemotron"
    if (low.startsWith("deepseek-")) return "DeepSeek"
    if (low.startsWith("ling-")) return "Ling"
    if (low.startsWith("mimo-")) return "MiMo"
    val head = low.substringBefore("-").substringBefore("/").trim()
    if (head.isEmpty()) return "Other"
    return head.replaceFirstChar { it.uppercase() }
}

/**
 * Professional catalog search engine: multi-token AND match over id + family,
 * tier filter (ALL/FREE/KEYED), free-tier ids ranked first. Pure + JVM-testable.
 */
fun searchModelCatalog(
    models: List<String>,
    query: String,
    tier: ModelTierFilter = ModelTierFilter.ALL,
    freeIds: Set<String> = emptySet()
): List<String> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    var list = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    list = when (tier) {
        ModelTierFilter.ALL -> list
        ModelTierFilter.FREE -> list.filter { it in freeIds }
        ModelTierFilter.KEYED -> list.filter { it !in freeIds }
    }
    if (tokens.isNotEmpty()) {
        list = list.filter { id ->
            val hay = "${id.lowercase()} ${modelFamily(id).lowercase()}"
            tokens.all { it in hay }
        }
    }
    val free = list.filter { it in freeIds }.sortedBy { it.lowercase() }
    val rest = (list - free.toSet()).sortedBy { it.lowercase() }
    return free + rest
}
