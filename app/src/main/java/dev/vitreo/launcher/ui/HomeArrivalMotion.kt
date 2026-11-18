package dev.vitreo.launcher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import dev.vitreo.launcher.core.homeArrivalOffset
import dev.vitreo.launcher.core.homeArrivalProgress

data class HomeArrivalEnvironment(
    val progress: State<Float>, val width: Float, val height: Float, val enabled: Boolean,
    val appDepth: State<Float>, val launchOrigin: String?,
)
val LocalHomeArrival = staticCompositionLocalOf {
    HomeArrivalEnvironment(mutableStateOf(1f), 0f, 0f, false, mutableStateOf(0f), null)
}

@Composable
fun homeArrival(center: Offset, iconSize: Float, entryId: String): Modifier {
    val motion = LocalHomeArrival.current
    if (!motion.enabled) return Modifier
    return Modifier.graphicsLayer {
        val p = homeArrivalProgress(center.x, center.y, motion.width, motion.height, motion.progress.value)
        val offset = homeArrivalOffset(center.x, center.y, motion.width, motion.height, iconSize, p)
        translationX = offset.x; translationY = offset.y
        val appSettle = if (entryId == motion.launchOrigin) motion.appDepth.value * .045f else 0f
        scaleX = .9f + .1f * p - appSettle
        scaleY = scaleX
        alpha = (p * 5f).coerceIn(0f, 1f)
    }
}
