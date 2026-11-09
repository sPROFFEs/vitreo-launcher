package dev.vitreo.launcher.core

import kotlin.math.roundToInt

/** Conservative thresholds: an uncertain cutout must never obscure the clock. */
object DepthMaskPolicy {
    fun alpha(confidence: Float): Int {
        if (!confidence.isFinite()) return 0
        val x = ((confidence - .55f) / .30f).coerceIn(0f, 1f)
        return (x * x * (3f - 2f * x) * 255).roundToInt()
    }

    fun usable(alpha: IntArray): Boolean {
        if (alpha.isEmpty()) return false
        val strong = alpha.count { it >= 230 }.toFloat() / alpha.size
        return strong in .015f.. .80f
    }

    /** Coordinates refer to the mask after the wallpaper's crop, before its wake animation. */
    fun allowsClockOverlap(alpha: IntArray, width: Int, height: Int,
        left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (width <= 0 || height <= 0 || alpha.size != width * height ||
            listOf(left, top, right, bottom).any { !it.isFinite() } || right <= left || bottom <= top) return false
        val x0 = left.toInt().coerceIn(0, width); val x1 = right.toInt().coerceIn(0, width)
        val y0 = top.toInt().coerceIn(0, height); val y1 = bottom.toInt().coerceIn(0, height)
        if (x1 <= x0 || y1 <= y0) return false
        var total = 0f
        val quarter = FloatArray(4); val counts = IntArray(4)
        for (y in y0 until y1) for (x in x0 until x1) {
            val value = alpha[y * width + x].coerceIn(0, 255) / 255f
            val part = ((x - x0) * 4 / (x1 - x0)).coerceAtMost(3)
            total += value; quarter[part] += value; counts[part]++
        }
        val coverage = total / ((x1 - x0) * (y1 - y0))
        return coverage in .003f.. .24f && quarter.indices.all { counts[it] > 0 && quarter[it] / counts[it] < .46f }
    }
}
