package com.jnd.ngdroid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

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
    }

    @Test
    fun typographyShrinksWithBucket() {
        val smallBody = appTypography(WidthBucket.SMALL).bodyLarge.fontSize
        val regularBody = appTypography(WidthBucket.REGULAR).bodyLarge.fontSize
        assert(smallBody < regularBody)
    }
}
