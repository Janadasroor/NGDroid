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
