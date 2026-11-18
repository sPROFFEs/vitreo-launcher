package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class HomeUnlockMotionTest {
    @Test fun incomingPositionsAreHeldUntilTheSingleRevealBegins() {
        val motion = HomeUnlockMotion()
        assertEquals(1f, motion.progress.value, 0f)
        motion.sleep(keyguardLocked = false)
        motion.wake(keyguardLocked = true)
        // No Home frame has been exposed yet. A long PIN screen cannot eat the reveal.
        motion.restoreIfOverdue(10_000)
        assertEquals(0f, motion.progress.value, 0f)
        assertEquals(1, motion.beginReveal(10_001))
        motion.renderFrame(1, .35f)
        assertEquals(.35f, motion.progress.value, 0f)
        motion.userPresent(10_100)
        assertNull(motion.beginReveal(10_101))
        motion.finishReveal(1)
        assertEquals(1f, motion.progress.value, 0f)
    }

    @Test fun wakeWithoutKeyguardDoesNotAnimateOrReactToLateSignals() {
        val motion = HomeUnlockMotion()
        motion.sleep(keyguardLocked = false)
        assertNull(motion.beginReveal(100))
        motion.wake(keyguardLocked = true)
        motion.userPresent(150)
        assertNull(motion.beginReveal(150))
        assertEquals(0, motion.serial)
        assertEquals(1f, motion.progress.value, 0f)
    }

    @Test fun callbacksFromAnAbortedRevealCannotChangeTheNextWake() {
        val motion = HomeUnlockMotion()
        motion.sleep(keyguardLocked = true)
        motion.beginReveal(100)
        motion.sleep(keyguardLocked = true)
        motion.beginReveal(150)
        motion.renderFrame(1, .45f)
        motion.finishReveal(1)
        assertEquals(0f, motion.progress.value, 0f)
        motion.renderFrame(2, .2f)
        assertEquals(.2f, motion.progress.value, 0f)
    }

    @Test fun pendingRevealFallsBackOnceHomeIsVisibleAndDoesNotRewind() {
        val motion = HomeUnlockMotion()
        motion.sleep(keyguardLocked = true)
        motion.visibleFrame(100)
        motion.visibleFrame(230)
        motion.restoreIfOverdue(521)
        assertEquals(1f, motion.progress.value, 0f)
        assertNull(motion.beginReveal(600))
        assertEquals(0, motion.serial)
    }

    @Test fun anAnimatorWithNoFramesStillRestoresTheWorkspace() {
        val motion = HomeUnlockMotion()
        motion.sleep(keyguardLocked = true)
        motion.beginReveal(100)
        assertEquals(0f, motion.progress.value, 0f)
        motion.restoreIfOverdue(901)
        assertEquals(1f, motion.progress.value, 0f)
        assertFalse(motion.holdingIcons)
    }
}
