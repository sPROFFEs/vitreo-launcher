package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class HomeReorderTest {
    private val apps = (0 until 60).map { HomeEntry.App("app$it/Main") }

    @Test fun fixedDropSlotsMatchTheFlowAcrossPagesAndMovableWidgets() {
        val widgets = listOf(HomeWidgetPlacement(10, column = 2, row = 2),
            HomeWidgetPlacement(ID_BUILTIN_CLOCK, page = 1, column = 2, row = 0))
        for (rows in 4..6) {
            val grid = planHomeGrid(apps, widgets, rows, true)
            grid.forEachIndexed { page, content -> content.apps.forEach { cell ->
                val slot = homeDropSlot(grid, page, rows, 400f, 100f, (cell.column + .5f) * 100,
                    (cell.row + .5f) * 100, apps.lastIndex)
                assertEquals(apps.indexOf(cell.entry), slot?.index)
            } }
            grid.forEachIndexed { page, content ->
                (content.widgets + listOfNotNull(content.clockPlacement, content.calendarPlacement)).forEach { widget ->
                    assertNull(homeDropSlot(grid, page, rows, 400f, 100f, (widget.column + .5f) * 100,
                        (widget.row + .5f) * 100, apps.lastIndex))
                }
            }
        }
    }
    @Test fun emptySpaceAppendsButCannotCreateAnUnrepresentableEmptyPage() {
        val grid = planHomeGrid(apps.take(2), listOf(HomeWidgetPlacement(30, page = 3)), 4, false)
        assertEquals(1, homeDropSlot(grid, 0, 4, 400f, 100f, 350f, 350f, 1)?.index)
        assertNull(homeDropSlot(grid, 2, 4, 400f, 100f, 50f, 50f, 1))
        assertNull(homeDropSlot(grid, 0, 4, 400f, 100f, Float.NaN, 50f, 1))
        assertNull(homeDropSlot(grid, 0, 4, 400f, 100f, 400f, 50f, 1))
    }
    @Test fun previewCanMoveInBothDirectionsWithoutDuplicatingOrMutatingTheSavedList() {
        val saved = apps.take(8)
        val forward = previewHomeMove(saved, saved[0], 5)
        assertEquals(saved[0], forward[5])
        assertEquals(saved, previewHomeMove(forward, saved[0], 0))
        assertEquals(saved.toSet(), forward.toSet()); assertEquals(saved.size, forward.size)
        assertEquals(apps.take(8), saved)
    }
    @Test fun commitUsesCurrentEntriesAndKeepsConcurrentRemovalRenameAndSettings() {
        val folder = HomeEntry.Folder("folder:test", "Renamed", listOf("inside/Main", "other/Main"))
        val current = LauncherState(home = listOf(apps[1], folder, apps[3]),
            appearance = Appearance(iconSize = 100f, themePreset = 1))
        val result = LayoutOps.applyHomeOrder(current, listOf(apps[3].id, apps[0].id, folder.id, apps[3].id))
        assertEquals(listOf(apps[3], folder, apps[1]), result.home)
        assertEquals(current.appearance, result.appearance)
        assertFalse(result.placedApps().contains(apps[0].id))
    }
    @Test fun incomingDockEntryMovesOnceAndMissingEntriesAreNeverResurrected() {
        val current = LauncherState(home = apps.take(3), dock = listOf(apps[4].id))
        val order = listOf(apps[4].id) + current.home.map { it.id }
        val result = LayoutOps.applyHomeOrder(current, order, apps[4].id)
        assertTrue(result.dock.isEmpty()); assertEquals(order, result.home.map { it.id })
        assertEquals(current, LayoutOps.applyHomeOrder(current, listOf(apps[5].id), apps[5].id))
    }
    @Test fun diagonalPageSwipeWinsBeforeAHoldCanBecomeAnIconDrag() {
        assertTrue(homeHorizontalSwipe(30f, 32f, 12f))
        assertTrue(homeHorizontalSwipe(-50f, 20f, 12f))
        assertFalse(homeHorizontalSwipe(10f, 0f, 12f))
        assertFalse(homeHorizontalSwipe(20f, 50f, 12f))
        assertFalse(homeHorizontalSwipe(Float.NaN, 0f, 12f))
    }
}
