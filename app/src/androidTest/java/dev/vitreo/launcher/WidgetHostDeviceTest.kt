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
