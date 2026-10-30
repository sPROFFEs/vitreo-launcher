package dev.vitreo.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vitreo.launcher.core.*
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
    val appearance = LocalGlass.current.appearance
    val active = appearance.cyberNightEffects

    Canvas(modifier.fillMaxSize().testTag("cyber_ambient")) {
        val p = phase.value
        val w = size.width; val h = size.height
        val drift = sin(p * Math.PI.toFloat() * 2)
        val pulse = .5f + .5f * drift

        // 1. NEON BLOOM: Atmosphere glow
        if (CYBER_EFFECT_BLOOM in active) {
            val bloomCenter = Offset(w * (.6f + drift * .15f), h * .65f)
            drawCircle(Brush.radialGradient(listOf(CyberColors.Cyan.copy(alpha = .035f + pulse * .025f), Color.Transparent),
                center = bloomCenter, radius = w * .75f), w * .75f, bloomCenter)
            val topBloomCenter = Offset(w * (.25f - drift * .1f), h * .2f)
            drawCircle(Brush.radialGradient(listOf(CyberColors.Red.copy(alpha = .02f + (1f - pulse) * .02f), Color.Transparent),
                center = topBloomCenter, radius = w * .55f), w * .55f, topBloomCenter)
        }

        // 2. CYBERSPACE MATRIX GRID: 3D Perspective floor grid
        if (CYBER_EFFECT_GRID in active) {
            val horizonY = h * 0.72f
            val gridAlpha = 0.08f + pulse * 0.04f
            val vanishX = w * 0.5f + drift * 20.dp.toPx()
            val numVLines = 9
            for (i in 0 until numVLines) {
                val bottomX = (w / (numVLines - 1)) * i
                drawLine(
                    Brush.verticalGradient(listOf(Color.Transparent, CyberColors.Cyan.copy(alpha = gridAlpha)), horizonY, h),
                    Offset(vanishX + (bottomX - vanishX) * 0.15f, horizonY),
                    Offset(bottomX, h),
                    1.dp.toPx()
                )
            }
            val numHLines = 6
            for (i in 0 until numHLines) {
                val t = ((p * 2.5f + i.toFloat() / numHLines) % 1f)
                val lineY = horizonY + (h - horizonY) * (t * t)
                val lineAlpha = gridAlpha * t * 1.5f
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, CyberColors.Cyan.copy(alpha = lineAlpha), Color.Transparent)),
                    Offset(0f, lineY),
                    Offset(w, lineY),
                    1.dp.toPx()
                )
            }
        }

        // 3. ACID RAIN: Deterministic neon rain streaks
        if (CYBER_EFFECT_RAIN in active) {
            repeat(38) { i ->
                val x = ((1f + i * .618034f + drift * .015f) % 1f) * w
                val y = ((i * .381966f + p * (3.2f + i % 3)) % 1f) * h
                val rainColor = if (i % 7 == 0) CyberColors.Yellow else CyberColors.Cyan
                val alpha = .055f + (i % 4) * .02f
                drawLine(rainColor.copy(alpha = alpha), Offset(x, y), Offset(x - 3.dp.toPx(), y + (10 + i % 12).dp.toPx()), 1.dp.toPx())
            }
        }

        // 4. SCANLINES & RADAR SWEEP
        if (CYBER_EFFECT_SCANLINE in active) {
            val scan = (p * 2f % 1f) * h
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, CyberColors.Cyan.copy(alpha = .04f), Color.Transparent), scan - 35.dp.toPx(), scan + 35.dp.toPx()))
            val step = 4.dp.toPx()
            var yScan = 0f
            while (yScan < h) {
                drawLine(Color.Black.copy(alpha = 0.04f), Offset(0f, yScan), Offset(w, yScan), 0.5f)
                yScan += step
            }
        }

        // 5. RELIC MALFUNCTION GLITCH: Cyberpunk 2077 optical interference slices
        if (CYBER_EFFECT_GLITCH in active) {
            val glitchPhase = (p * 7f) % 1f
            if (glitchPhase in 0.02f..0.09f || glitchPhase in 0.52f..0.57f) {
                val intensity = if (glitchPhase in 0.02f..0.09f) (glitchPhase - 0.02f) / 0.07f else (glitchPhase - 0.52f) / 0.05f
                val sliceY1 = (h * 0.28f + sin(p * 50f) * h * 0.15f).coerceIn(0f, h)
                val sliceH1 = (14.dp.toPx() * (1f + sin(p * 30f))).coerceAtLeast(4.dp.toPx())
                val sliceDisplacement = 12.dp.toPx() * sin(p * 80f)

                drawRect(CyberColors.Red.copy(alpha = 0.16f * intensity), Offset(sliceDisplacement, sliceY1), Size(w, sliceH1))
                drawRect(CyberColors.Cyan.copy(alpha = 0.14f * intensity), Offset(-sliceDisplacement * 0.7f, sliceY1 + 2.dp.toPx()), Size(w, sliceH1))

                val sliceY2 = (h * 0.65f - sin(p * 40f) * h * 0.12f).coerceIn(0f, h)
                val sliceH2 = 6.dp.toPx()
                drawRect(CyberColors.Yellow.copy(alpha = 0.22f * intensity), Offset(-sliceDisplacement * 1.2f, sliceY2), Size(w * 0.75f, sliceH2))

                for (k in 0..4) {
                    val ny = sliceY1 + k * 3.dp.toPx()
                    drawLine(CyberColors.Text.copy(alpha = 0.35f * intensity), Offset(w * 0.1f * k, ny), Offset(w * 0.1f * k + 25.dp.toPx(), ny), 1.5.dp.toPx())
                }
            }
        }

        // 6. KIROSHI OPTICS TELEMETRY & HUD RETICLES
        if (CYBER_EFFECT_TELEMETRY in active) {
            val edge = 13.dp.toPx()
            listOf(.18f, .82f).forEach { y ->
                drawLine(CyberColors.Yellow.copy(alpha = .45f), Offset(edge, h * y), Offset(edge + 18.dp.toPx(), h * y), 2.dp.toPx())
                drawLine(CyberColors.Red.copy(alpha = .35f), Offset(w - edge, h * y), Offset(w - edge, h * y + 20.dp.toPx()), 2.dp.toPx())
            }
            repeat(12) { i ->
                val lit = ((p * 24).toInt() + i) % 7 < 3
                drawLine(if (lit) CyberColors.Cyan.copy(alpha = .45f) else CyberColors.Cyan.copy(alpha = .09f),
                    Offset(edge, h * .57f + i * 5.dp.toPx()), Offset(edge + if (i % 3 == 0) 10.dp.toPx() else 5.dp.toPx(), h * .57f + i * 5.dp.toPx()), 1.dp.toPx())
            }
            val reticleX = w - 32.dp.toPx()
            val reticleY = h * 0.22f
            val rSize = 10.dp.toPx()
            val reticleAlpha = 0.25f + pulse * 0.15f
            drawLine(CyberColors.Cyan.copy(alpha = reticleAlpha), Offset(reticleX - rSize, reticleY), Offset(reticleX + rSize, reticleY), 1.dp.toPx())
            drawLine(CyberColors.Cyan.copy(alpha = reticleAlpha), Offset(reticleX, reticleY - rSize), Offset(reticleX, reticleY + rSize), 1.dp.toPx())
            drawCircle(CyberColors.Cyan.copy(alpha = reticleAlpha * 0.6f), radius = rSize * 0.6f, center = Offset(reticleX, reticleY), style = Stroke(1.dp.toPx()))

            val bX = edge + 4.dp.toPx()
            val bY = h * 0.45f
            val bLen = 8.dp.toPx()
            drawLine(CyberColors.Yellow.copy(alpha = 0.4f), Offset(bX, bY), Offset(bX + bLen, bY), 1.5.dp.toPx())
            drawLine(CyberColors.Yellow.copy(alpha = 0.4f), Offset(bX, bY), Offset(bX, bY + bLen), 1.5.dp.toPx())
        }
    }
}
