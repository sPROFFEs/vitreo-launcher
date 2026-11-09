package dev.vitreo.launcher.core

const val CYBER_FACTION_COUNT = 8

/** Append-only faction/wallpaper IDs preserve existing layouts and backups. */
data class CyberCharacter(val name: String, val faction: Int, val wallpaper: Int, val description: String)
val CYBER_CHARACTERS = listOf(
    CyberCharacter("David", 4, 9, "Emergency yellow · Sandevistan green"),
    CyberCharacter("Lucy", 5, 10, "Moonlight cyan · Pearl and violet"),
    CyberCharacter("Rebecca", 6, 11, "Mint green · Electric pink"),
    CyberCharacter("Kiwi", 7, 12, "Crimson netrunner · Pale gold"),
)

/** This explicit action applies both wallpapers. Palette-only selection leaves photos untouched. */
fun Appearance.withCyberCharacter(character: CyberCharacter): Appearance = copy(
    themePreset = 1, cyberFaction = character.faction, palette = character.wallpaper, usePhoto = false,
    lockScreen = lockScreen.copy(wallpaper = character.wallpaper),
)
