package dev.vitreo.launcher

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.data.AppCatalog
import dev.vitreo.launcher.core.LauncherState
import dev.vitreo.launcher.core.LayoutOps
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCatalogDeviceTest {
    @Test fun runtimeEnabledAliasesStayVisibleAndKeepTheirDockPositionWhenTheIconChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val packageName = instrumentation.context.packageName
        val defaultIcon = ComponentName(packageName, "dev.vitreo.launcher.CatalogDefaultIcon")
        val alternateIcon = ComponentName(packageName, "dev.vitreo.launcher.CatalogAlternateIcon")
        fun setState(component: ComponentName, state: Int) {
            val automation = instrumentation.uiAutomation
            automation.adoptShellPermissionIdentity("android.permission.CHANGE_COMPONENT_ENABLED_STATE")
            try {
                context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                assertEquals("The alias override must be installed before querying the catalog", state,
                    context.packageManager.getComponentEnabledSetting(component))
            } finally { automation.dropShellPermissionIdentity() }
        }
        try {
            setState(defaultIcon, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(defaultIcon, PackageManager.MATCH_DISABLED_COMPONENTS)
            assertFalse("The fixture must reproduce a manifest-disabled alias", info.enabled)
            val catalog = AppCatalog(context)
            val originalId = defaultIcon.flattenToString()
            assertTrue("A runtime-enabled icon must appear in the launcher", catalog.load().any { it.id == originalId })

            setState(alternateIcon, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            setState(defaultIcon, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            val apps = catalog.load()
            val alternateId = alternateIcon.flattenToString()
            assertTrue(apps.any { it.id == alternateId })
            assertFalse(apps.any { it.id == originalId })
            val replacements = catalog.aliasReplacements(setOf(originalId), apps)
            assertEquals(mapOf(originalId to alternateId), replacements)
            val before = LauncherState(initialized = true, dock = listOf(originalId), knownApps = setOf(originalId))
            val after = LayoutOps.reconcile(LayoutOps.remapApps(before, replacements), apps.map { it.id })
            assertEquals(listOf(alternateId), after.dock)
            assertFalse(after.placedApps().contains(originalId))
            assertEquals(1, after.placedApps().count { it == alternateId })
        } finally {
            setState(defaultIcon, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            setState(alternateIcon, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        }
    }

    @Test fun returningToHomeReusesUnchangedAppArtwork() {
        val catalog = AppCatalog(ApplicationProvider.getApplicationContext<Context>())
        val first = catalog.load()
        assertTrue(first.isNotEmpty())
        val again = catalog.load().associateBy { it.id }
        first.forEach {
            assertSame("Unchanged records must not recompose every icon on return", it, again[it.id])
            assertSame(it.icon, again[it.id]?.icon)
        }
    }

    @Test fun invalidatingOnePackagePreservesOtherAppsArtwork() {
        val catalog = AppCatalog(ApplicationProvider.getApplicationContext<Context>())
        val first = catalog.load()
        val selected = first.first { it.packageName == "com.android.settings" }
        catalog.invalidate(selected.packageName)
        val again = catalog.load().associateBy { it.id }
        assertNotSame(selected, again[selected.id])
        first.filter { it.packageName != selected.packageName }.forEach { assertSame(it, again[it.id]) }
        assertEquals(first.map { it.id }.toSet(), again.keys)
    }
}
