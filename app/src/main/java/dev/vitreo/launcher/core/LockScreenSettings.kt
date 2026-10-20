package dev.vitreo.launcher.core

/** Portable appearance only. Activation and authentication never belong in a layout backup. */
data class LockScreenSettings(
    val theme: Int = 0, // Follow Home, iOS, Cyberpunk
    val clockSize: Float = 104f,
    val clockStyle: Int = 0, // Rounded, light, bold, serif, mono, condensed; append-only IDs
    val clockHeight: Float = 1f,
    val clockColor: Int = 0xfff5f8ff.toInt(),
    val glassClock: Boolean = true,
    val showDate: Boolean = true,
    val showBattery: Boolean = true,
    val showAlarm: Boolean = true,
    val notificationCount: Boolean = false,
    val wallpaper: Int = -1, // Follow Home or a built-in collection item
    val dim: Float = .18f,
    val immersive: Boolean = false,
    val showSystemUnlock: Boolean = false, // Optional shortcut to the real keyguard; never app authentication.
) {
    fun sanitized() = copy(theme = theme.coerceIn(0, 2),
        clockSize = if (clockSize.isFinite()) clockSize.coerceIn(72f, 132f) else 104f,
        clockStyle = clockStyle.coerceIn(0, 5),
        clockHeight = if (clockHeight.isFinite()) clockHeight.coerceIn(1f, 2.4f) else 1f,
        clockColor = clockColor or 0xff000000.toInt(),
        wallpaper = wallpaper.coerceIn(-1, WALLPAPER_NAMES.lastIndex), dim = if (dim.isFinite()) dim.coerceIn(0f, .65f) else .18f)

    fun appearance(home: Appearance): Appearance = home.copy(
        themePreset = when (theme) { 1 -> 0; 2 -> 1; else -> home.themePreset },
        usePhoto = wallpaper == -1 && home.usePhoto,
        palette = if (wallpaper == -1) home.palette else wallpaper,
        wallpaperDim = dim, cyberImmersive = immersive,
    )
}

/** A deliberate upward drag; distance/velocity in dp so every Pixel has the same threshold. */
object LockScreenGesture {
    fun commits(distance: Float, velocity: Float): Boolean = distance.isFinite() && velocity.isFinite() &&
        (distance >= 112f || distance >= 48f && velocity >= 850f)
}
