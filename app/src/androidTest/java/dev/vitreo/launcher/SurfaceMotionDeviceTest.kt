package dev.vitreo.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.ui.SurfaceHost
import dev.vitreo.launcher.ui.SurfaceKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SurfaceMotionDeviceTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun reversingAnOpeningPanelPreservesVelocityAndReopeningRetainsItsContent() {
        var target by mutableStateOf<String?>("panel")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SurfaceHost(target, { SurfaceKind.Category }, TransformOrigin.Center, { target = null }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(160.dp).testTag("motion_marker"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        val openingWidth = compose.onNodeWithTag("motion_marker").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { target = null }
        compose.mainClock.advanceTimeBy(32)
        val reversingWidth = compose.onNodeWithTag("motion_marker").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Reversal must inherit opening velocity instead of stopping instantly", reversingWidth >= openingWidth - 1f)
        compose.runOnIdle { target = "panel" }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("motion_marker").assertIsDisplayed()
        compose.runOnIdle { target = null }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("motion_marker").assertDoesNotExist()
    }
}
