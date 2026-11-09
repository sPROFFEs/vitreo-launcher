package dev.vitreo.launcher

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.graphics.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DepthWallpaperDeviceTest {
    @Test fun bundledModelSegmentsPeopleAndReusesTheStillImageMaskWithoutPlayServices() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val source = testContext.assets.open("depth-person.jpg").use(BitmapFactory::decodeStream)!!
        val mask = DepthWallpaper.mask(context, source)
        assertNotNull("The bundled CPU model must run on the AOSP emulator", mask)
        assertSame(mask, DepthWallpaper.mask(context, source))
        val pixels = IntArray(mask!!.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        assertTrue(pixels.count { Color.alpha(it) > 230 } > pixels.size / 20)
        assertTrue(pixels.count { Color.alpha(it) == 0 } > pixels.size / 4)
    }

    @Test fun blankWallpaperFallsBackToTheNormalClock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val blank = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 45, 60)) }
        assertNull(DepthWallpaper.mask(context, blank))
        assertNull(DepthWallpaper.mask(context, blank))
    }

    @Test fun foregroundUsesTheExactWallpaperCropAndDoesNotApplyDimTwice() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val mask = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawRect(0f, 0f, 100f, 100f, Paint().apply { color = Color.WHITE })
        val background = HomeBackground.render(WallpaperFrame(source, source), Appearance(wallpaperDim = .25f), 100, 200, 1f)
        val foreground = DepthWallpaper.foreground(mask, source, background)
        assertEquals(background.width, foreground.width); assertEquals(background.height, foreground.height)
        assertEquals(255, Color.alpha(foreground.getPixel(25, 100)))
        assertEquals(0, Color.alpha(foreground.getPixel(75, 100)))
        assertEquals(Color.red(background.getPixel(25, 100)).toDouble(), Color.red(foreground.getPixel(25, 100)).toDouble(), 2.0)
    }
}
