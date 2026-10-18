package dev.vitreo.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vitreo.launcher.core.Appearance
import kotlin.math.sin

val LocalCyberPhase = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Created only while visible. Draw-phase reads never recompose the workspace or rewrite its wallpaper. */
@Composable
fun cyberAmbientPhase(appearance: Appearance, visible: Boolean): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val enabled = visible && resumed && LocalWindowInfo.current.isWindowFocused &&
        appearance.themePreset == 1 && appearance.cyberImmersive && appearance.cyberEffects && !appearance.reducedEffects
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "Cyberpunk ambience")
    return transition.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "HUD phase")
}

@Composable
fun CyberAmbient(modifier: Modifier = Modifier) {
    val phase = LocalCyberPhase.current
    Canvas(modifier.fillMaxSize().testTag("cyber_ambient")) {
        val p = phase.value
        // Original HUD, never flashes the content or displaces app labels.
        val w = size.width; val h = size.height
        val drift = sin(p * Math.PI.toFloat() * 2)
        val pulse = .5f + .5f * drift
        drawCircle(Brush.radialGradient(listOf(CyberColors.Cyan.copy(alpha = .035f + pulse * .025f), Color.Transparent),
            center = Offset(w * (.6f + drift * .15f), h * .65f), radius = w * .75f), w * .75f, Offset(w * (.6f + drift * .15f), h * .65f))
        // Deterministic rain particles: no allocation of random data or per-frame bitmap effects.
        repeat(34) { i ->
            val x = ((1f + i * .618034f + drift * .015f) % 1f) * w
            val y = ((i * .381966f + p * (3f + i % 3)) % 1f) * h
            drawLine(CyberColors.Cyan.copy(alpha = .055f + (i % 4) * .018f), Offset(x, y), Offset(x - 3.dp.toPx(), y + (9 + i % 11).dp.toPx()), 1.dp.toPx())
        }
        val scan = (p * 2f % 1f) * h
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, CyberColors.Cyan.copy(alpha = .035f), Color.Transparent), scan - 35.dp.toPx(), scan + 35.dp.toPx()))
        val edge = 13.dp.toPx()
        listOf(.18f, .82f).forEach { y ->
            drawLine(CyberColors.Yellow.copy(alpha = .45f), Offset(edge, h * y), Offset(edge + 18.dp.toPx(), h * y), 2.dp.toPx())
            drawLine(CyberColors.Red.copy(alpha = .35f), Offset(w - edge, h * y), Offset(w - edge, h * y + 20.dp.toPx()), 2.dp.toPx())
        }
        repeat(12) { i ->
            val lit = ((p * 24).toInt() + i) % 7 < 3
            drawLine(if (lit) CyberColors.Cyan.copy(alpha = .4f) else CyberColors.Cyan.copy(alpha = .09f),
                Offset(edge, h * .57f + i * 5.dp.toPx()), Offset(edge + if (i % 3 == 0) 8.dp.toPx() else 4.dp.toPx(), h * .57f + i * 5.dp.toPx()), 1.dp.toPx())
        }
    }
}
