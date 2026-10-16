package dev.vitreo.launcher

import android.content.Context
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.data.AppCatalog
import dev.vitreo.launcher.core.LauncherState
import dev.vitreo.launcher.core.LayoutOps
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
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
        fun setState(component: ComponentName, state: String) {
            instrumentation.uiAutomation.executeShellCommand("pm $state --user current ${component.flattenToString()}").use {
                FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
            }
        }
        try {
            setState(defaultIcon, "enable")
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(defaultIcon, PackageManager.MATCH_DISABLED_COMPONENTS)
            assertFalse("The fixture must reproduce a manifest-disabled alias", info.enabled)
            assertTrue(context.getSystemService(LauncherApps::class.java).isActivityEnabled(defaultIcon, Process.myUserHandle()))
            val catalog = AppCatalog(context)
            val originalId = defaultIcon.flattenToString()
            assertTrue("A runtime-enabled icon must appear in the launcher", catalog.load().any { it.id == originalId })

            setState(alternateIcon, "enable")
            setState(defaultIcon, "disable")
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
            setState(defaultIcon, "default-state")
            setState(alternateIcon, "default-state")
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
