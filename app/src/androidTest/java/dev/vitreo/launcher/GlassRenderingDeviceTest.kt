package dev.vitreo.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.graphics.GlassDiagnostics
import dev.vitreo.launcher.graphics.Viewport
import dev.vitreo.launcher.graphics.WallpaperStore
import dev.vitreo.launcher.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Prove that the hardware path visibly refracts, rather than merely compiling AGSL. */
@RunWith(AndroidJUnit4::class)
class GlassRenderingDeviceTest {
    @get:Rule val compose = createComposeRule()

    @SdkSuppress(minSdkVersion = 33)
    @Test fun clearAndRegularGlassRefractAtTheEdgesAndKeepTheCentreStable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val texture = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(texture)
        val paint = Paint()
        val colors = intArrayOf(0xff153d77.toInt(), 0xff8ce3cf.toInt(), 0xffdd796a.toInt(), 0xffe3b8f2.toInt())
        for (y in 0 until 1600 step 32) for (x in 0 until 720 step 32) {
            paint.color = colors[(x / 32 + y / 32) % colors.size]
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + 32).toFloat(), (y + 32).toFloat(), paint)
        }
        val frame = WallpaperStore(context).frame(texture)
        var strength by mutableFloatStateOf(0f)
        compose.setContent {
            var viewport by remember { mutableStateOf(Viewport()) }
            VitreoTheme(1) {
                Box(Modifier.fillMaxSize().onGloballyPositioned {
                    val position = it.positionOnScreen()
                    viewport = Viewport(it.size.width.toFloat(), it.size.height.toFloat(), position.x, position.y)
                }) {
                    Image(frame.sharp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    CompositionLocalProvider(LocalGlass provides GlassEnvironment(frame, viewport,
                        Appearance(interfaceStyle = 1, refraction = strength))) {
                        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            GlassPanel(Modifier.width(280.dp).height(120.dp).testTag("clear"), radius = 32f) {}
                            GlassPanel(Modifier.width(280.dp).height(120.dp).testTag("regular"), radius = 32f, material = GlassMaterial.Regular) {}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val flat = listOf("clear", "regular").associateWith { capture(it, "flat") }
        compose.runOnIdle { strength = 1f }
        compose.waitForIdle()
        for (name in flat.keys) {
            val before = flat.getValue(name)
            val after = capture(name, "refracted")
            var edgeDifference = 0L; var edgePixels = 0
            var centreDifference = 0L; var centrePixels = 0
            for (y in 0 until before.height) for (x in 0 until before.width) {
                val a = before.getPixel(x, y); val b = after.getPixel(x, y)
                val delta = abs(AndroidColor.red(a) - AndroidColor.red(b)) +
                    abs(AndroidColor.green(a) - AndroidColor.green(b)) + abs(AndroidColor.blue(a) - AndroidColor.blue(b))
                if (x < before.width / 8 || x > before.width * 7 / 8 || y < before.height / 8 || y > before.height * 7 / 8) {
                    edgeDifference += delta; edgePixels++
                } else if (x in before.width / 3..before.width * 2 / 3 && y in before.height / 3..before.height * 2 / 3) {
                    centreDifference += delta; centrePixels++
                }
            }
            val edge = edgeDifference.toDouble() / (edgePixels * 3)
            val centre = centreDifference.toDouble() / (centrePixels * 3)
            assertTrue("$name must visibly refract, mean edge change=$edge", edge > 2.0)
            assertTrue("$name centre must remain stable for text, mean change=$centre", centre < 1.0)
        }
        assertTrue("A software fallback must not pass this test", GlassDiagnostics.hardwareRefractionDrawn)
        assertEquals("none", GlassDiagnostics.lastFailure)
    }

    private fun capture(tag: String, suffix: String): Bitmap {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = checkNotNull(context.getExternalFilesDir("review"))
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "glass-$tag-$suffix.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }
}
