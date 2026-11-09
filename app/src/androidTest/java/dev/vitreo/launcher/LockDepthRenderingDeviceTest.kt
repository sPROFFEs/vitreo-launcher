package dev.vitreo.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.vitreo.launcher.ui.LockDepthLayer
import dev.vitreo.launcher.ui.VitreoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LockDepthRenderingDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun foregroundActuallyOverlapsTheClockWhileClippingAndUnlockTouchesRemainSafe() {
        val foreground = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        Canvas(foreground).drawRect(0f, 150f, 200f, 190f, Paint().apply { color = android.graphics.Color.GREEN })
        var root by mutableStateOf<LayoutCoordinates?>(null)
        var clock by mutableStateOf<LayoutCoordinates?>(null)
        var bounds by mutableStateOf(Rect.Zero)
        var taps = 0
        compose.setContent {
            VitreoTheme {
                Box(Modifier.size(200.dp, 400.dp).background(Color.White).onGloballyPositioned { root = it }.testTag("depth_test_root")) {
                    Box(Modifier.offset(20.dp, 60.dp).size(160.dp, 100.dp).background(Color.Blue).onGloballyPositioned {
                        clock = it
                        root?.let { parent -> bounds = parent.localBoundingBoxOf(it, clipBounds = false) }
                    })
                    Text("Unlock", Modifier.offset(60.dp, 240.dp).clickable { taps++ }.padding(12.dp))
                    LockDepthLayer(foreground, clock, root, bounds, { 1f }, { 1f },
                        Modifier.matchParentSize().testTag("depth_test_overlay"))
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("depth_test_overlay", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("depth_test_root").captureToImage().asAndroidBitmap()
        assertEquals(android.graphics.Color.GREEN, image.getPixel(image.width / 2, image.height * 155 / 400))
        assertEquals("Foreground must not leak below the clock", android.graphics.Color.WHITE,
            image.getPixel(image.width / 2, image.height * 175 / 400))
        compose.onNodeWithText("Unlock").performClick()
        assertEquals(1, taps)
    }
}
