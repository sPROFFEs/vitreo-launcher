package dev.vitreo.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.WALLPAPER_NAMES
import dev.vitreo.launcher.graphics.HomeBackground
import dev.vitreo.launcher.graphics.WallpaperFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeBackgroundDeviceTest {
    @Test fun backgroundUsesDimAndBoundsItsTextureSize() {
        val source = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val frame = WallpaperFrame(source, source, 1f)
        val dimmed = HomeBackground.render(frame, Appearance(wallpaperDim = .4f), 400, 800, 1f)
        assertEquals(153.0, Color.red(dimmed.getPixel(123, 123)).toDouble(), 1.0)
        val large = HomeBackground.render(frame, Appearance(), 1440, 3200, 3f)
        assertEquals(1600, large.height)
        assertEquals(720, large.width)
    }

    @Test fun cyberpunkClassicAndNeonCityRespectDimForPhotosAndEveryPreset() {
        val source = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val frame = WallpaperFrame(source, source, 1f)
        for (immersive in listOf(false, true)) for (photo in listOf(false, true)) for (palette in WALLPAPER_NAMES.indices) {
            val pixels = listOf(0f, .3f, .6f).map { dim ->
                val appearance = Appearance(themePreset = 1, cyberImmersive = immersive, usePhoto = photo, palette = palette, wallpaperDim = dim)
                val bitmap = HomeBackground.render(frame, appearance, 400, 800, 1f)
                try { bitmap.getPixel(123, 123) } finally { bitmap.recycle() }
            }
            val label = "immersive=$immersive photo=$photo palette=$palette"
            assertEquals("No hidden tint at zero dim: $label", Color.WHITE, pixels.first())
            pixels.forEachIndexed { index, pixel ->
                val expected = listOf(255.0, 178.0, 102.0)[index]
                assertEquals(label, expected, Color.red(pixel).toDouble(), 1.0)
                assertEquals(label, expected, Color.green(pixel).toDouble(), 1.0)
                assertEquals(label, expected, Color.blue(pixel).toDouble(), 1.0)
            }
        }
    }
}
