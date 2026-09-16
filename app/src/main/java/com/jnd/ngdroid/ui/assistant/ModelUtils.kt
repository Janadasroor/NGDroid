package com.jnd.ngdroid.ui.assistant

import com.jnd.ngdroid.data.AgentProvider
import com.jnd.ngdroid.data.AgentSettings

/**
 * Providers whose catalogs can be listed: Zen (public) + every keyed
 * provider with a saved key. Keyless providers are never fetched (their
 * /models would just 401). This is what keeps the all-providers picker
 * populated — pure; JVM-testable.
 */
fun eligibleCatalogProviders(settings: AgentSettings): List<AgentProvider> =
    AgentProvider.entries.filter {
        it == AgentProvider.OPENCODE_ZEN || settings.apiKeyFor(it).isNotBlank()
    }

/**
 * Per-provider model catalog cache: switching providers (or pasting a key)
 * must never wipe another provider's already-fetched list. The ViewModel
 * publishes from here; pure store, JVM-testable.
 */
class ProviderCatalogs {
    private val models = mutableMapOf<AgentProvider, List<String>>()
    private val free = mutableMapOf<AgentProvider, List<String>>()

    fun models(provider: AgentProvider): List<String> = models[provider].orEmpty()
    fun freeModels(provider: AgentProvider): List<String> = free[provider].orEmpty()
    fun has(provider: AgentProvider): Boolean = models.containsKey(provider)

    fun store(provider: AgentProvider, models: List<String>, freeModels: List<String>) {
        this.models[provider] = models
        this.free[provider] = freeModels
    }

    fun clear(provider: AgentProvider) {
        models.remove(provider)
        free.remove(provider)
    }
}

/**
 * True when saving [key] should trigger a catalog fetch: key present for the
 * current provider but its list is still empty (fresh paste, nothing yet
 * fetched). Prevents a fetch per keystroke once the catalog exists.
 * Pure; JVM-testable.
 */
fun shouldAutoRefreshOnKeySave(key: String, modelsEmpty: Boolean): Boolean =
    key.trim().isNotEmpty() && modelsEmpty

/** One provider's fetched catalog (already free-first ranked). Pure. */
data class ProviderCatalog(
    val provider: AgentProvider,
    val models: List<String>,
    val freeModels: List<String>
)

/** One searchable row: model id tagged with its owner provider. Pure. */
data class CatalogEntry(
    val provider: AgentProvider,
    val id: String,
    val free: Boolean
)

/**
 * Search every cached provider catalog at once, groups in [catalogs] order
 * (caller puts current provider first), rows in each provider's ranked
 * order. Tier filter applies per row. A query matching a provider's display
 * name ("zen", "gemini") selects that whole provider catalog, tier-filtered —
 * ids alone don't always match. Pure; JVM-testable.
 */
fun searchAllCatalogs(
    catalogs: List<ProviderCatalog>,
    query: String,
    tier: ModelTierFilter
): List<CatalogEntry> {
    val out = mutableListOf<CatalogEntry>()
    val q = query.trim().lowercase()
    for (c in catalogs) {
        val freeSet = c.freeModels.toSet()
        val ids = if (q.isNotEmpty() && c.provider.displayName.lowercase().contains(q)) {
            rankModelsFreeFirst(tierFilterIds(c.models, tier, freeSet), freeSet)
        } else {
            searchModelCatalog(c.models, query, tier, freeSet)
        }
        for (id in ids) out.add(CatalogEntry(c.provider, id, id in freeSet))
    }
    return out
}

/** Tier filter without ranking or text match. Pure; JVM-testable. */
fun tierFilterIds(
    ids: List<String>,
    tier: ModelTierFilter,
    freeIds: Set<String>
): List<String> {
    val distinct = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return when (tier) {
        ModelTierFilter.ALL -> distinct
        ModelTierFilter.FREE -> distinct.filter { it in freeIds }
        ModelTierFilter.KEYED -> distinct.filter { it !in freeIds }
    }
}

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
 * `gpt-*` → GPT, `grok-*` → Grok, `gemini-*` → Gemini, `claude-*` → Claude,
 * `nemotron-*` → Nemotron…).
 * Falls back to the vendor prefix before `-`, else "Other". Pure.
 */
fun modelFamily(id: String): String {
    // OpenRouter ids look like `author/slug:free` — classify by the slug.
    var low = id.trim().lowercase().removeSuffix(":free")
    if (low.isEmpty()) return "Other"
    if ("/" in low) low = low.substringAfterLast("/")
    if (low.isEmpty()) return "Other"
    if (low.startsWith("muse-spark-")) return "Spark"
    if (low.startsWith("kimi-")) return "Kimi"
    if (low.startsWith("glm-")) return "GLM"
    if (low.startsWith("qwen")) return "Qwen"
    if (low.startsWith("minimax-")) return "MiniMax"
    if (low.startsWith("longcat-")) return "LongCat"
    if (low.startsWith("claude-")) return "Claude"
    if (low.startsWith("gpt-")) return "GPT"
    if (low.startsWith("grok-")) return "Grok"
    if (low.startsWith("gemini-")) return "Gemini"
    if (low.startsWith("nemotron-")) return "Nemotron"
    if (low.startsWith("deepseek-")) return "DeepSeek"
    if (low.startsWith("ling-")) return "Ling"
    if (low.startsWith("llama")) return "Llama"
    if (low.startsWith("mistral")) return "Mistral"
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
