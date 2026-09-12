package com.jnd.ngdroid.agent

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

object SpiceValidator {
    private val validLeadChars = "RCLVDIQMXTBGHEFJKSOWUYZAB".toSet()
    private val knownDirectives = setOf(
        ".tran", ".ac", ".dc", ".op", ".model", ".subckt", ".ends", ".end",
        ".param", ".include", ".lib", ".ic", ".nodeset", ".options", ".temp",
        ".plot", ".print", ".probe", ".save", ".four", ".noise", ".tf", ".sens",
        ".control", ".endc"
    )
    private const val MAX_LINES = 2000

    fun validate(netlist: String): ValidationResult {
        val errors = mutableListOf<String>()
        if (netlist.isBlank()) {
            return ValidationResult(false, listOf("Netlist is blank"))
        }
        val lines = netlist.lines()
        if (lines.size > MAX_LINES) {
            errors.add("Netlist exceeds $MAX_LINES lines (${lines.size})")
        }
        val nonBlank = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (nonBlank.isEmpty()) {
            return ValidationResult(false, listOf("Netlist contains only empty lines"))
        }
        val first = nonBlank.first()
        if (!first.startsWith("*")) {
            errors.add("First non-blank line must be a comment starting with '*'")
        }
        val hasEnd = nonBlank.any { it.lowercase() == ".end" || it.lowercase().startsWith(".end ") }
        if (!hasEnd) {
            errors.add("Missing '.end' terminator")
        }
        for ((idx, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("*")) continue
            if (line.startsWith(".")) {
                val directive = line.lowercase().split(Regex("\\s+")).first()
                if (directive !in knownDirectives) {
                    errors.add("Line ${idx + 1}: unknown directive '$directive' (did you mean .tran/.ac/.dc/.op/.model/.subckt/.ends/.end?)")
                }
                continue
            }
            if (line.startsWith("+")) continue
            val lead = line.first().uppercaseChar()
            if (lead !in validLeadChars) {
                errors.add("Line ${idx + 1}: unknown component lead character '$lead'")
            }
        }
        return ValidationResult(errors.isEmpty(), errors)
    }
}
