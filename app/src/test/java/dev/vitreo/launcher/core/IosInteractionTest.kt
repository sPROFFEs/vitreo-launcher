package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class IosInteractionTest {
    @Test fun sliderHasNoStretchInsideItsRealRange() {
        for (fraction in listOf(0f, .1f, .5f, .9f, 1f)) assertEquals(SliderStretch(), iosSliderStretch(fraction))
    }
    @Test fun overscrollUsesOppositeAnchorsAndBoundedResistance() {
        assertEquals(1f, iosSliderStretch(1.2f).anchor)
        assertEquals(0f, iosSliderStretch(-.2f).anchor)
        val near = iosSliderStretch(1.05f).scale
        val farther = iosSliderStretch(1.4f).scale
        val extreme = iosSliderStretch(Float.MAX_VALUE).scale
        assertTrue(near > 1f && farther > near && extreme >= farther && extreme < 1.086f)
        assertEquals(iosSliderStretch(-.4f).scale, farther, .0001f)
        assertEquals(1f, iosSliderStretch(1.000001f).scale, .00001f)
    }
    @Test fun malformedInputCannotDeformTheSlider() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) assertEquals(SliderStretch(), iosSliderStretch(value))
    }
    @Test fun indicatorWindowKeepsLaterPagesVisibleAndNeverExceedsItsCollection() {
        for (pages in listOf(1, 2, 7, 12, 100)) for (current in 1..pages) {
            val start = homePageIndicatorStart(current.toFloat(), pages)
            val count = minOf(7, pages)
            assertTrue(start >= 0 && start + count <= pages)
            assertTrue(current - 1 in start until start + count)
        }
        assertEquals(93, homePageIndicatorStart(100f, 100))
        assertEquals(0, homePageIndicatorStart(Float.NaN, 100))
        assertEquals(0, homePageIndicatorStart(4f, 0))
    }
}
