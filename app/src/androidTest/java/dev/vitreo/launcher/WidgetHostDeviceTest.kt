package dev.vitreo.launcher

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.data.WidgetController
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetHostDeviceTest {
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
}
