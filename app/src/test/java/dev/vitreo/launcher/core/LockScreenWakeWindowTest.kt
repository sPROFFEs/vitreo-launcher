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
    @Test fun offDeliveredAfterWakeRecoversOnceWithoutWaitingForTheNextBroadcast() {
        val window = LockScreenWakeWindow()
        window.screenOff(1000)
        assertTrue(window.consumeQueuedOff(1000, interactive = true, secureLocked = true))
        assertFalse("A delayed SCREEN_ON must not cause another launch", window.consumeWake(1700))
        assertFalse(window.consumeQueuedOff(1701, interactive = true, secureLocked = true))
    }
    @Test fun sleepingOrNotYetSecuredOffKeepsTheOriginalBoundedWakeRecovery() {
        val window = LockScreenWakeWindow()
        window.screenOff(1000)
        assertFalse(window.consumeQueuedOff(1000, interactive = false, secureLocked = true))
        assertTrue(window.consumeWake(1200))
        window.screenOff(2000)
        assertFalse(window.consumeQueuedOff(2000, interactive = true, secureLocked = false))
        assertTrue(window.consumeWake(2200))
    }
}
