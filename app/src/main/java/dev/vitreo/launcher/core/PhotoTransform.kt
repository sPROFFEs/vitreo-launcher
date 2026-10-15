package dev.vitreo.launcher.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Zoom is relative to Fill; offsets are fractions of the viewport, independent of density. */
data class PhotoTransform(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun sanitized() = PhotoTransform(
        if (zoom.isFinite()) zoom.coerceIn(.0001f, 6f) else 1f,
        if (x.isFinite()) x else 0f,
        if (y.isFinite()) y else 0f,
    )
}

data class PhotoPlacement(val left: Float, val top: Float, val width: Float, val height: Float,
    val transform: PhotoTransform)

fun photoFitZoom(imageWidth: Float, imageHeight: Float, width: Float, height: Float): Float {
    if (minOf(imageWidth, imageHeight, width, height) <= 0f) return 1f
    return min(width / imageWidth, height / imageHeight) / max(width / imageWidth, height / imageHeight)
}

/** Keep the complete image reachable at Fit, and prevent panning it out of sight. */
fun photoPlacement(imageWidth: Float, imageHeight: Float, width: Float, height: Float,
    value: PhotoTransform): PhotoPlacement {
    require(listOf(imageWidth, imageHeight, width, height).all { it.isFinite() && it > 0f })
    val clean = value.sanitized()
    val zoom = clean.zoom.coerceIn(photoFitZoom(imageWidth, imageHeight, width, height), 6f)
    val scale = max(width / imageWidth, height / imageHeight) * zoom
    val w = imageWidth * scale; val h = imageHeight * scale
    val dx = (clean.x * width).coerceIn(-abs(w - width) / 2f, abs(w - width) / 2f)
    val dy = (clean.y * height).coerceIn(-abs(h - height) / 2f, abs(h - height) / 2f)
    return PhotoPlacement((width - w) / 2f + dx, (height - h) / 2f + dy, w, h,
        PhotoTransform(zoom, dx / width, dy / height))
}

/** Pinch around the fingers rather than the centre of the screen. */
fun transformPhoto(imageWidth: Float, imageHeight: Float, width: Float, height: Float,
    value: PhotoTransform, zoomChange: Float, panX: Float, panY: Float,
    centroidX: Float = width / 2f, centroidY: Float = height / 2f): PhotoTransform {
    val old = photoPlacement(imageWidth, imageHeight, width, height, value).transform
    val nextZoom = (old.zoom * zoomChange).coerceIn(photoFitZoom(imageWidth, imageHeight, width, height), 6f)
    val ratio = nextZoom / old.zoom
    val next = PhotoTransform(nextZoom,
        ((old.x * width - centroidX + width / 2f) * ratio + centroidX - width / 2f + panX) / width,
        ((old.y * height - centroidY + height / 2f) * ratio + centroidY - height / 2f + panY) / height)
    return photoPlacement(imageWidth, imageHeight, width, height, next).transform
}
