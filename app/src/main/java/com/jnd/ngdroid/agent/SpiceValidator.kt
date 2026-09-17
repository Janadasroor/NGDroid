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
        // Structural state for complex netlists: subcircuits, models, instance names.
        val subcktNames = mutableSetOf<String>()
        val modelNames = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()
        var openSubckts = 0
        // Pre-scan: definitions may come after use (SPICE allows forward
        // references), so collect every .subckt/.model name first.
        for (raw in lines) {
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.isEmpty() || !parts.first().startsWith(".")) continue
            when (parts.first().lowercase()) {
                ".subckt" -> if (parts.size >= 3) subcktNames.add(parts[1].lowercase())
                ".model" -> if (parts.size >= 2) modelNames.add(parts[1].lowercase())
            }
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
                if (directive == ".control") {
                    errors.add("Line ${idx + 1}: '.control/.endc' blocks are not supported on Android (sharedspice mode strips them) — remove the block and keep plain analyses (.tran/.ac/.dc/.op)")
                }
                if (directive == ".subckt") {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 3) {
                        errors.add("Line ${idx + 1}: '.subckt' needs a name and at least one node (e.g. '.subckt INV vdd vss in out')")
                    } else {
                        subcktNames.add(parts[1].lowercase())
                        openSubckts++
                    }
                }
                if (directive == ".ends") {
                    if (openSubckts <= 0) {
                        errors.add("Line ${idx + 1}: '.ends' without a matching '.subckt'")
                    } else {
                        openSubckts--
                    }
                }
                if (directive == ".model") {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2) modelNames.add(parts[1].lowercase())
                }
                continue
            }
            if (line.startsWith("+")) continue
            val lead = line.first().uppercaseChar()
            if (lead !in validLeadChars) {
                errors.add("Line ${idx + 1}: unknown component lead character '$lead'")
                continue
            }
            val name = line.split(Regex("\\s+")).first()
            val key = name.lowercase()
            if (!seenNames.add(key)) {
                errors.add("Line ${idx + 1}: duplicate instance name '$name' (rename one of them)")
                continue
            }
            val tokens = line.split(Regex("\\s+"))
            when (lead) {
                // X<name> nodes... <subckt>: subcircuit must be defined in this file.
                'X' -> {
                    val ref = tokens.lastOrNull().orEmpty()
                    if (ref.isNotEmpty() && ref.lowercase() !in subcktNames) {
                        errors.add("Line ${idx + 1}: X-device '$name' references undefined subcircuit '$ref' (add a matching '.subckt $ref ...' block)")
                    }
                }
                // Q/M/J need an explicit .model; D falls back to the built-in default.
                'Q', 'M', 'J' -> {
                    val ref = tokens.lastOrNull().orEmpty()
                    if (ref.isNotEmpty() && ref.lowercase() !in modelNames) {
                        errors.add("Line ${idx + 1}: '$name' references undefined model '$ref' (add a matching '.model $ref ...' line)")
                    }
                }
                // A (XSPICE code model) needs its .model definition too.
                'A' -> {
                    val ref = tokens.lastOrNull().orEmpty()
                    if (ref.isNotEmpty() && ref.lowercase() !in modelNames) {
                        errors.add("Line ${idx + 1}: A-device '$name' references undefined code model '$ref' (add '.model $ref <codemodel>(...)')")
                    }
                }
            }
        }
        if (openSubckts > 0) {
            errors.add("Unbalanced '.subckt': $openSubckts block(s) never closed with '.ends'")
        }
        return ValidationResult(errors.isEmpty(), errors)
    }
}
