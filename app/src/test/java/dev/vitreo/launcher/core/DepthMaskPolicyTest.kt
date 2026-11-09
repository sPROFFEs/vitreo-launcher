package dev.vitreo.launcher.core

import dev.vitreo.launcher.data.StateCodec
import org.junit.Assert.*
import org.junit.Test

class DepthMaskPolicyTest {
    @Test fun experimentalDepthIsOffInNewAndLegacyBackupsAndCanBeRestored() {
        assertFalse(LockScreenSettings().depthEffect)
        val legacy = org.json.JSONObject(StateCodec.encode(LauncherState()))
        legacy.getJSONObject("appearance").getJSONObject("lockScreen").remove("depthEffect")
        assertFalse(StateCodec.decode(legacy.toString()).appearance.lockScreen.depthEffect)
        val original = LauncherState(appearance = Appearance(lockScreen = LockScreenSettings(depthEffect = true)))
        assertTrue(StateCodec.decode(StateCodec.encode(original)).appearance.lockScreen.depthEffect)
    }
    @Test fun confidenceHasASoftMonotonicEdgeAndRejectsUncertainPixels() {
        assertEquals(0, DepthMaskPolicy.alpha(Float.NaN))
        assertEquals(0, DepthMaskPolicy.alpha(.54f)); assertEquals(255, DepthMaskPolicy.alpha(.86f))
        assertTrue(DepthMaskPolicy.alpha(.70f) in 100..150)
        val values = (0..100).map { DepthMaskPolicy.alpha(it / 100f) }
        assertTrue(values.zipWithNext().all { (a, b) -> a <= b })
    }
    @Test fun blankLowConfidenceAndNearlySolidMasksKeepTheNormalClock() {
        assertFalse(DepthMaskPolicy.usable(IntArray(100)))
        assertFalse(DepthMaskPolicy.usable(IntArray(100) { 128 }))
        assertFalse(DepthMaskPolicy.usable(IntArray(100) { 255 }))
        assertTrue(DepthMaskPolicy.usable(IntArray(100) { if (it < 25) 255 else 0 }))
    }
    @Test fun smallPartialOverlapIsAllowedButOneWholeDigitCannotBeCovered() {
        val mask = IntArray(100 * 100)
        for (y in 40 until 50) for (x in 20 until 80) mask[y * 100 + x] = 255
        assertTrue(DepthMaskPolicy.allowsClockOverlap(mask, 100, 100, 0f, 0f, 100f, 50f))
        assertFalse(DepthMaskPolicy.allowsClockOverlap(mask, 100, 100, 0f, 0f, 100f, 20f))
        val digit = IntArray(100 * 100) { if (it % 100 < 20) 255 else 0 }
        assertFalse(DepthMaskPolicy.allowsClockOverlap(digit, 100, 100, 0f, 0f, 100f, 50f))
        assertFalse(DepthMaskPolicy.allowsClockOverlap(mask, 100, 100, Float.NaN, 0f, 100f, 50f))
    }
}
