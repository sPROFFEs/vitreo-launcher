package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class HomePanelGestureTest {
    @Test fun topAreaSeparatesPanelsAndRespectsAndroidStatusBar() {
        assertEquals(HomePanel.Controls, homePanelAt(.8f, 80f, 30f, 112f, true, true))
        assertEquals(HomePanel.Notifications, homePanelAt(.4f, 80f, 30f, 112f, true, true))
        assertNull(homePanelAt(.8f, 20f, 30f, 112f, true, true))
        assertNull(homePanelAt(.8f, 143f, 30f, 112f, true, true))
        assertNull(homePanelAt(.8f, 80f, 30f, 112f, false, true))
        assertNull(homePanelAt(.4f, 80f, 30f, 112f, true, false))
        assertNull(homePanelAt(Float.NaN, 80f, 30f, 112f, true, true))
    }
    @Test fun shortSlowPullCancelsButFlickAndDeliberatePullOpen() {
        assertFalse(shouldOpenHomePanel(40f, 100f))
        assertFalse(shouldOpenHomePanel(90f, -800f))
        assertFalse(shouldOpenHomePanel(8f, 1400f))
        assertTrue(shouldOpenHomePanel(30f, 800f))
        assertTrue(shouldOpenHomePanel(65f, 100f))
    }
}
