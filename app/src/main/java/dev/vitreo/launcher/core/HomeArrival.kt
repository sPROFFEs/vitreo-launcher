package dev.vitreo.launcher.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class HomeArrivalOffset(val x: Float, val y: Float)

/** A radial start beyond the nearest screen edge, settling into the unchanged grid cell. */
fun homeArrivalOffset(centerX: Float, centerY: Float, width: Float, height: Float, iconSize: Float, progress: Float): HomeArrivalOffset {
    if (width <= 0 || height <= 0 || !centerX.isFinite() || !centerY.isFinite() || !width.isFinite() ||
        !height.isFinite() || !iconSize.isFinite() || !progress.isFinite() || progress >= 1f)
        return HomeArrivalOffset(0f, 0f)
    val dx = centerX - width / 2f; val dy = centerY - height / 2f
    val distance = sqrt(dx * dx + dy * dy)
    val nx = if (distance < 1f) 0f else dx / distance
    val ny = if (distance < 1f) 1f else dy / distance
    val edge = min(if (abs(nx) < .001f) Float.POSITIVE_INFINITY else width / 2f / abs(nx),
        if (abs(ny) < .001f) Float.POSITIVE_INFINITY else height / 2f / abs(ny))
    val travel = (edge - distance + iconSize).coerceAtLeast(0f) * (1f - progress.coerceIn(0f, 1f))
    return HomeArrivalOffset(nx * travel, ny * travel)
}
