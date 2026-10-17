package dev.vitreo.launcher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import dev.vitreo.launcher.core.homeArrivalOffset

data class HomeArrivalEnvironment(val progress: State<Float>, val width: Float, val height: Float, val enabled: Boolean)
val LocalHomeArrival = staticCompositionLocalOf { HomeArrivalEnvironment(mutableStateOf(1f), 0f, 0f, false) }

@Composable
fun homeArrival(center: Offset, iconSize: Float): Modifier {
    val motion = LocalHomeArrival.current
    if (!motion.enabled) return Modifier
    return Modifier.graphicsLayer {
        val p = motion.progress.value.coerceIn(0f, 1f)
        val offset = homeArrivalOffset(center.x, center.y, motion.width, motion.height, iconSize, p)
        translationX = offset.x; translationY = offset.y
        scaleX = .86f + .14f * p; scaleY = scaleX
        alpha = (p * 5f).coerceIn(0f, 1f)
    }
}
