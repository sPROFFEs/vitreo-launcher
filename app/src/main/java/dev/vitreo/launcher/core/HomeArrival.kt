package dev.vitreo.launcher.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class HomeArrivalOffset(val x: Float, val y: Float)

/** The outer ring leads. Each icon eases to rest in its own, fixed cell. */
fun homeArrivalProgress(centerX: Float, centerY: Float, width: Float, height: Float, timeline: Float): Float {
    if (timeline <= 0f) return 0f
    if (timeline >= 1f || !timeline.isFinite() || width <= 0f || height <= 0f ||
        !centerX.isFinite() || !centerY.isFinite() || !width.isFinite() || !height.isFinite()) return 1f
    val x = centerX / width - .5f
    val y = centerY / height - .5f
    val radius = (sqrt(x * x + y * y) * 1.414214f).coerceIn(0f, 1f)
    val delay = (1f - radius) * .14f
    val t = ((timeline - delay) / (1f - delay)).coerceIn(0f, 1f)
    val remaining = 1f - t
    return 1f - remaining * remaining * remaining
}

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
