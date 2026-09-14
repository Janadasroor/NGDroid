package com.jnd.ngdroid.agent

import com.jnd.ngdroid.data.CustomSkill
import com.jnd.ngdroid.data.skillSlug
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val skillArgJson = Json { ignoreUnknownKeys = true }

/**
 * Stage 2 Activation: loads one SKILL.md body on demand.
 * The system prompt carries only the name+description catalog (Stage 1);
 * the model calls this when a description matches the task.
 */
class ReadSkillTool(
    private val skillsProvider: () -> List<CustomSkill>
) : AgentTool {
    override val name: String = "read_skill"
    override val description: String =
        "Load one custom skill's full SKILL.md instructions. " +
            "Input JSON: {\"skill\": \"<name or slug>\"}. " +
            "Use when the task matches a skill description from the system prompt catalog."
    override val parametersJsonSchema: String =
        """{"type":"object","properties":{"skill":{"type":"string"},"name":{"type":"string"}},"required":["skill"]}"""

    override suspend fun execute(argsJson: String): String {
        val query = runCatching {
            val o = skillArgJson.parseToJsonElement(argsJson).jsonObject
            (o["skill"]?.jsonPrimitive?.contentOrNull
                ?: o["name"]?.jsonPrimitive?.contentOrNull).orEmpty().trim()
        }.getOrDefault("").trim()
        if (query.isEmpty()) return "ERROR: pass {\"skill\": \"<name>\"}."
        val skills = skillsProvider().filter { it.enabled && it.instructions.isNotBlank() }
        if (skills.isEmpty()) return "ERROR: no custom skills are enabled."
        val q = query.lowercase()
        val hit = skills.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?: skills.firstOrNull { skillSlug(it.name) == skillSlug(query) }
            ?: skills.firstOrNull { it.id == query }
            ?: skills.firstOrNull { it.name.lowercase().contains(q) }
            ?: return "ERROR: no skill matches '$query'. Available: " +
                skills.take(20).joinToString(", ") { it.name }
        return "# ${hit.name}\n\n${hit.instructions.trim().take(4000)}"
    }
}
