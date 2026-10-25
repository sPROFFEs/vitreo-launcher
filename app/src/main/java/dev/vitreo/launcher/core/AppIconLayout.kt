package dev.vitreo.launcher.core

/** Expanded folders and Library use real icon dimensions, never a hard-coded 58/60dp. */
fun appGridColumns(width: Float, icon: Float, maximum: Int = 3): Int =
    ((width.coerceAtLeast(1f) + 12f) / (icon.coerceAtLeast(24f) + 12f)).toInt().coerceIn(1, maximum)

fun libraryIconSize(appearance: Appearance, screenWidth: Float): Float {
    val a = appearance.sanitized()
    if (a.libraryIconSize > 0f) return a.libraryIconSize
    val inset = if (a.themePreset == 1) 36f else 40f
    return workspaceGeometry((screenWidth - inset).coerceAtLeast(128f), 600f, LayoutPreset(iconSize = a.iconSize)).iconSize
}

/** A pointer targets the artwork, not the full row containing its label. */
fun folderHoverRadius(iconSize: Float): Float = (iconSize * .46f).coerceAtLeast(18f)
