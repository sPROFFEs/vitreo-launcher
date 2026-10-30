package dev.vitreo.launcher.data

import dev.vitreo.launcher.core.*
import org.junit.Assert.*
import org.junit.Test

class StateCodecTest {
    private val a = "com.example.one/.Main"
    private val b = "com.example.two/.Main"
    private val c = "com.example.three/.Main"
    @Test fun immersiveAndWideControlsSurviveBackupWithClassicAsMigrationDefault() {
        val state = LauncherState(appearance = Appearance(themePreset = 1, cyberImmersive = true, palette = 5,
            cyberStatusHeight = 36f, cyberStatusOpacity = .9f,
            controlShortcuts = listOf("timer", "camera"), controlWide = setOf("timer"))).sanitized()
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        assertFalse(StateCodec.decode("""{"schema":1,"home":[],"dock":[],"appearance":{"themePreset":1}}""").appearance.cyberImmersive)
        assertEquals(setOf("timer"), Appearance(controlShortcuts = listOf("timer"), controlWide = setOf("timer", "camera", "bad")).sanitized().controlWide)
        assertEquals(WALLPAPER_NAMES.lastIndex, Appearance(palette = 999).sanitized().palette)
        val legacy = StateCodec.decode("""{"schema":1,"home":[],"dock":[],"appearance":{"themePreset":1}}""").appearance
        assertEquals(22f, legacy.cyberStatusHeight); assertEquals(.72f, legacy.cyberStatusOpacity)
        assertEquals(DEFAULT_CYBER_NIGHT_EFFECTS, legacy.cyberNightEffects)
        val customEffects = LauncherState(appearance = Appearance(themePreset = 1, cyberImmersive = true,
            cyberNightEffects = setOf(CYBER_EFFECT_RAIN, CYBER_EFFECT_GLITCH))).sanitized()
        assertEquals(customEffects, StateCodec.decode(StateCodec.encode(customEffects)))
        assertEquals(setOf(CYBER_EFFECT_RAIN), Appearance(cyberNightEffects = setOf(CYBER_EFFECT_RAIN, "unknown_effect")).sanitized().cyberNightEffects)
        assertEquals(16f, Appearance(cyberStatusHeight = -1f).sanitized().cyberStatusHeight)
        assertEquals(40f, Appearance(cyberStatusHeight = 999f).sanitized().cyberStatusHeight)
        assertEquals(22f, Appearance(cyberStatusHeight = Float.NaN).sanitized().cyberStatusHeight)
        assertEquals(.2f, Appearance(cyberStatusOpacity = -1f).sanitized().cyberStatusOpacity)
        assertEquals(1f, Appearance(cyberStatusOpacity = 9f).sanitized().cyberStatusOpacity)
        assertEquals(.72f, Appearance(cyberStatusOpacity = Float.POSITIVE_INFINITY).sanitized().cyberStatusOpacity)
    }
    @Test fun builtInOverridesAreAllowlistedPortableAndFollowAliasChanges() {
        val icon = IconOverride(THEME_ICON_PACK, "photos")
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(a)),
            appearance = Appearance(iconOverrides = mapOf(a to icon))).sanitized()
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        assertEquals(icon, LayoutOps.remapApps(state, mapOf(a to b)).appearance.iconOverrides[b])
        assertTrue(IconOverride(THEME_ICON_PACK, "clock").valid())
        for (name in listOf("wallpaper_neon_rider", "../../file", "unknown", "")) assertFalse(IconOverride(THEME_ICON_PACK, name).valid())
        assertTrue(state.copy(appearance = state.appearance.copy(iconOverrides = mapOf(a to IconOverride(THEME_ICON_PACK, "bad")))).sanitized().appearance.iconOverrides.isEmpty())
    }
    @Test fun homeIntegrationIsOptionalAndPortableWithoutNotificationContent() {
        val old = StateCodec.decode("""{"schema":1,"home":[],"dock":[],"appearance":{}}""").appearance
        assertFalse(old.homeStatusBar); assertFalse(old.notificationCenter); assertFalse(old.mediaIntegration)
        val configured = LauncherState(appearance = Appearance(homeStatusBar = true, notificationCenter = true,
            mediaIntegration = true, panelGestureHeight = 140f)).sanitized()
        assertEquals(configured, StateCodec.decode(StateCodec.encode(configured)))
        assertEquals(180f, Appearance(panelGestureHeight = 999f).sanitized().panelGestureHeight)
        assertEquals(112f, Appearance(panelGestureHeight = Float.NaN).sanitized().panelGestureHeight)
    }
    @Test fun roundTrip() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.Folder("folder:1", "M\u00fasica", listOf(a, b))),
            dock = listOf(c), appearance = Appearance(palette = 2)).sanitized()
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }
    @Test fun futureSchemaRejected() { assertThrows(IllegalArgumentException::class.java) { StateCodec.decode("{\"schema\":9}") } }
    @Test fun hugeInputRejected() { assertThrows(IllegalArgumentException::class.java) { StateCodec.decode("x".repeat(StateCodec.MAX_BYTES + 1)) } }
    @Test fun unknownElementRejected() { assertThrows(IllegalStateException::class.java) { StateCodec.decode("""{"schema":1,"home":[{"type":"intent","id":"bad"}],"dock":[]}""") } }
    @Test fun invalidAppRemoved() { assertTrue(StateCodec.decode("""{"schema":1,"home":[{"type":"app","id":"intent://bad"}],"dock":[]}""").home.isEmpty()) }
    @Test fun noSilentTruncationOfDock() { assertThrows(IllegalArgumentException::class.java) {
        StateCodec.decode("""{"schema":1,"home":[],"dock":["a/A","b/B","c/C","d/D","e/E"]}""")
    } }
    @Test fun duplicateConflictSanitized() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(a)), dock = listOf(a, a))
        assertEquals(listOf(a), StateCodec.decode(StateCodec.encode(state)).placedApps())
    }
    @Test fun unicodeAndEscapesPreserved() {
        val s = LauncherState(initialized = true, home = listOf(HomeEntry.Folder("folder:test", "\u00d1 \"test\" \\ line", listOf(a,b)))).sanitized()
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }
    @Test fun newAppearanceOptionsRoundTrip() {
        val s = LauncherState(initialized = true, appearance = Appearance(iconStyle = 3,
            tintColor = 0xffe6b4f3.toInt(), interfaceStyle = 2, showWidgets = false, iosIcons = false,
            photoTransform = PhotoTransform(.25f, -.12f, .34f), wallpaperDim = .3f))
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }
    @Test fun previousVersionBackupsKeepLayoutAndGetNewDefaults() {
        val state = StateCodec.decode("""{"schema":1,"home":[{"type":"app","id":"com.example.one/.Main"}],"dock":[],"appearance":{"palette":2,"showLabels":false}}""")
        assertEquals(listOf(a), state.placedApps())
        assertEquals(2, state.appearance.palette)
        assertFalse(state.appearance.showLabels)
        assertEquals(0, state.appearance.iconStyle)
        assertTrue(state.appearance.showWidgets)
        assertEquals(PhotoTransform(), state.appearance.photoTransform)
        assertEquals(.12f, state.appearance.wallpaperDim)
        assertEquals(0, state.appearance.themePreset)
        assertEquals("", state.appearance.iconPack)
        assertTrue(state.appearance.iconOverrides.isEmpty())
        assertTrue(state.appearance.syncWallpaper)
        assertTrue(state.appearance.controlCenter)
        assertEquals(DEFAULT_CONTROL_SHORTCUTS, state.appearance.controlShortcuts)
        assertEquals(DEFAULT_TODAY_SECTIONS, state.appearance.todaySections)
        assertTrue(state.appearance.hiddenTodaySections.isEmpty())
        assertEquals(-1f, state.appearance.homeBrightness)
    }
    @Test fun controlAndTodayCustomizationSurvivesBackupWithoutChangingApps() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(a)), dock = listOf(b),
            appearance = Appearance(controlCenter = false, controlShortcuts = listOf("timer", "camera", "nfc"),
                homeBrightness = .35f, todaySections = listOf("widgets", "suggestions", "clock_calendar"),
                hiddenTodaySections = setOf("clock_calendar"))).sanitized()
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        assertEquals(listOf(b, a), StateCodec.decode(StateCodec.encode(state)).placedApps())
    }
    @Test fun unknownCardsAndControlsCannotHideNativeWidgetsOrCreateArbitraryActions() {
        val a = Appearance(controlShortcuts = listOf("intent://bad", "camera", "camera", "shell"),
            todaySections = listOf("widgets", "widgets", "unknown"), hiddenTodaySections = setOf("widgets", "suggestions", "bad"),
            homeBrightness = Float.NaN).sanitized()
        assertEquals(listOf("camera"), a.controlShortcuts)
        assertEquals(listOf("widgets", "clock_calendar", "suggestions"), a.todaySections)
        assertEquals(setOf("suggestions"), a.hiddenTodaySections)
        assertEquals(-1f, a.homeBrightness)
        assertEquals(.02f, a.copy(homeBrightness = 0f).sanitized().homeBrightness)
    }
    @Test fun cyberThemeAndExternalIconReferencesSurviveBackupWithoutChangingPlacement() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(a)), dock = listOf(b),
            appearance = Appearance(themePreset = 1, cyberEffects = false, syncWallpaper = false, iconPack = "com.example.pack",
                iconOverrides = mapOf(a to IconOverride("com.example.other", "alternative_1")))).sanitized()
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        assertEquals(listOf(b, a), state.placedApps())
    }
    @Test fun unsafePackAndDrawableReferencesAreRejectedWithoutLosingApps() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(a)),
            appearance = Appearance(themePreset = 9, iconPack = "../../bad", iconOverrides = mapOf(
                a to IconOverride("com.example.pack", "../../drawable"), b to IconOverride("intent://bad", "safe")))).sanitized()
        assertEquals(listOf(a), state.placedApps())
        assertEquals(1, state.appearance.themePreset)
        assertEquals("", state.appearance.iconPack)
        assertTrue(state.appearance.iconOverrides.isEmpty())
    }
    @Test fun uninstallAndAliasReplacementAlsoUpdatePerAppIconReferences() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(a)),
            appearance = Appearance(iconOverrides = mapOf(a to IconOverride("com.example.pack", "alternative"))))
        assertTrue(LayoutOps.removePackages(state, setOf("com.example.one")).appearance.iconOverrides.isEmpty())
        assertEquals(state.appearance.iconOverrides[a], LayoutOps.remapApps(state, mapOf(a to b)).appearance.iconOverrides[b])
    }
}
