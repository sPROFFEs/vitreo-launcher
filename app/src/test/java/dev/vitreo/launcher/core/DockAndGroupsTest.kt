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
}
