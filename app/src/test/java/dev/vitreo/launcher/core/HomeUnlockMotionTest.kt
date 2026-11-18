package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class HomeUnlockMotionTest {
    @Test fun preparedFrameNeverDrawsSettledIconsBeforeTheReveal() {
        val motion = HomeUnlockMotion()
        assertEquals(1f, motion.frameProgress(0, 1f), 0f)
        motion.sleep(keyguardLocked = false)
        assertEquals(0f, motion.frameProgress(0, 1f), 0f)
        motion.wake(keyguardLocked = true)

        motion.showUnlockedHome(100)
        assertEquals(1, motion.serial)
        // Render can run before Compose installs the new animation.
        assertEquals(0f, motion.frameProgress(0, 1f), 0f)
        assertEquals(0f, motion.frameProgress(1, 0f), 0f)
        assertTrue(motion.consumeReveal())
        assertEquals(.35f, motion.frameProgress(1, .35f), 0f)
        motion.userPresent(120)
        motion.showUnlockedHome(150)
        assertEquals(1, motion.serial)
        assertFalse(motion.consumeReveal())
        motion.finishReveal(1)
        assertEquals(1f, motion.frameProgress(1, .35f), 0f)
    }

    @Test fun wakeWithoutKeyguardLeavesIconsSettledEvenAfterLateSignals() {
        val motion = HomeUnlockMotion()
        motion.sleep(keyguardLocked = false)
        motion.showUnlockedHome(100)
        motion.wake(keyguardLocked = true)
        motion.userPresent(150)
        motion.showUnlockedHome(150)
        assertEquals(0, motion.serial)
        assertEquals(1f, motion.frameProgress(0, 0f), 0f)
    }

    @Test fun finishingAnInterruptedRevealCannotUnprepareTheNextWake() {
        val motion = HomeUnlockMotion()
        motion.sleep(keyguardLocked = true)
        motion.showUnlockedHome(100)
        assertTrue(motion.consumeReveal())
        motion.sleep(keyguardLocked = false)
        motion.finishReveal(1)
        assertEquals(0f, motion.frameProgress(1, .3f), 0f)
        motion.showUnlockedHome(200)
        assertFalse(motion.consumeReveal())
        assertEquals(1f, motion.frameProgress(1, .3f), 0f)
    }

    @Test fun onlyAHomeStoppedForSleepKeepsTheEarlyDrawSignal() {
        val motion = HomeUnlockMotion()
        motion.sleep(keyguardLocked = true)
        assertFalse(motion.stoppedForSleep)
        motion.sleep(keyguardLocked = true, stopped = true)
        motion.sleep(keyguardLocked = true)
        assertTrue(motion.stoppedForSleep)
        motion.leaveHome()
        assertFalse(motion.stoppedForSleep)
        assertEquals(1f, motion.frameProgress(0, 0f), 0f)
    }
}
