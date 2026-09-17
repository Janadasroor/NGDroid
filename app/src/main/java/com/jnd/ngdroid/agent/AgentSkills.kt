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
