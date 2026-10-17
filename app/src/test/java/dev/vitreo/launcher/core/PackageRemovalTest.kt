package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class PackageRemovalTest {
    private val gone = "dev.fixture.gone/Main"
    private val alias = "dev.fixture.gone/Alternate"
    private val other = "dev.fixture.other/Main"
    private val similar = "dev.fixture.gone.extra/Main"

    @Test fun uninstallRemovesEveryActivityFromHomeDockAndKnownApps() {
        val state = LauncherState(initialized = true, dock = listOf(gone, other), home = listOf(HomeEntry.App(alias), HomeEntry.App(similar)),
            knownApps = setOf(gone, alias, other, similar)).sanitized()
        val after = LayoutOps.removePackages(state, setOf("dev.fixture.gone"))
        assertEquals(listOf(other), after.dock)
        assertEquals(listOf(HomeEntry.App(similar)), after.home)
        assertEquals(setOf(other, similar), after.knownApps)
    }
    @Test fun uninstallCollapsesOneItemFolderAndDropsAnEmptyFolder() {
        val state = LauncherState(initialized = true, home = listOf(
            HomeEntry.Folder("folder:two", "Both", listOf(gone, alias)),
            HomeEntry.Folder("folder:one", "Keep", listOf("dev.fixture.gone/Third", other)))).sanitized()
        assertEquals(listOf(HomeEntry.App(other)), LayoutOps.removePackages(state, setOf("dev.fixture.gone")).home)
    }
    @Test fun uninstallPreservesSurvivingFolderTitleOrderAndAppearance() {
        val folder = HomeEntry.Folder("folder:keep", "My folder", listOf(other, gone, similar))
        val state = LauncherState(initialized = true, home = listOf(folder), appearance = Appearance(iconSize = 84f)).sanitized()
        val after = LayoutOps.removePackages(state, setOf("dev.fixture.gone"))
        assertEquals(listOf(folder.copy(apps = listOf(other, similar))), after.home)
        assertEquals(state.appearance, after.appearance)
    }
    @Test fun refreshDoesNotTreatAMissingActivityAsAnUninstall() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(gone)), knownApps = setOf(gone))
        assertEquals(state, LayoutOps.reconcile(state, emptyList()))
        assertSame(state, LayoutOps.removePackages(state, emptySet()))
    }
    @Test fun reinstallCanAddTheAppAgainAfterAnUninstall() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(gone)), knownApps = setOf(gone))
        val removed = LayoutOps.removePackages(state, setOf("dev.fixture.gone"))
        assertEquals(listOf(HomeEntry.App(gone)), LayoutOps.reconcile(removed, listOf(gone)).home)
    }
}
