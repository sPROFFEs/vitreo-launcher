package dev.vitreo.launcher.core

import kotlin.math.abs
import kotlin.math.floor

/** Visual overscroll only: real brightness and volume always stay clamped to Android's range. */
data class SliderStretch(val scale: Float = 1f, val anchor: Float = .5f)

fun iosSliderStretch(fraction: Float): SliderStretch {
    if (!fraction.isFinite()) return SliderStretch()
    val excess = when { fraction > 1f -> fraction - 1f; fraction < 0f -> fraction; else -> 0f }
    if (excess == 0f) return SliderStretch()
    val resistance = .085f * (1f - 1f / (1f + abs(excess) * 3f))
    return SliderStretch(1f + resistance, if (excess > 0) 1f else 0f)
}

/** A bounded window keeps the current page visible even in very large app collections. */
fun homePageIndicatorStart(position: Float, pages: Int, visible: Int = 7): Int {
    if (pages <= 0 || visible <= 0 || !position.isFinite()) return 0
    val page = floor(position - 1f).toInt().coerceIn(0, pages - 1)
    return (page - visible / 2).coerceIn(0, (pages - visible).coerceAtLeast(0))
}
