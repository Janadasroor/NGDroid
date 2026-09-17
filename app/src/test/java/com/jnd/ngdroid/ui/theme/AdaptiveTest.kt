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
import androidx.compose.ui.graphics.Color
import com.jnd.ngdroid.data.ButtonStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdaptiveTest {

    @Test
    fun tinyPhonesAreSmall() {
        assertEquals(WidthBucket.SMALL, widthBucketFor(320))
        assertEquals(WidthBucket.SMALL, widthBucketFor(339))
    }

    @Test
    fun commonPhonesAreCompact() {
        assertEquals(WidthBucket.COMPACT, widthBucketFor(340))
        assertEquals(WidthBucket.COMPACT, widthBucketFor(360))
        assertEquals(WidthBucket.COMPACT, widthBucketFor(393))
        assertEquals(WidthBucket.COMPACT, widthBucketFor(404))
    }

    @Test
    fun wideScreensAreRegular() {
        assertEquals(WidthBucket.REGULAR, widthBucketFor(405))
        assertEquals(WidthBucket.REGULAR, widthBucketFor(411))
        assertEquals(WidthBucket.REGULAR, widthBucketFor(600))
    }

    @Test
    fun sizesShrinkWithBucket() {
        val small = appSizesFor(WidthBucket.SMALL)
        val compact = appSizesFor(WidthBucket.COMPACT)
        val regular = appSizesFor(WidthBucket.REGULAR)
        assert(small.contentPadding < regular.contentPadding)
        assert(compact.contentPadding < regular.contentPadding)
        assert(small.navLabelSize < regular.navLabelSize)
        assert(small.tableCellWidth < regular.tableCellWidth)
        assert(small.iconButton < compact.iconButton)
        assert(compact.iconButton < regular.iconButton)
    }

    @Test
    fun largeFontsDropToTighterBucket() {
        // 400dp phone with huge fonts behaves like a small screen.
        assertEquals(WidthBucket.SMALL, widthBucketFor(400, 1.3f))
        assertEquals(WidthBucket.SMALL, widthBucketFor(360, 1.3f))
        // Normal fonts keep the physical bucket.
        assertEquals(WidthBucket.COMPACT, widthBucketFor(400, 1f))
        assertEquals(WidthBucket.COMPACT, widthBucketFor(360, 1f))
        // Small fonts open up room.
        assertEquals(WidthBucket.REGULAR, widthBucketFor(360, 0.85f))
    }

    @Test
    fun typographyShrinksWithBucket() {
        val smallBody = appTypography(WidthBucket.SMALL).bodyLarge.fontSize
        val regularBody = appTypography(WidthBucket.REGULAR).bodyLarge.fontSize
        assert(smallBody < regularBody)
    }

    @Test
    fun secondaryKeepsAccentHueFamily() {
        val accent = Color(0xFF00E5FF) // cyan ~187°
        val diff = abs(secondaryFor(accent).hueDeg() - accent.hueDeg())
        assertTrue(diff < 30f)
    }

    @Test
    fun tertiaryIsShiftedOffAccent() {
        val diff = abs(tertiaryFor(Color(0xFF00E5FF)).hueDeg() - 187f)
        assertTrue(diff > 20f && diff < 60f)
    }

    @Test
    fun cyanAccentYieldsNoPurpleFallbacks() {
        val hues = listOf(
            secondaryFor(Color(0xFF00E5FF)).hueDeg(),
            tertiaryFor(Color(0xFF00E5FF)).hueDeg()
        )
        // 270° == purple: neither derived color may sit on the old default.
        hues.forEach { assertTrue(abs(it - 270f) > 40f) }
    }

    @Test
    fun buttonShapeFollowsSetting() {
        assertTrue(buttonShapeFor(ButtonStyle.PILL).toString().contains("24.0.dp"))
        assertTrue(buttonShapeFor(ButtonStyle.SQUARE).toString().contains("4.0.dp"))
        assertTrue(buttonShapeFor(ButtonStyle.ROUNDED) is RoundedCornerShape)
    }

    @Test
    fun dialogShapeFollowsSetting() {
        assertTrue(dialogShapeFor(28) is RoundedCornerShape)
        assertTrue(dialogShapeFor(0).toString().contains("0.0.dp"))
        // Out-of-range values clamp to the 0..28 slider range.
        assertEquals(dialogShapeFor(28).toString(), dialogShapeFor(99).toString())
        assertEquals(dialogShapeFor(0).toString(), dialogShapeFor(-5).toString())
    }
}
