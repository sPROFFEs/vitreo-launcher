package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DockAndGroupsTest {
    private val ids = (0..15).map { "app$it/App$it" }
    @Test fun fullDockExchangePreservesEveryShortcut() {
        val start = LayoutOps.reconcile(LauncherState(), ids)
        val result = LayoutOps.placeInDock(start, ids[8], 1)
        assertEquals(ids[8], result.dock[1])
        assertTrue(result.home.any { it.id == ids[1] })
        assertEquals(start.placedApps().toSet(), result.placedApps().toSet())
        assertEquals(16, result.placedApps().size)
    }
    @Test fun randomDockTransfersNeverLoseOrDuplicateApps() {
        val random = Random(193847)
        var state = LayoutOps.reconcile(LauncherState(), ids)
        repeat(1000) {
            state = LayoutOps.placeInDock(state, ids[random.nextInt(ids.size)], random.nextInt(4))
            assertEquals(ids.toSet(), state.placedApps().toSet())
            assertEquals(ids.size, state.placedApps().size)
            assertEquals(4, state.dock.size)
        }
    }
    @Test fun draggingOutOfDockPlacesAppInChosenHomePosition() {
        val start = LayoutOps.reconcile(LauncherState(), ids)
        val result = LayoutOps.moveToSlot(start, ids[0], ids[6])
        assertFalse(ids[0] in result.dock)
        assertEquals(ids[0], result.home[2].id)
        assertEquals(start.placedApps().toSet(), result.placedApps().toSet())
    }
    @Test fun declaredCategoriesWinOverNameHints() {
        assertEquals("Games", appGroup("Calendar Wars", "games.calendar", 0))
        assertEquals("Creativity", appGroup("Cámara", "camera.android", -1))
        assertEquals("Social", appGroup("Mensajes", "com.android.messaging", -1))
        assertEquals("Utilities", appGroup("Unknown", "example.app", -1))
    }

    @Test fun largeIconsUseTheAvailableColumnsAndKeepEveryAppReachable() {
        val large = workspaceGeometry(375f, 540f, LayoutPreset(iconSize = MAX_ICON_SIZE))
        val narrow = workspaceGeometry(280f, 240f, LayoutPreset(iconSize = MAX_ICON_SIZE), 2f)
        assertEquals(84f, large.iconSize, .01f)
        assertTrue(narrow.iconSize < 84f)
        assertTrue(narrow.iconSize < narrow.cells.cellWidth)
        val pages = ids.chunked(narrow.pageSize)
        assertEquals(ids, pages.flatten())
        assertTrue(narrow.cells.rowHeight >= narrow.iconSize + 40f)
    }

    @Test fun aliasChangesPreserveFolderOrderAndDoNotRestoreAppsRemovedFromHome() {
        val replacement = "app0/AlternateIcon"
        val folder = HomeEntry.Folder("folder:icons", "Messages", listOf(ids[0], ids[1], ids[2]))
        val before = LauncherState(initialized = true, home = listOf(folder, HomeEntry.App(ids[3])),
            knownApps = ids.take(5).toSet())
        val after = LayoutOps.reconcile(LayoutOps.remapApps(before, mapOf(ids[0] to replacement)),
            listOf(replacement) + ids.drop(1).take(4))
        assertEquals(folder.copy(apps = listOf(replacement, ids[1], ids[2])), after.home.first())
        assertEquals(before.home[1], after.home[1])
        assertFalse(ids[4] in after.placedApps())
        assertFalse(ids[0] in after.knownApps)
        assertTrue(replacement in after.knownApps)
    }
}
