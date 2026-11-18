package dev.vitreo.launcher

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.data.AppRecord
import dev.vitreo.launcher.data.rasterizeAppIcon
import dev.vitreo.launcher.graphics.Viewport
import dev.vitreo.launcher.ui.EntryIcon
import dev.vitreo.launcher.ui.GlassEnvironment
import dev.vitreo.launcher.ui.LocalGlass
import dev.vitreo.launcher.ui.VitreoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class IconFallbackDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opaqueAdaptiveForegroundKeepsItsOriginalColorInEveryMode() {
        val blue = 0xff1565c0.toInt()
        val original = AdaptiveIconDrawable(ColorDrawable(0xffbb0011.toInt()), ColorDrawable(blue))
        val (bitmap, mask) = rasterizeAppIcon(original)
        assertNull("A color foreground does not qualify as a monochrome icon", mask)
        val id = "dev.vitreo.fixture.opaque/.MainActivity"
        val app = AppRecord(id, "Opaque foreground", "dev.vitreo.fixture.opaque", bitmap, monochromeIcon = mask)
        val mode = mutableIntStateOf(0)
        compose.setContent {
            val appearance = Appearance(iconStyle = mode.intValue)
            CompositionLocalProvider(LocalGlass provides GlassEnvironment(null, Viewport(), appearance)) {
                VitreoTheme(appearance = appearance) { EntryIcon(HomeEntry.App(id), mapOf(id to app), 64.dp) }
            }
        }
        for (style in 0..3) {
            compose.runOnIdle { mode.intValue = style }
            val pixels = compose.onNodeWithTag("icon_art:$id").captureToImage().toPixelMap()
            assertEquals("Original icon artwork in style $style", Color(blue), pixels[pixels.width / 2, pixels.height / 2])
        }
    }

    @Test fun androidMasksRejectSolidPlatesAndAcceptActualSilhouettes() {
        if (Build.VERSION.SDK_INT < 33) return
        fun adaptive(layer: android.graphics.drawable.Drawable) =
            AdaptiveIconDrawable(ColorDrawable(0xffbb0011.toInt()), ColorDrawable(0xff1565c0.toInt()), layer)
        val white = 0xffffffff.toInt()
        assertNull(rasterizeAppIcon(adaptive(ColorDrawable(white))).second)
        assertNotNull(rasterizeAppIcon(adaptive(InsetDrawable(ColorDrawable(white), 52))).second)
    }
}
