package dev.vitreo.launcher.core

import dev.vitreo.launcher.data.StateCodec
import dev.vitreo.launcher.ui.CyberColors
import org.junit.Assert.*
import org.junit.Test

class CyberCharactersTest {
    @Test fun charactersHaveStableUniqueIdsAndCompletePalettesAndWallpapers() {
        assertEquals(CYBER_FACTION_COUNT, CyberColors.Factions.size)
        assertEquals(listOf(4, 5, 6, 7), CYBER_CHARACTERS.map { it.faction })
        assertEquals(listOf(9, 10, 11, 12), CYBER_CHARACTERS.map { it.wallpaper })
        CYBER_CHARACTERS.forEach {
            assertEquals(it.name, CyberColors.forFaction(it.faction).name)
            assertTrue(WALLPAPER_NAMES[it.wallpaper].startsWith(it.name))
            val home = Appearance(usePhoto = true, reducedEffects = true, cyberEffects = false,
                lockScreen = LockScreenSettings(dim = .30f, depthEffect = true))
            val applied = home.withCyberCharacter(it)
            assertEquals(it.faction, applied.cyberFaction); assertFalse(applied.usePhoto)
            assertEquals(it.wallpaper, applied.palette); assertEquals(it.wallpaper, applied.lockScreen.wallpaper)
            assertEquals(home.lockScreen.dim, applied.lockScreen.dim); assertTrue(applied.lockScreen.depthEffect)
            assertTrue(applied.reducedEffects); assertFalse(applied.cyberEffects)
            assertEquals(applied, StateCodec.decode(StateCodec.encode(LauncherState(appearance = applied))).appearance)
        }
        assertEquals(listOf("Kiroshi Optics", "Arasaka Corpo", "Netrunner Deep-Net", "Trauma Team"), CyberColors.Factions.take(4).map { it.name })
    }
}
