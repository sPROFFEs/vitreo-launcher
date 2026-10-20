package dev.vitreo.launcher.data

import dev.vitreo.launcher.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LockScreenSettingsTest {
    @Test fun backupRoundTripKeepsAppearanceButNeverContainsActivation() {
        val options = LockScreenSettings(showSystemUnlock = true, theme = 2, clockSize = 126f, clockStyle = 5, clockHeight = 2.1f, clockColor = 0xffb9eaff.toInt(),
            glassClock = false, showDate = false, showBattery = false, showAlarm = false, notificationCount = true,
            wallpaper = 5, dim = .48f, immersive = true)
        val text = StateCodec.encode(LauncherState(appearance = Appearance(lockScreen = options)))
        assertEquals(options, StateCodec.decode(text).appearance.lockScreen)
        val keys = LockScreenCodec.encode(options).keys().asSequence().toSet()
        assertFalse(keys.contains("enabled")); assertFalse(text.contains("password")); assertFalse(text.contains("credential"))
    }
    @Test fun oldBackupsHaveSafeDefaults() {
        val old = JSONObject(StateCodec.encode(LauncherState()))
        old.getJSONObject("appearance").remove("lockScreen")
        assertEquals(LockScreenSettings(), StateCodec.decode(old.toString()).appearance.lockScreen)
    }
    @Test fun boundsAndNonFiniteValuesAreSanitized() {
        val options = LockScreenSettings(theme = 99, clockSize = Float.NaN, clockStyle = -1,
            clockColor = 0x00123456, wallpaper = 999, dim = Float.POSITIVE_INFINITY).sanitized()
        assertEquals(2, options.theme); assertEquals(104f, options.clockSize); assertEquals(0, options.clockStyle)
        assertEquals(0xff123456.toInt(), options.clockColor); assertEquals(WALLPAPER_NAMES.lastIndex, options.wallpaper); assertEquals(.18f, options.dim)
        assertEquals(72f, LockScreenSettings(clockSize = -3f).sanitized().clockSize)
        assertEquals(132f, LockScreenSettings(clockSize = 999f).sanitized().clockSize)
        assertEquals(1f, LockScreenSettings(clockHeight = Float.NaN).sanitized().clockHeight)
        assertEquals(2.4f, LockScreenSettings(clockHeight = 99f).sanitized().clockHeight)
        assertEquals(1f, LockScreenSettings(clockHeight = -2f).sanitized().clockHeight)
        assertEquals(5, LockScreenSettings(clockStyle = 99).sanitized().clockStyle)
        val legacy = LockScreenCodec.encode(LockScreenSettings()).apply { remove("clockHeight") }
        assertEquals(1f, LockScreenCodec.decode(legacy).clockHeight)
        assertFalse(LockScreenCodec.decode(legacy.apply { remove("showSystemUnlock") }).showSystemUnlock)
        assertFalse(LockScreenCodec.decode(null).showSystemUnlock)
    }
    @Test fun themeAndWallpaperFollowHomeOrOverrideIndependently() {
        val home = Appearance(themePreset = 1, usePhoto = true, palette = 4, reducedEffects = true)
        val follow = LockScreenSettings().appearance(home)
        assertEquals(1, follow.themePreset); assertTrue(follow.usePhoto); assertEquals(4, follow.palette)
        val ios = LockScreenSettings(theme = 1, wallpaper = 2).appearance(home)
        assertEquals(0, ios.themePreset); assertFalse(ios.usePhoto); assertEquals(2, ios.palette); assertTrue(ios.reducedEffects)
        for (id in 6..8) {
            val state = LauncherState(appearance = home.copy(palette = id, usePhoto = false,
                lockScreen = LockScreenSettings(theme = 2, wallpaper = id)))
            val restored = StateCodec.decode(StateCodec.encode(state))
            assertEquals(id, restored.appearance.palette)
            assertEquals(id, restored.appearance.lockScreen.wallpaper)
            assertEquals(id, restored.appearance.lockScreen.appearance(home).palette)
        }
    }
    @Test fun swipeRequiresDistanceOrDeliberateUpwardVelocity() {
        assertFalse(LockScreenGesture.commits(47f, 2000f))
        assertFalse(LockScreenGesture.commits(80f, -1000f))
        assertFalse(LockScreenGesture.commits(100f, 100f))
        assertTrue(LockScreenGesture.commits(48f, 850f)); assertTrue(LockScreenGesture.commits(112f, 0f))
        assertFalse(LockScreenGesture.commits(Float.NaN, 9999f))
        assertFalse(LockScreenGesture.commits(120f, Float.POSITIVE_INFINITY))
    }
}
