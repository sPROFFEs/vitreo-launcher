package dev.vitreo.launcher

import android.graphics.Color as AndroidColor
import android.view.View
import android.widget.FrameLayout
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.ui.IosGroup
import dev.vitreo.launcher.ui.WidgetPreview
import dev.vitreo.launcher.ui.WidgetHostContainer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPresentationDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun catalogPreviewHasClearSpaceAboveAndAroundItsContent() {
        val resourceContext = InstrumentationRegistry.getInstrumentation().context
        val provider = ComponentName(resourceContext.packageName, "PreviewFixture")
        val info = AppWidgetProviderInfo().apply {
            this.provider = provider
            label = "Preview fixture"
            previewLayout = resourceContext.resources.getIdentifier("widget_preview_fixture", "layout", resourceContext.packageName)
            minWidth = 320; minHeight = 160
        }
        compose.setContent { IosGroup(Modifier.testTag("gallery_card")) { WidgetPreview(info) } }
        val tag = "widget_preview:${provider.flattenToString()}"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        val card = compose.onNodeWithTag("gallery_card").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val density = resourceContext.resources.displayMetrics.density
        assertTrue("Preview touches the top of its card", preview.top - card.top >= 37f * density)
        assertTrue(preview.left - card.left >= 35f * density)
        assertTrue(card.right - preview.right >= 35f * density)
        assertTrue(card.bottom - preview.bottom >= 25f * density)
    }

    @Test fun oversizedNativeContentStaysInsideItsRoundedWidgetCard() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Green).testTag("widget_canvas"), contentAlignment = Alignment.Center) {
                AndroidView(factory = { context ->
                    WidgetHostContainer(context).apply {
                        addView(View(context).apply { setBackgroundColor(AndroidColor.RED) }, FrameLayout.LayoutParams(4000, 4000))
                    }
                }, modifier = Modifier.size(200.dp, 120.dp).testTag("widget_card"))
            }
        }
        compose.waitForIdle()
        val root = compose.onNodeWithTag("widget_canvas").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("widget_card").fetchSemanticsNode().boundsInRoot.translate(Offset(-root.left, -root.top))
        val image = compose.onNodeWithTag("widget_canvas").captureToImage().asAndroidBitmap()
        assertEquals(AndroidColor.RED, image.getPixel(card.center.x.toInt(), card.center.y.toInt()))
        assertEquals("Oversized RemoteViews must not paint beyond their card", AndroidColor.GREEN,
            image.getPixel(card.right.toInt() + 8, card.center.y.toInt()))
        assertEquals("The native child must respect rounded corners", AndroidColor.GREEN,
            image.getPixel(card.left.toInt() + 2, card.top.toInt() + 2))
    }
}
