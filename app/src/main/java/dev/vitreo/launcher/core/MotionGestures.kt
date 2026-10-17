package dev.vitreo.launcher.core

/** A short intentional flick can dismiss; a tiny fast tap or an upward reversal cannot. */
fun shouldDismissSurface(distanceDp: Float, velocityDpPerSecond: Float): Boolean {
    if (!distanceDp.isFinite() || !velocityDpPerSecond.isFinite()) return false
    if (velocityDpPerSecond < -450f) return false
    return distanceDp > 78f || distanceDp > 18f && velocityDpPerSecond > 900f
}
