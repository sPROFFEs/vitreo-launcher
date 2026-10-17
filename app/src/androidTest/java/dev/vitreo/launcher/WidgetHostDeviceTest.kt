package dev.vitreo.launcher

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.data.WidgetController
import dev.vitreo.launcher.core.HomeWidgetPlacement
import dev.vitreo.launcher.ui.renderWidgetPreview
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetHostDeviceTest {
    @Test fun providerLayoutPreviewRendersWithoutAllocatingOrBindingAWidget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceContext = instrumentation.context
        val widgets = WidgetController(context)
        val before = widgets.host.appWidgetIds.toSet()
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(resourceContext.packageName, "PreviewFixture")
            previewLayout = resourceContext.resources.getIdentifier("widget_preview_fixture", "layout", resourceContext.packageName)
        }
        assertTrue(info.previewLayout != 0)
        var preview: Bitmap? = null
        instrumentation.runOnMainSync { preview = renderWidgetPreview(context, info, 320, 160, false) }
        assertNotNull(preview)
        assertEquals(Color.rgb(18, 108, 66), preview!!.getPixel(160, 120))
        assertEquals(before, widgets.host.appWidgetIds.toSet())
        assertEquals(-1, widgets.pendingId)
    }

    @Test fun cancellationRejectsLateResultsAndAllowsAnotherRequest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgets = WidgetController(context)
        val provider = widgets.providers().firstOrNull()
        assumeNotNull(provider)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        val first = checkNotNull(widgets.beginAdd())
        var second = -1
        try {
            assertNull("Double taps must not allocate another widget", widgets.beginAdd())
            assertTrue(widgets.manager.bindAppWidgetIdIfAllowed(first, provider!!.provider))
            widgets.cancelAdd()
            assertNull(widgets.manager.getAppWidgetInfo(first))
            second = checkNotNull(widgets.beginAdd())
            assertFalse(widgets.finishAdd(first, true))
            assertEquals(second, widgets.pendingId)
            assertFalse(first in widgets.ids)
            assertTrue(widgets.manager.bindAppWidgetIdIfAllowed(second, provider.provider))
            assertTrue(widgets.finishAdd(second, true))
            assertTrue(second in WidgetController(context).ids)
        } finally {
            widgets.cancelAdd()
            widgets.remove(first)
            if (second >= 0) widgets.remove(second)
            automation.dropShellPermissionIdentity()
        }
    }

    @Test fun boundWidgetPersistsAndRemovalReleasesItsId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgets = WidgetController(context)
        val provider = widgets.providers().firstOrNull()
        assumeNotNull(provider)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        val id = widgets.host.allocateAppWidgetId()
        try {
            assertTrue(widgets.manager.bindAppWidgetIdIfAllowed(id, provider!!.provider))
            widgets.add(id)
            assertTrue(id in WidgetController(context).ids)
            widgets.remove(id)
            assertFalse(id in WidgetController(context).ids)
            assertNull(widgets.manager.getAppWidgetInfo(id))
        } finally {
            widgets.remove(id)
            automation.dropShellPermissionIdentity()
        }
    }

    @Test fun todayOrderPersistsWithoutMovingHomeWidgets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgets = WidgetController(context)
        val provider = checkNotNull(widgets.providers().firstOrNull())
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        check(automation.executeShellCommand("getprop ro.kernel.qemu").use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText().trim() }
        } == "1")
        automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        val allocated = mutableListOf<Int>()
        try {
            repeat(3) {
                val id = widgets.host.allocateAppWidgetId(); allocated.add(id)
                assertTrue(widgets.manager.bindAppWidgetIdIfAllowed(id, provider.provider))
                assertTrue(widgets.add(id))
            }
            val (first, home, third) = allocated
            widgets.placeOnHome(HomeWidgetPlacement(home, 2))
            val placement = widgets.homePlacements.toList()
            widgets.moveInToday(third, -1)
            assertEquals(listOf(third, first), widgets.todayIds.filter { it in allocated })
            val restored = WidgetController(context)
            assertEquals(widgets.todayIds, restored.todayIds)
            assertEquals(placement, restored.homePlacements.toList())
            restored.moveInToday(restored.todayIds.first(), -1)
            assertEquals(widgets.todayIds, restored.todayIds)
            restored.moveInToday(home, -1)
            assertEquals(placement, restored.homePlacements.toList())
        } finally { allocated.forEach(widgets::remove); automation.dropShellPermissionIdentity() }
    }

    @Test fun homeWidgetSizePageAndTodayMigrationSurviveControllerRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgets = WidgetController(context)
        val provider = checkNotNull(widgets.providers().firstOrNull())
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        val id = checkNotNull(widgets.beginAdd(homePage = 2, columns = 4, rows = 2))
        try {
            assertTrue(widgets.manager.bindAppWidgetIdIfAllowed(id, provider.provider))
            assertTrue(widgets.finishAdd(id, true))
            val restored = WidgetController(context)
            assertEquals(HomeWidgetPlacement(id, 2, columns = 4), restored.homePlacements.single { it.id == id })
            assertFalse(id in restored.todayIds)
            restored.placeOnHome(HomeWidgetPlacement(id, 1, columns = 4, rows = 4))
            assertEquals(4, WidgetController(context).homePlacements.single { it.id == id }.rows)
            restored.moveToToday(id)
            val today = WidgetController(context)
            assertTrue(id in today.todayIds)
            assertTrue(today.homePlacements.none { it.id == id })
            assertNotNull(today.manager.getAppWidgetInfo(id))
            today.remove(id)
            assertNull(today.manager.getAppWidgetInfo(id))
        } finally { widgets.remove(id); automation.dropShellPermissionIdentity() }
    }
}
