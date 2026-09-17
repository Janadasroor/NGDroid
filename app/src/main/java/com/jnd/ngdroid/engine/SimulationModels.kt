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

package com.jnd.ngdroid.engine

data class VectorSeries(
    val name: String,
    val isScale: Boolean = false,
    val values: List<Double> = emptyList(),
    /** Override auto V/A detection (used by math traces). Null = auto. */
    val forceCurrent: Boolean? = null
) {
    val isCurrent: Boolean
        get() = forceCurrent ?: (name.endsWith("#branch", ignoreCase = true) ||
                name.startsWith("i(", ignoreCase = true) ||
                name.startsWith("I(", ignoreCase = true))
}

data class SimulationPlot(
    val title: String = "",
    val plotName: String = "",
    val plotType: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val scaleVector: VectorSeries? = null,
    val dataVectors: List<VectorSeries> = emptyList()
)

data class SimulationState(
    val isSimulating: Boolean = false,
    val isPaused: Boolean = false,
    val statusText: String = "Idle",
    val progressFraction: Float? = null,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val currentPlot: SimulationPlot? = null,
    val activeVectors: Set<String> = emptySet(),
    val logs: List<String> = emptyList(),
    val totalPointCount: Int = 0,
    val plotHistory: List<SimulationPlot> = emptyList()
)

data class PresetNetlist(
    val id: String,
    val title: String,
    val description: String,
    val netlist: String,
    val category: String = "General"
)
