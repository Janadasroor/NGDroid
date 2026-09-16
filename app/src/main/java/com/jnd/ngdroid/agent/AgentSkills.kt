package com.jnd.ngdroid.agent

/**
 * Minimal skill shape the agent layer understands. Defined here (not in
 * `data`) so the dependency points the right way: `data -> agent`.
 * `data.CustomSkill` implements this interface.
 */
interface SkillEntry {
    val id: String
    val name: String
    val instructions: String
    val enabled: Boolean
}

/** kebab-case dir name: lowercase letters/numbers/hyphens, max 64. Pure. */
fun skillSlug(name: String): String {
    val slug = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(64).trim('-')
    return slug.ifBlank { "skill" }
}
