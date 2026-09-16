package com.jnd.ngdroid.data

import com.jnd.ngdroid.agent.SkillEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** User-created skill: SKILL.md-style (name + description for discovery, body for activation). */
data class CustomSkill(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String,
    val description: String = "",
    override val instructions: String,
    override val enabled: Boolean = true
) : SkillEntry {
    /** Filesystem-safe skill dir name: kebab-case, max 64 chars (SKILL.md standard). */
    val slug: String get() = skillSlug(name)
}

/** Built-in skill toggle: maps 1:1 to an agent tool id. */
data class BuiltinSkill(
    val id: String,
    val title: String,
    val blurb: String,
    val group: String
)

val BUILTIN_SKILLS: List<BuiltinSkill> = listOf(
    BuiltinSkill("validate_netlist", "Validate netlist", "SPICE syntax check before answers", "SPICE"),
    BuiltinSkill("netlist_template", "Netlist templates", "Starter RC/RLC/diode/BJT/opamp circuits", "SPICE"),
    BuiltinSkill("apply_netlist", "Apply to editor", "Send results to the editor/simulator", "SPICE"),
    BuiltinSkill("run_simulation", "Run simulation", "Apply, run and return status/logs/vectors", "SPICE"),
    BuiltinSkill("web_search", "Web search", "Current facts via Brave or DuckDuckGo", "Web"),
    BuiltinSkill("fetch_url", "Read pages", "Fetch and summarize web pages", "Web"),
    BuiltinSkill("curl_fetch", "Curl fetch", "GET-only curl-style URL reads", "Web"),
    BuiltinSkill("image_search", "Image search", "Pinouts, schematics, photos", "Web"),
    BuiltinSkill("download_file", "Download files", "Save PDFs/images/CSV/ZIP to Downloads", "Files"),
    BuiltinSkill("read_file", "Read files", "Inspect saved docs, text and images", "Files"),
    BuiltinSkill("render_plot", "See plot image", "Shape check: clipping, ringing, phase (numbers still from text)", "SPICE")
)

val BUILTIN_SKILL_IDS: Set<String> = BUILTIN_SKILLS.map { it.id }.toSet()

/** kebab-case dir name: lowercase letters/numbers/hyphens, max 64. Pure. */
fun skillSlug(name: String): String = com.jnd.ngdroid.agent.skillSlug(name)

/** Error message when invalid, null when the skill is usable. Pure. */
fun validateCustomSkill(name: String, description: String, instructions: String): String? {
    if (skillSlug(name).length < 2) return "Give the skill a short name (2+ chars)."
    if (name.trim().length > 40) return "Keep the name under 40 characters."
    if (description.trim().length < 10) return "Add a description (10+ chars) so the agent knows when to use it."
    if (description.trim().length > 500) return "Keep the description under 500 characters."
    if (instructions.trim().length < 10) return "Add instructions (10+ chars) so the agent knows what to do."
    if (instructions.trim().length > 4000) return "Keep instructions under 4000 characters."
    return null
}

/** Back-compat overload: description derived from instructions head. */
fun validateCustomSkill(name: String, instructions: String): String? =
    validateCustomSkill(name, instructions.trim().take(200), instructions)

fun newCustomSkill(name: String, description: String, instructions: String): CustomSkill = CustomSkill(
    id = UUID.randomUUID().toString(),
    name = name.trim().take(40),
    description = description.trim().take(500).ifBlank { instructions.trim().take(200) },
    instructions = instructions.trim().take(4000),
    enabled = true
)

fun newCustomSkill(name: String, instructions: String): CustomSkill =
    newCustomSkill(name, instructions.trim().take(200), instructions)

private val skillJson = Json { ignoreUnknownKeys = true }

object CustomSkillCodec {
    fun encode(skills: List<CustomSkill>): String {
        val arr = kotlinx.serialization.json.buildJsonArray {
            skills.take(50).forEach { s ->
                add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("id", JsonPrimitive(s.id.take(64)))
                        put("name", JsonPrimitive(s.name.take(40)))
                        put("description", JsonPrimitive(s.description.take(500)))
                        put("instructions", JsonPrimitive(s.instructions.take(4000)))
                        put("enabled", JsonPrimitive(s.enabled))
                    }
                )
            }
        }
        return arr.toString()
    }

    fun decode(raw: String): List<CustomSkill> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val el = skillJson.parseToJsonElement(raw)
            val arr: JsonArray = when {
                el is JsonArray -> el
                el is JsonObject && el["skills"] is JsonArray -> el["skills"]!!.jsonArray
                else -> return emptyList()
            }
            arr.take(50).mapNotNull { item ->
                runCatching {
                    val o = item.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val instructions = o["instructions"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (name.isEmpty() || instructions.isEmpty()) return@mapNotNull null
                    val desc = o["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        .ifBlank { instructions.take(200) }
                    CustomSkill(
                        id = o["id"]?.jsonPrimitive?.contentOrNull?.take(64)
                            ?.ifBlank { UUID.randomUUID().toString() }
                            ?: UUID.randomUUID().toString(),
                        name = name.take(40),
                        description = desc.take(500),
                        instructions = instructions.take(4000),
                        enabled = o["enabled"]?.jsonPrimitive?.contentOrNull
                            ?.let { it == "true" } ?: true
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * SKILL.md text for one skill (agentskills.io style): YAML frontmatter
 * (name + description for discovery) + markdown body (loaded on activation).
 * Pure; used for export / copy-paste interop.
 */
fun CustomSkill.toSkillMarkdown(): String = buildString {
    append("---\n")
    append("name: ${slug}\n")
    append("description: ${description.trim().replace("\n", " ").take(500)}\n")
    append("---\n\n")
    append("# ${name.trim().take(40)}\n\n")
    append(instructions.trim().take(4000))
    if (!instructions.trim().endsWith("\n")) append("\n")
}

/**
 * Draft preview for the skill editor: same SKILL.md shape as [toSkillMarkdown]
 * but tolerant of blank fields so Write → Preview works mid-typing. Pure.
 */
fun previewSkillMarkdown(name: String, description: String, instructions: String): String {
    val title = name.trim().take(40).ifBlank { "Untitled skill" }
    val slug = skillSlug(name.ifBlank { "skill" })
    val desc = description.trim().replace("\n", " ").take(500)
        .ifBlank { "No description yet — add 10+ chars so the agent knows when to use it." }
    val body = instructions.trim().take(4000)
        .ifBlank { "_No instructions yet — describe the steps the agent follows once the skill loads._" }
    return buildString {
        append("---\n")
        append("name: $slug\n")
        append("description: $desc\n")
        append("---\n\n")
        append("# $title\n\n")
        append(body)
        if (!body.endsWith("\n")) append("\n")
    }
}

/** Parse pasted SKILL.md back into name/description/body. Pure; null when unusable. */
fun parseSkillMarkdown(md: String): Triple<String, String, String>? {
    val text = md.trim()
    if (text.isEmpty()) return null
    val fm = Regex("""^---\s*\n(.*?)\n---\s*\n?(.*)$""", RegexOption.DOT_MATCHES_ALL).find(text)
    var name = ""
    var description = ""
    var body: String
    if (fm != null) {
        val head = fm.groupValues[1]
        body = fm.groupValues[2].trim()
        name = Regex("""(?m)^name\s*:\s*(.+)$""").find(head)?.groupValues?.get(1)?.trim().orEmpty()
        description = Regex("""(?m)^description\s*:\s*(.+)$""").find(head)?.groupValues?.get(1)?.trim().orEmpty()
    } else {
        body = text
    }
    if (body.isEmpty()) return null
    if (name.isEmpty()) {
        name = Regex("""(?m)^#\s+(.+)$""").find(body)?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isNotEmpty()) body = body.replaceFirst(Regex("""(?m)^#\s+.+$"""), "").trim()
    }
    if (name.isEmpty()) name = body.lines().firstOrNull()?.take(40).orEmpty()
    if (description.isEmpty()) description = body.take(200)
    if (validateCustomSkill(name, description, body) != null) return null
    return Triple(name.take(40), description.take(500), body.take(4000))
}

/**
 * Stage 1 Discovery catalog: base prompt + name/description list only
 * (~30 tokens/skill). Full bodies load via the read_skill tool (Stage 2).
 * Pure; JVM-testable.
 */
fun skillCatalogPrompt(base: String, customs: List<CustomSkill>): String {
    val active = customs.filter { it.enabled && it.instructions.isNotBlank() }.take(20)
    if (active.isEmpty()) return base
    return buildString {
        append(base)
        append("\n\nSkills available (progressive disclosure): name + description below. ")
        append("Call read_skill {\"skill\": \"<name>\"} to load the full SKILL.md body when the task matches a description; otherwise ignore the skill.")
        active.forEach { s ->
            append("\n- ${s.name.trim().take(40)} (${s.slug}): ${s.description.trim().replace("\n", " ").take(200)}")
        }
    }
}

/** Legacy full-injection prompt (kept for tests/back-compat). Prefer [skillCatalogPrompt]. */
fun effectiveSystemPrompt(base: String, customs: List<CustomSkill>): String {
    val active = customs.filter { it.enabled && it.instructions.isNotBlank() }.take(20)
    if (active.isEmpty()) return base
    return buildString {
        append(base)
        append("\n\nCustom skills (follow enabled ones):")
        active.forEach { s ->
            append("\n- ${s.name.trim().take(40)}: ${s.instructions.trim().take(4000)}")
        }
    }
}
