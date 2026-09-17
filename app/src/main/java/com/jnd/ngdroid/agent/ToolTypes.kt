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

/** Rich tool result: text observation plus optional vision images. */
data class ToolResult(
    val text: String,
    val images: List<LlmImage> = emptyList()
)

interface AgentTool {
    val name: String
    val description: String
    val parametersJsonSchema: String
    suspend fun execute(argsJson: String): String
    /** Rich result (text + optional vision images). Default wraps [execute]. */
    suspend fun executeEx(argsJson: String): ToolResult =
        ToolResult(execute(argsJson))
}

interface ToolRegistry {
    fun register(tool: AgentTool)
    fun list(): List<AgentTool>
    fun get(name: String): AgentTool?
}

class MapToolRegistry : ToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()

    override fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    override fun list(): List<AgentTool> = tools.values.toList()

    override fun get(name: String): AgentTool? = tools[name]
}
