package dev.vitreo.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.data.AppCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCatalogDeviceTest {
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
