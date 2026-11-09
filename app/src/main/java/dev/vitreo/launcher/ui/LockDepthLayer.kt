package dev.vitreo.launcher.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import dev.vitreo.launcher.core.DepthMaskPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** An inert drawing layer: only clock glyphs can be occluded, never the secure unlock controls. */
@Composable
internal fun LockDepthLayer(foreground: Bitmap, clock: LayoutCoordinates?, root: LayoutCoordinates?,
    restBounds: Rect, scale: () -> Float, opacity: () -> Float, modifier: Modifier = Modifier) {
    val allowed by produceState(false, foreground, restBounds, root?.size) {
        value = false
        val viewport = root?.size ?: return@produceState
        if (viewport.width <= 0 || viewport.height <= 0 || restBounds.isEmpty) return@produceState
        value = withContext(Dispatchers.Default) {
            val width = 128
            val height = (foreground.height * width.toFloat() / foreground.width).roundToInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(foreground, width, height, true)
            val pixels = IntArray(width * height)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            if (small !== foreground) small.recycle()
            DepthMaskPolicy.allowsClockOverlap(pixels.map { android.graphics.Color.alpha(it) }.toIntArray(), width, height,
                restBounds.left * width / viewport.width, restBounds.top * height / viewport.height,
                restBounds.right * width / viewport.width, restBounds.bottom * height / viewport.height)
        }
    }
    val reveal by animateFloatAsState(if (allowed) 1f else 0f,
        tween(if (LocalGlass.current.appearance.reducedEffects) 0 else 180), label = "Depth reveal")
    if (allowed) {
        val bitmap = remember(foreground) { foreground.asImageBitmap() }
        Canvas(modifier) {
            if (root?.isAttached != true || clock?.isAttached != true) return@Canvas
            // Read the current layer transforms while drawing: clock and wallpaper stay
            // aligned throughout wake, swipe and editor resize without a recomposition per frame.
            val clip = root.localBoundingBoxOf(clock, clipBounds = false)
            clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                scale(scale(), pivot = Offset(size.width / 2, size.height / 2)) {
                    drawImage(bitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        alpha = opacity().coerceIn(0f, 1f) * reveal, filterQuality = FilterQuality.High)
                }
            }
        }
    }
}
