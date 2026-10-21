package dev.vitreo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.graphics.GlassRenderer
import dev.vitreo.launcher.graphics.Viewport
import dev.vitreo.launcher.graphics.WallpaperFrame

data class GlassEnvironment(val frame: WallpaperFrame?, val viewport: Viewport, val appearance: Appearance, val motion: State<*>? = null)
val LocalGlass = staticCompositionLocalOf { GlassEnvironment(null, Viewport(), Appearance()) }
enum class GlassMaterial { Clear, Regular }
val LocalGlassContentColor = staticCompositionLocalOf { Color.White }

@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Float = 28f, tint: Float = .18f,
    material: GlassMaterial = GlassMaterial.Clear, interactive: Boolean = false, contentColor: Color? = null, content: @Composable BoxScope.() -> Unit) {
    if (LocalCyber.current) { CyberPanel(modifier, content); return }
    val glass = LocalGlass.current
    val surfaceReveal = LocalSurfaceReveal.current
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset(.5f, .5f)) }
    val light = animateFloatAsState(if (pressed && !glass.appearance.reducedEffects) 1f else 0f,
        if (glass.appearance.reducedEffects) tween(0) else spring(dampingRatio = .85f, stiffness = 500f), label = "glass touch")
    val color = contentColor ?: if (material == GlassMaterial.Regular) MaterialTheme.colorScheme.onSurface else LocalGlassContentColor.current
    val gesture = if (!interactive) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touch = Offset(down.position.x / size.width.coerceAtLeast(1), down.position.y / size.height.coerceAtLeast(1))
            pressed = true
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    event.changes.firstOrNull { it.pressed }?.let {
                        touch = Offset((it.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (it.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f))
                    }
                } while (event.changes.any { it.pressed })
            } finally { pressed = false }
        }
    }
    val context = LocalContext.current
    val renderer = remember(context) { GlassRenderer(context) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.then(gesture).shadow(6.dp, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .12f)).graphicsLayer {
        scaleX = 1f + light.value * .008f; scaleY = scaleX
        rotationX = (touch.y - .5f) * light.value * -2.5f
        rotationY = (touch.x - .5f) * light.value * 2.5f
        cameraDistance = 16 * density
    }.clip(RoundedCornerShape(radius.dp))) {
        Canvas(Modifier.matchParentSize().onGloballyPositioned { coordinates = it }) {
            // Subscribe only this drawing to the parent's reveal, not the workspace.
            if (surfaceReveal.value <= 0f) return@Canvas
            glass.motion?.value // Snapshot observation: invalidate drawing when its parent moves.
            // Parent graphics layers can move without a layout pass. Sample the current
            // screen position at draw time so the lens stays attached to the wallpaper.
            val position = coordinates?.takeIf { it.isAttached }?.positionOnScreen() ?: Offset.Zero
            renderer.configure(glass.frame, glass.viewport, glass.appearance, radius, tint,
                material == GlassMaterial.Regular, touch.x, touch.y, light.value, foregroundDark = color.luminance() < .4f)
            drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), position.x, position.y) }
        }
        CompositionLocalProvider(LocalGlassContentColor provides color, LocalContentColor provides color) { content() }
    }
}
