package dev.vitreo.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Prove that the hardware path visibly refracts, rather than merely compiling AGSL. */
@RunWith(AndroidJUnit4::class)
class GlassRenderingDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun untouchedLibraryGlassTracksScrollingWallpaper() = verifyScrollingGlass(reducedEffects = false)

    @Test fun reducedEffectsGlassTracksScrollingWallpaper() = verifyScrollingGlass(reducedEffects = true)

    /** Moving an existing lens must match a newly placed lens, without a press to refresh it. */
    private fun verifyScrollingGlass(reducedEffects: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val texture = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(texture)
        val paint = Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, 1600f,
                intArrayOf(0xffe84824.toInt(), 0xff195cdf.toInt(), 0xfff2bc29.toInt(), 0xff276850.toInt()),
                null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 720f, 1600f, paint)
        val frame = WallpaperStore(context).frame(texture)
        val grid = LazyGridState()
        lateinit var scope: CoroutineScope
        var generation by mutableIntStateOf(0)
        compose.setContent {
            scope = rememberCoroutineScope()
            var viewport by remember { mutableStateOf(Viewport()) }
            VitreoTheme(1) {
                Box(Modifier.fillMaxSize().onGloballyPositioned {
                    val position = it.positionOnScreen()
                    viewport = Viewport(it.size.width.toFloat(), it.size.height.toFloat(), position.x, position.y)
                }) {
                    Image(frame.sharp.asImageBitmap(), null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    CompositionLocalProvider(LocalGlass provides GlassEnvironment(frame, viewport,
                        Appearance(interfaceStyle = 1, refraction = 1f, reducedEffects = reducedEffects))) {
                        LazyVerticalGrid(GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items((0 until 20).toList(), key = { it }) { item ->
                                key(if (item == 2) generation else 0) {
                                    // No interactive light, parent motion state or content recomposition can refresh this lens.
                                    GlassPanel(Modifier.height(140.dp).fillMaxWidth()
                                        .testTag("scroll-glass-$item"), radius = 32f) {}
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val name = "scroll-glass-2"
        val mode = if (reducedEffects) "reduced" else "full"
        val beforeBounds = compose.onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
        val before = capture(name, "$mode-before")
        compose.runOnIdle { scope.launch { grid.scrollToItem(0, before.height * 3 / 4) } }
        compose.waitForIdle()
        val movedBounds = compose.onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
        assertTrue("The existing card must actually move", beforeBounds.top - movedBounds.top > before.height / 2)
        val moved = capture(name, "$mode-scrolled")
        compose.runOnIdle { generation++ }
        compose.waitForIdle()
        assertEquals("Refreshing the lens must not change its geometry", movedBounds,
            compose.onNodeWithTag(name).fetchSemanticsNode().boundsInRoot)
        val fresh = capture(name, "$mode-fresh")
        val referenceChange = centreDifference(before, fresh)
        assertTrue("The wallpaper fixture must visibly change at the destination: $referenceChange", referenceChange > 10.0)
        val staleDifference = centreDifference(moved, fresh)
        assertTrue("Untouched $mode glass must sample its new wallpaper position: difference=$staleDifference",
            staleDifference < 1.0)
    }

    private fun centreDifference(a: Bitmap, b: Bitmap): Double {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var difference = 0L
        var pixels = 0
        for (y in a.height / 3 until a.height * 2 / 3) for (x in a.width / 3 until a.width * 2 / 3) {
            val first = a.getPixel(x, y); val second = b.getPixel(x, y)
            difference += abs(AndroidColor.red(first) - AndroidColor.red(second)) +
                abs(AndroidColor.green(first) - AndroidColor.green(second)) + abs(AndroidColor.blue(first) - AndroidColor.blue(second))
            pixels++
        }
        return difference.toDouble() / (pixels * 3)
    }

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
