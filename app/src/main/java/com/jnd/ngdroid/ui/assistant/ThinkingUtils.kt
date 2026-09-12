package com.jnd.ngdroid.ui.assistant

/**
 * Pure thinking-row helpers. No Android imports — JVM-testable.
 *
 * Tool progress is stored as [ChatRoleUi.SYSTEM] messages inline in the
 * transcript. The chat body hides them; they surface inside thinking
 * expanders instead — live while a turn runs, and per-answer afterwards.
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

private enum class ToolKind { VALIDATE, APPLY, RUN, GENERATE }

private fun kindOf(text: String): ToolKind? {
    val s = text.lowercase()
    return when {
        "validat" in s -> ToolKind.VALIDATE
        "apply" in s -> ToolKind.APPLY
        "run" in s || "simulat" in s -> ToolKind.RUN
        "generat" in s || "template" in s -> ToolKind.GENERATE
        else -> null
    }
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
            null -> s.take(80)
        }
    }
    if (!lastStep.isNullOrBlank()) {
        return when (kindOf(lastStep)) {
            ToolKind.VALIDATE -> "Validated"
            ToolKind.APPLY -> "Applied"
            ToolKind.RUN -> "Ran"
            ToolKind.GENERATE -> "Generated"
            null -> "Thought process"
        }
    }
    return "Thinking…"
}

/** Collapsed title for a finished answer, e.g. "Validated • 4 steps". */
fun pastThinkingTitle(steps: List<ChatMsg>): String {
    val base = thinkingTitle(null, steps.lastOrNull()?.text)
    return "$base • ${steps.size} steps"
}
