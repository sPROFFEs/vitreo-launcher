/*
 * Portions adapted from Folio's LayoutModel.kt, retrieved 2026-10-14.
 * Copyright (c) 2026 Duo Launcher contributors. MIT License.
 * See third_party/folio/ for exact provenance and retained license.
 * Modifications: four-column phone layout, no status rail/widgets/fold rules;
 * finite-value guards, bounded rows and minimum usable cell sizes.
 */
package dev.vitreo.launcher.core

import kotlin.math.floor

const val GRID_COLUMNS = 4
const val DOCK_SLOTS = 4
const val DEFAULT_COLUMN_GAP = 16f
const val MIN_LABELED_COLUMN = 64f

private fun Float.safe(low: Float, high: Float, fallback: Float) =
    if (isFinite()) coerceIn(low, high) else fallback

data class LayoutPreset(
    val iconSize: Float = 60f,
    val rowGap: Float = 12f,
    val columnGap: Float = DEFAULT_COLUMN_GAP,
) {
    fun sanitized() = copy(
        iconSize = iconSize.safe(40f, 68f, 60f),
        rowGap = rowGap.safe(4f, 28f, 12f),
        columnGap = columnGap.safe(8f, 40f, DEFAULT_COLUMN_GAP),
    )
}

/** Folio's cell-position / row-pitch model, reduced to the phone workspace. */
data class HomeCellLayout(val cellWidth: Float, val rowHeight: Float, val inset: Float = 0f) {
    fun x(column: Int) = inset + column * cellWidth
    fun y(row: Int) = row * rowHeight
    fun spanHeight(rows: Int) = rows * rowHeight
}

data class WorkspaceGeometry(
    val columns: Int,
    val rows: Int,
    val iconSize: Float,
    val cells: HomeCellLayout,
) {
    val pageSize: Int get() = columns * rows
}

/** Units are dp, excluding bars/header/dock. Tiny windows scroll instead of losing apps. */
fun workspaceGeometry(width: Float, height: Float, preset: LayoutPreset, fontScale: Float = 1f): WorkspaceGeometry {
    val p = preset.sanitized()
    val usableWidth = width.safe(128f, 4096f, 360f)
    val usableHeight = height.safe(48f, 4096f, 480f)
    val scale = fontScale.safe(0.8f, 3f, 1f)
    val cellWidth = usableWidth / GRID_COLUMNS
    // The 80%-of-column cap and label allowance follow Folio's homeGeometry.
    val icon = minOf(p.iconSize, cellWidth * .8f).coerceAtLeast(24f)
    val minRow = maxOf(48f, icon + 20f * scale) + p.rowGap
    val rows = floor(usableHeight / minRow).toInt().coerceIn(1, 6)
    val rowHeight = maxOf(minRow, usableHeight / rows).coerceAtMost(minRow + 22f)
    return WorkspaceGeometry(GRID_COLUMNS, rows, icon, HomeCellLayout(cellWidth, rowHeight))
}

fun pageCount(itemCount: Int, pageSize: Int): Int {
    require(itemCount >= 0 && pageSize > 0)
    return maxOf(1, ((itemCount.toLong() + pageSize - 1) / pageSize).toInt())
}
