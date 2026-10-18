package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class HomeChromeTest {
    @Test fun resizedControlRowsKeepOrderAndNeverOverflowFourColumns() {
        for (mask in 0..4095) {
            val wide = CONTROL_SHORTCUTS.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val rows = controlRows(CONTROL_SHORTCUTS, wide)
            assertEquals(CONTROL_SHORTCUTS, rows.flatten())
            assertTrue(rows.all { row -> row.map { if (it in wide) 2 else 1 }.sum() <= 4 })
        }
        assertTrue(controlRows(emptyList(), emptySet()).isEmpty())
        assertEquals(listOf(listOf("camera")), controlRows(listOf("camera", "camera", "bad"), setOf("bad")))
    }
    @Test fun dockFollowsTheFingerOnBothEdgesAndRemainsVisibleBetweenHomePages() {
        assertEquals(0f, homeChromeVisibility(0f, 3), 0f)
        assertEquals(.35f, homeChromeVisibility(.35f, 3), .001f)
        assertEquals(1f, homeChromeVisibility(1.5f, 3), 0f)
        assertEquals(1f, homeChromeVisibility(2.8f, 3), 0f)
        assertEquals(.35f, homeChromeVisibility(3.65f, 3), .001f)
        assertEquals(0f, homeChromeVisibility(4f, 3), 0f)
        for (step in 0..100) {
            val p = step / 100f
            assertEquals(homeChromeVisibility(p, 1), homeChromeVisibility(2f - p, 1), .001f)
        }
    }
    @Test fun invalidOrOutOfRangePagerCoordinatesDoNotExposeTheDock() {
        for (p in listOf(-1f, 9f, Float.NaN, Float.POSITIVE_INFINITY)) assertEquals(0f, homeChromeVisibility(p, 2), 0f)
        assertEquals(0f, homeChromeVisibility(1f, 0), 0f)
    }
    @Test fun movingTodayCardsPreservesAllSectionsAndIgnoresInvalidMoves() {
        assertEquals(listOf("suggestions", "clock_calendar", "widgets"), moveTodaySection(DEFAULT_TODAY_SECTIONS, "suggestions", 0))
        assertEquals(listOf("widgets", "clock_calendar", "suggestions"), moveTodaySection(DEFAULT_TODAY_SECTIONS, "widgets", 0))
        assertEquals(DEFAULT_TODAY_SECTIONS, moveTodaySection(DEFAULT_TODAY_SECTIONS, "unknown", 0))
        assertEquals(DEFAULT_TODAY_SECTIONS, moveTodaySection(DEFAULT_TODAY_SECTIONS, "widgets", 3))
    }
}
