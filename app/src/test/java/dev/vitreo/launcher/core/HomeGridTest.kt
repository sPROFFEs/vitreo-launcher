package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class HomeGridTest {
    private val apps = (0 until 73).map { HomeEntry.App("test.app$it/Main") }
    private fun assertSafe(pages: List<HomeGridPage>, rows: Int) {
        assertEquals(apps, pages.flatMap { it.apps }.map { it.entry })
        pages.forEachIndexed { pageIndex, page ->
            val occupied = mutableSetOf<Pair<Int, Int>>()
            if (page.builtins) repeat(2) { row -> repeat(4) { occupied.add(it to row) } }
            page.widgets.forEach { widget ->
                assertEquals(pageIndex, widget.page)
                repeat(widget.rows) { y -> repeat(widget.columns) { x ->
                    val cell = widget.column + x to widget.row + y
                    assertTrue(cell.first in 0..3 && cell.second in 0 until rows)
                    assertTrue("Overlapping widget at $cell", occupied.add(cell))
                } }
            }
            page.apps.forEach { assertTrue("App overlaps widget", occupied.add(it.column to it.row)) }
        }
    }
    @Test fun noWidgetsKeepsTheExistingFirstPageCapacityAndAppOrder() {
        val pages = planHomeGrid(apps, emptyList(), 6, true)
        assertEquals(16, pages.first().apps.size)
        assertEquals(24, pages[1].apps.size)
        assertSafe(pages, 6)
    }
    @Test fun collidingWidgetsReserveDisjointRectanglesAndMoveAppsToFreeCells() {
        val widgets = (1..8).map { HomeWidgetPlacement(it, columns = if (it % 2 == 0) 4 else 2) }
        val pages = planHomeGrid(apps, widgets, 5, true)
        assertEquals(widgets.map { it.id }.toSet(), pages.flatMap { it.widgets }.map { it.id }.toSet())
        assertSafe(pages, 5)
    }
    @Test fun largeWidgetMovesToNextPageWhenBuiltinsLeaveTooLittleHeight() {
        val pages = planHomeGrid(apps, listOf(HomeWidgetPlacement(1, columns = 4, rows = 4)), 4, true)
        assertTrue(pages[0].widgets.isEmpty())
        assertEquals(1, pages[1].widgets.single().id)
        assertSafe(pages, 4)
    }
    @Test fun widgetCanMoveToTheRequestedPageAndCell() {
        val widget = HomeWidgetPlacement(17, page = 2, column = 2, row = 3)
        val pages = planHomeGrid(apps, listOf(widget), 6, true)
        assertEquals(widget, pages[2].widgets.single())
        assertSafe(pages, 6)
    }
    @Test fun rotationLargeTextAndIconSizeChangesKeepEveryAppAndWidget() {
        val widgets = listOf(HomeWidgetPlacement(1, columns = 4, rows = 4), HomeWidgetPlacement(2, column = 2, row = 4))
        for (rows in 1..6) {
            val pages = planHomeGrid(apps, widgets, rows, true)
            assertEquals(setOf(1, 2), pages.flatMap { it.widgets }.map { it.id }.toSet())
            assertSafe(pages, rows)
        }
    }
    @Test fun removingWidgetsRestoresAppOrderWithoutTouchingTheSavedList() {
        planHomeGrid(apps, listOf(HomeWidgetPlacement(1, page = 1, columns = 4, rows = 4)), 6, false)
        val restored = planHomeGrid(apps, emptyList(), 6, false)
        assertEquals(apps.take(24), restored.first().apps.map { it.entry })
        assertSafe(restored, 6)
    }
    @Test fun invalidAndDuplicateLocalIdsCannotCreateUnboundedPages() {
        val pages = planHomeGrid(apps, listOf(HomeWidgetPlacement(-1), HomeWidgetPlacement(1, Int.MAX_VALUE, Int.MAX_VALUE,
            Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE), HomeWidgetPlacement(1)), 6, false)
        assertEquals(1, pages.flatMap { it.widgets }.size)
        assertEquals(64, pages.size)
        assertSafe(pages, 6)
    }
    @Test fun emptyHomeAlwaysHasOnePage() { assertEquals(1, planHomeGrid(emptyList(), emptyList(), 4, false).size) }

    @Test fun dismissUsesDistanceAndVelocityAndRespectsAnUpwardReversal() {
        assertFalse(shouldDismissSurface(10f, 3000f))
        assertTrue(shouldDismissSurface(30f, 1100f))
        assertTrue(shouldDismissSurface(100f, 0f))
        assertFalse(shouldDismissSurface(100f, -800f))
        assertFalse(shouldDismissSurface(40f, 0f))
        assertFalse(shouldDismissSurface(Float.NaN, 0f))
    }
}
