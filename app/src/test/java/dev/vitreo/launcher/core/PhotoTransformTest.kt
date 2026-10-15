package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PhotoTransformTest {
    @Test fun fitShowsTheWholeLandscapePhotoInPortrait() {
        val fit = photoFitZoom(1600f, 900f, 400f, 900f)
        val p = photoPlacement(1600f, 900f, 400f, 900f, PhotoTransform(fit))
        assertEquals(400f, p.width, .001f)
        assertEquals(225f, p.height, .001f)
        assertEquals(0f, p.left, .001f)
        assertEquals(337.5f, p.top, .001f)
        assertTrue(fit < 1f)
    }
    @Test fun fillCoversTheViewportWithoutStretching() {
        val p = photoPlacement(1600f, 900f, 400f, 900f, PhotoTransform())
        assertEquals(900f, p.height, .001f)
        assertEquals(1600f / 900f, p.width / p.height, .001f)
        assertTrue(p.left < 0)
    }
    @Test fun placementIsIndependentOfDisplayDensity() {
        val a = photoPlacement(1600f, 900f, 400f, 900f, PhotoTransform(.8f, -.2f, .3f))
        val b = photoPlacement(1600f, 900f, 1200f, 2700f, PhotoTransform(.8f, -.2f, .3f))
        assertEquals(a.transform, b.transform)
        assertEquals(a.left * 3, b.left, .001f)
        assertEquals(a.top * 3, b.top, .001f)
    }
    @Test fun pinchKeepsThePointUnderTheFingers() {
        val old = PhotoTransform(2f)
        val next = transformPhoto(1000f, 1000f, 400f, 800f, old, 1.2f, 0f, 0f, 240f, 300f)
        val a = photoPlacement(1000f, 1000f, 400f, 800f, old)
        val b = photoPlacement(1000f, 1000f, 400f, 800f, next)
        assertEquals((240 - a.left) / a.width, (240 - b.left) / b.width, .0001f)
        assertEquals((300 - a.top) / a.height, (300 - b.top) / b.height, .0001f)
    }
    @Test fun arbitraryZoomAndPanCannotLoseThePhoto() {
        val random = Random(22)
        repeat(1000) {
            val w = random.nextFloat() * 2000 + 1; val h = random.nextFloat() * 2000 + 1
            val p = photoPlacement(w, h, 400f, 850f, PhotoTransform(random.nextFloat() * 9, random.nextFloat() * 8 - 4, random.nextFloat() * 8 - 4))
            assertTrue(p.left <= 400f && p.left + p.width >= 0f)
            assertTrue(p.top <= 850f && p.top + p.height >= 0f)
            assertEquals(w / h, p.width / p.height, .001f)
            assertTrue(p.transform.zoom >= photoFitZoom(w, h, 400f, 850f))
        }
    }
    @Test fun nonFinitePreferencesGetSafeDefaults() {
        assertEquals(PhotoTransform(), PhotoTransform(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).sanitized())
        val a = Appearance(photoTransform = PhotoTransform(Float.NaN), wallpaperDim = Float.NaN).sanitized()
        assertEquals(PhotoTransform(), a.photoTransform)
        assertEquals(.12f, a.wallpaperDim)
    }
}
