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

package com.jnd.ngdroid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnd.ngdroid.data.ButtonStyle

/**
 * Responsive flow: one UI, metrics chosen by screen-width bucket so phones
 * big and small render at a visually consistent size.
 *
 * The bucket uses the font-aware effective width (physical dp divided by the
 * system font scale): a narrow phone and a wider phone with huge fonts both
 * land in a tighter bucket. Geometry scales here; the type scale in
 * appTypography() follows the same bucket with smaller base sizes.
 *
 * - SMALL (<340dp effective): tiny phones / large fonts — tightest padding,
 *   smallest touch targets.
 * - COMPACT (340–404dp effective): most phones — slightly tightened.
 * - REGULAR (>=405dp effective): baseline metrics (current look).
 */
enum class WidthBucket { SMALL, COMPACT, REGULAR }

@Composable
fun rememberWidthBucket(): WidthBucket {
    val config = LocalConfiguration.current
    return widthBucketFor(config.screenWidthDp, config.fontScale)
}

/** Pure bucket mapping — JVM-testable. */
fun widthBucketFor(widthDp: Int, fontScale: Float = 1f): WidthBucket {
    val effective = (widthDp / fontScale.coerceAtLeast(0.5f)).toInt()
    return when {
        effective < 340 -> WidthBucket.SMALL
        effective < 405 -> WidthBucket.COMPACT
        else -> WidthBucket.REGULAR
    }
}

@Immutable
data class AppSizes(
    val contentPadding: Dp,
    val messageSpacing: Dp,
    val bubbleMaxFraction: Float,
    val emptyIcon: Dp,
    val emptyIconInner: Dp,
    val inputButton: Dp,
    /** Standard icon-button touch target across all screens (never sp). */
    val iconButton: Dp,
    val navLabelSize: TextUnit,
    val tableCellWidth: Dp
)

fun appSizesFor(bucket: WidthBucket): AppSizes = when (bucket) {
    WidthBucket.SMALL -> AppSizes(
        contentPadding = 10.dp,
        messageSpacing = 12.dp,
        bubbleMaxFraction = 0.92f,
        emptyIcon = 60.dp,
        emptyIconInner = 28.dp,
        inputButton = 44.dp,
        iconButton = 40.dp,
        navLabelSize = 10.sp,
        tableCellWidth = 92.dp
    )
    WidthBucket.COMPACT -> AppSizes(
        contentPadding = 12.dp,
        messageSpacing = 14.dp,
        bubbleMaxFraction = 0.88f,
        emptyIcon = 68.dp,
        emptyIconInner = 32.dp,
        inputButton = 44.dp,
        iconButton = 44.dp,
        navLabelSize = 10.5.sp,
        tableCellWidth = 104.dp
    )
    WidthBucket.REGULAR -> AppSizes(
        contentPadding = 16.dp,
        messageSpacing = 18.dp,
        bubbleMaxFraction = 0.85f,
        emptyIcon = 84.dp,
        emptyIconInner = 40.dp,
        inputButton = 48.dp,
        iconButton = 48.dp,
        navLabelSize = 12.sp,
        tableCellWidth = 130.dp
    )
}

val LocalAppSizes = staticCompositionLocalOf { appSizesFor(WidthBucket.REGULAR) }

/** Action-button shape from settings. Pure — JVM-testable. */
fun buttonShapeFor(style: ButtonStyle): Shape = RoundedCornerShape(style.cornerDp.dp)

/** Action-button shape, provided at the app root from Settings. */
val LocalButtonShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(12.dp) }

/** Dialog container shape from settings (corner dp, clamped to 0..28). Pure — JVM-testable. */
fun dialogShapeFor(cornerDp: Int): Shape = RoundedCornerShape(cornerDp.coerceIn(0, 28).dp)

/** Dialog container shape, provided at the app root from Settings. */
val LocalDialogShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(28.dp) }
