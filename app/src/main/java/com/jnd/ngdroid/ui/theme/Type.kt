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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Readable type scale: system font (Roboto on Android),
// body 15-16px with generous line height, small UI text 12-14px.
// Sizes step down per width bucket so small phones render proportionally.
fun appTypography(bucket: WidthBucket): Typography {
    // Each entry: fontSize to lineHeight, in display order below.
    val scales: List<Pair<TextUnit, TextUnit>> = when (bucket) {
        WidthBucket.SMALL -> listOf(
            14.5.sp to 22.sp, 13.5.sp to 20.sp, 12.5.sp to 17.5.sp,
            19.sp to 25.sp, 15.sp to 21.sp, 12.sp to 16.5.sp, 11.5.sp to 15.5.sp
        )
        WidthBucket.COMPACT -> listOf(
            15.sp to 23.sp, 14.sp to 21.sp, 13.sp to 18.sp,
            20.sp to 26.sp, 16.sp to 22.sp, 12.5.sp to 17.sp, 12.sp to 16.sp
        )
        WidthBucket.REGULAR -> listOf(
            16.sp to 25.sp, 15.sp to 23.sp, 13.5.sp to 19.sp,
            22.sp to 28.sp, 17.sp to 23.sp, 13.5.sp to 18.sp, 12.5.sp to 17.sp
        )
    }
    val bodyL = scales[0]
    val bodyM = scales[1]
    val bodyS = scales[2]
    val titleL = scales[3]
    val titleM = scales[4]
    val labelM = scales[5]
    val labelS = scales[6]
    return Typography(
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = bodyL.first,
            lineHeight = bodyL.second,
            letterSpacing = 0.15.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = bodyM.first,
            lineHeight = bodyM.second,
            letterSpacing = 0.15.sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = bodyS.first,
            lineHeight = bodyS.second,
            letterSpacing = 0.2.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = titleL.first,
            lineHeight = titleL.second,
            letterSpacing = (-0.2).sp
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = titleM.first,
            lineHeight = titleM.second,
            letterSpacing = 0.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = labelM.first,
            lineHeight = labelM.second,
            letterSpacing = 0.2.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = labelS.first,
            lineHeight = labelS.second,
            letterSpacing = 0.3.sp
        )
    )
}

/** Baseline scale (kept for previews/tests that need a static instance). */
val Typography = appTypography(WidthBucket.REGULAR)