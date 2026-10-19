package dev.vitreo.launcher.core

/** A short intentional flick can dismiss; a tiny fast tap or an upward reversal cannot. */
fun shouldDismissSurface(distanceDp: Float, velocityDpPerSecond: Float): Boolean {
    if (!distanceDp.isFinite() || !velocityDpPerSecond.isFinite()) return false
    if (velocityDpPerSecond < -450f) return false
    return distanceDp > 78f || distanceDp > 18f && velocityDpPerSecond > 900f
}

enum class HomePanel { Controls, Notifications }

/** Coordinates belong to the launcher window; Android owns its physical top edge. */
fun homePanelAt(xFraction: Float, yDp: Float, safeTopDp: Float, heightDp: Float,
    controls: Boolean, notifications: Boolean): HomePanel? {
    if (!xFraction.isFinite() || !yDp.isFinite() || !safeTopDp.isFinite() || !heightDp.isFinite()) return null
    if (xFraction !in 0f..1f || yDp < safeTopDp || yDp > safeTopDp + heightDp.coerceIn(64f, 180f)) return null
    return if (xFraction >= .62f) HomePanel.Controls.takeIf { controls }
        else HomePanel.Notifications.takeIf { notifications }
}

fun shouldOpenHomePanel(distanceDp: Float, velocityDpPerSecond: Float) =
    distanceDp.isFinite() && velocityDpPerSecond.isFinite() && velocityDpPerSecond > -350f &&
        (distanceDp >= 60f || distanceDp >= 24f && velocityDpPerSecond >= 700f)
