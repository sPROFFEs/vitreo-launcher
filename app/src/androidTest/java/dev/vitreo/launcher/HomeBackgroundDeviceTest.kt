package dev.vitreo.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.graphics.HomeBackground
import dev.vitreo.launcher.graphics.WallpaperFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeBackgroundDeviceTest {
    @Test fun sameBackgroundRendererIncludesDimPhotoAndCyberpunkLayers() {
        val source = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val frame = WallpaperFrame(source, source, 1f)
        val dimmed = HomeBackground.render(frame, Appearance(wallpaperDim = .4f), 400, 800, 1f)
        assertEquals(153.0, Color.red(dimmed.getPixel(123, 123)).toDouble(), 1.0)
        val cyber = HomeBackground.render(frame, Appearance(themePreset = 1, usePhoto = false), 400, 800, 1f)
        assertEquals(Color.rgb(9, 13, 19), cyber.getPixel(123, 123))
        val photo = HomeBackground.render(frame, Appearance(themePreset = 1, usePhoto = true), 400, 800, 1f)
        assertTrue("Photo remains visible under HUD", photo.getPixel(123, 123) != cyber.getPixel(123, 123))
        val large = HomeBackground.render(frame, Appearance(), 1440, 3200, 3f)
        assertEquals(1600, large.height)
        assertEquals(720, large.width)
    }
}
