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

/** Points per trace reaching Canvas Path — ~1500 lineTo fills a phone screen. Pure; JVM-testable. */
const val MAX_DRAW_POINTS = 1500

/**
 * Min-max bucket decimation of an (x, y) trace for drawing.
 *
 * Large transient runs (100k points x N traces) cost O(N*T) full scans per
 * frame for bounds plus aliased stride sampling that drops narrow spikes.
 * This keeps at most [maxPoints] pairs while preserving the envelope:
 * each bucket contributes its min and max (in index order, so x stays
 * monotonic), which also preserves exact global y bounds. Non-finite
 * samples are dropped — the draw loop breaks paths on them anyway.
 *
 * Small traces (count <= [maxPoints]) pass through untouched, including
 * non-finite samples so the caller keeps exact path-break behavior.
 */
fun decimateXY(
    x: List<Double>,
    y: List<Double>,
    maxPoints: Int = MAX_DRAW_POINTS
): List<Pair<Double, Double>> {
    val count = minOf(x.size, y.size)
    if (count <= 0 || maxPoints <= 0) return emptyList()
    if (count <= maxPoints) return List(count) { i -> x[i] to y[i] }

    val buckets = (maxPoints / 2).coerceAtLeast(1)
    val out = ArrayList<Pair<Double, Double>>(buckets * 2)
    val bucketSize = count.toDouble() / buckets
    var b = 0
    while (b < buckets) {
        val start = (b * bucketSize).toInt()
        val end = ((b + 1) * bucketSize).toInt().coerceAtMost(count)
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var minX = 0.0
        var maxX = 0.0
        var minI = -1
        var maxI = -1
        var i = start
        while (i < end) {
            val yv = y[i]
            if (yv.isFinite() && x[i].isFinite()) {
                if (yv < minY) { minY = yv; minX = x[i]; minI = i }
                if (yv > maxY) { maxY = yv; maxX = x[i]; maxI = i }
            }
            i++
        }
        // Emit in index order so the path stays monotonic in x.
        if (minI >= 0 && maxI >= 0) {
            if (minI <= maxI) {
                out.add(minX to minY)
                if (maxI != minI) out.add(maxX to maxY)
            } else {
                out.add(maxX to maxY)
                out.add(minX to minY)
            }
        }
        b++
    }
    return out
}
