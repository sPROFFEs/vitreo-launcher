package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class LockScreenWakeWindowTest {
    @Test fun quickWakeHasOneRecoveryAndRequiresAnObservedScreenOff() {
        val window = LockScreenWakeWindow()
        assertFalse(window.consumeWake(1000))
        window.screenOff(1000)
        assertTrue(window.consumeWake(1001))
        assertFalse(window.consumeWake(1002))
        window.screenOff(2000)
        assertTrue(window.consumeWake(2600))
    }
    @Test fun normalWakeCancellationAndInvalidClockNeverCauseALateLaunch() {
        val window = LockScreenWakeWindow()
        window.screenOff(1000); assertFalse(window.consumeWake(1601))
        window.screenOff(2000); window.cancel(); assertFalse(window.consumeWake(2100))
        window.screenOff(3000); assertFalse(window.consumeWake(2999))
    }
}
