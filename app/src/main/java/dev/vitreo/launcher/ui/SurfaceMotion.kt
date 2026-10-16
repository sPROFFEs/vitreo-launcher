package dev.vitreo.launcher.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/** Motion roles adapted from Folio's MIT FolioTokens.kt (see third_party/folio/PROVENANCE.md). */
object LauncherMotion {
    fun <T> panel() = spring<T>(dampingRatio = .9f, stiffness = 380f)
    fun <T> menu() = spring<T>(dampingRatio = .78f, stiffness = 650f)
    fun <T> close() = spring<T>(dampingRatio = 1f, stiffness = 700f)
}

enum class SurfaceKind { Folder, Category, Search, Controls, Page }

/** One progress drives both directions, including an interrupted close and a finger-driven dismissal.
 * Keep the outgoing content alive until its last frame, including a folder's icon and page position. */
@Composable
fun <T : Any> SurfaceHost(target: T?, kind: (T) -> SurfaceKind, origin: TransformOrigin,
    onClose: () -> Unit, progressChanged: (Float) -> Unit = {}, content: @Composable (T) -> Unit) {
    var retained by remember { mutableStateOf(target) }
    val displayed = target ?: retained
    val progress = remember { Animatable(0f) }
    var pull by remember { mutableFloatStateOf(0f) }
    val reduced = LocalGlass.current.appearance.reducedEffects
    val close by rememberUpdatedState(onClose)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val currentTarget by rememberUpdatedState(target)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) scope.launch {
                // A frame clock can stop midway through a sheet animation behind another app.
                // Settle offscreen, so returning cannot expose an invisible modal barrier.
                progress.snapTo(if (currentTarget == null) 0f else 1f)
                retained = currentTarget; pull = 0f
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(target, reduced) {
        if (target != null) retained = target
        progress.animateTo(if (target != null) 1f else 0f,
            if (reduced) tween(120) else if (target == null) LauncherMotion.close() else LauncherMotion.panel())
        if (target == null) retained = null
    }
    // Notify only the draw layer's state; the workspace does not recompose with each frame.
    LaunchedEffect(Unit) { snapshotFlow { (progress.value * (1f - pull)).coerceIn(0f, 1f) }.collect(progressChanged) }
    if (displayed != null) {
        val style = kind(displayed)
        val gesture = if (style == SurfaceKind.Page || reduced || target == null) Modifier else Modifier.pointerInput(style) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                // Scrolling app grids and text fields keep their gestures. The header is a dismissal handle.
                if (down.position.y > 170 * density && style != SurfaceKind.Folder) return@awaitEachGesture
                var dragging = false
                var delta = 0f
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val distance = change.position - down.position
                    if (!dragging && (change.isConsumed || abs(distance.x) > abs(distance.y))) break
                    if (distance.y > viewConfiguration.touchSlop) dragging = true
                    if (dragging) {
                        change.consume()
                        delta = distance.y.coerceAtLeast(0f)
                        pull = (delta / (size.height * .65f)).coerceIn(0f, .85f)
                    }
                } while (true)
                if (dragging) scope.launch {
                    progress.snapTo(progress.value * (1f - pull)); pull = 0f
                    if (delta > 78 * density) close()
                    else progress.animateTo(1f, LauncherMotion.panel())
                }
            }
        }
        Box(Modifier.fillMaxSize().then(gesture).graphicsLayer {
            val p = (progress.value * (1f - pull)).coerceIn(0f, 1f)
            alpha = (p * 2.5f).coerceIn(0f, 1f)
            if (!reduced) {
                val start = when (style) {
                    SurfaceKind.Folder -> .25f
                    SurfaceKind.Category -> .72f
                    else -> 1f
                }
                scaleX = start + (1f - start) * p; scaleY = scaleX
                transformOrigin = if (style == SurfaceKind.Folder || style == SurfaceKind.Category) origin else TransformOrigin.Center
                translationY = when (style) {
                    SurfaceKind.Search -> -(1f - p) * 65 * density
                    SurfaceKind.Controls -> -(1f - p) * size.height * .3f
                    SurfaceKind.Page -> (1f - p) * size.height * .16f
                    else -> 0f
                }
            }
        }) {
            AnimatedContent(displayed, transitionSpec = {
                if (reduced) fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                else (fadeIn(tween(150)) + slideInHorizontally(LauncherMotion.panel()) { it / 9 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(LauncherMotion.close()) { -it / 12 })
            }, label = "surface navigation") { content(it) }
        }
    }
}
