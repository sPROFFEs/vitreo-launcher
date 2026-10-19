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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/** Motion roles adapted from Folio's MIT FolioTokens.kt (see third_party/folio/PROVENANCE.md). */
object LauncherMotion {
    fun <T> panel(cyber: Boolean = false) = spring<T>(dampingRatio = if (cyber) 1f else .9f, stiffness = if (cyber) 1000f else 380f)
    fun <T> menu(cyber: Boolean = false) = spring<T>(dampingRatio = if (cyber) 1f else .78f, stiffness = if (cyber) 1200f else 650f)
    fun <T> close() = spring<T>(dampingRatio = 1f, stiffness = 700f)
}

enum class SurfaceKind { Folder, Category, Search, Controls, Notifications, Page }

/** One progress drives both directions, including an interrupted close and a finger-driven dismissal.
 * Keep the outgoing content alive until its last frame, including a folder's icon and page position. */
@Composable
fun <T : Any> SurfaceHost(target: T?, kind: (T) -> SurfaceKind, origin: TransformOrigin,
    onClose: () -> Unit, progressChanged: (Float) -> Unit = {}, forward: Boolean = true, openingState: State<Float?>? = null, content: @Composable (T) -> Unit) {
    val openingProgress = openingState?.value
    var retained by remember { mutableStateOf(target) }
    val displayed = target ?: retained
    val progress = remember { Animatable(0f) }
    var lastOpeningProgress by remember { mutableStateOf<Float?>(null) }
    SideEffect { if (openingProgress != null) lastOpeningProgress = openingProgress }
    var fingerProgress by remember { mutableStateOf<Float?>(null) }
    var releaseVelocity by remember { mutableStateOf<Float?>(null) }
    var dismissDown by remember { mutableStateOf(false) }
    var handoffProgress by remember { mutableFloatStateOf(1f) }
    var translationCorrection by remember { mutableFloatStateOf(0f) }
    val reduced = LocalGlass.current.appearance.reducedEffects
    val cyber = LocalCyber.current
    val close by rememberUpdatedState(onClose)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val currentTarget by rememberUpdatedState(target)
    val currentOpening by rememberUpdatedState(openingProgress)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) scope.launch {
                // A frame clock can stop midway through a sheet animation behind another app.
                // Settle offscreen, so returning cannot expose an invisible modal barrier.
                progress.snapTo(if (currentTarget == null) 0f else 1f)
                retained = currentTarget; fingerProgress = null; releaseVelocity = null; dismissDown = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(target, reduced, openingProgress != null) {
        // Let Animatable interrupt its preceding mutation. Cancelling the
        // animation's coroutine on every target change would zero its velocity
        // before the next spring can inherit it. This scope still dies with UI.
        scope.launch {
            if (currentTarget != target) return@launch
            if (target != null) retained = target
            if (openingProgress != null) { progress.stop(); return@launch }
            lastOpeningProgress?.let { progress.snapTo(it); lastOpeningProgress = null }
            fingerProgress?.let { progress.snapTo(it); fingerProgress = null }
            val velocity = releaseVelocity ?: progress.velocity
            releaseVelocity = null
            progress.animateTo(if (target != null) 1f else 0f,
                if (reduced) tween(120) else if (target == null) LauncherMotion.close() else LauncherMotion.panel(cyber),
                initialVelocity = if (reduced) 0f else velocity)
            dismissDown = false
            if (target == null) retained = null
        }
    }
    // Scrim/blur use bounded opacity; geometry keeps the spring's small overshoot.
    LaunchedEffect(Unit) { snapshotFlow { (fingerProgress ?: currentOpening ?: lastOpeningProgress ?: progress.value).coerceIn(0f, 1f) }.collect(progressChanged) }
    if (displayed != null) {
        val style = kind(displayed)
        val gesture = if (style == SurfaceKind.Page || target == null) Modifier else Modifier.pointerInput(style, cyber) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                // Scrolling app grids and text fields keep their gestures. The header is a dismissal handle.
                if (style == SurfaceKind.Controls || style == SurfaceKind.Notifications) {
                    if (down.position.y < size.height - 100 * density) return@awaitEachGesture
                } else if (down.position.y > 170 * density && style != SurfaceKind.Folder) return@awaitEachGesture
                val direction = if (style == SurfaceKind.Controls || style == SurfaceKind.Notifications) -1f else 1f
                var dragging = false
                var released = false
                var delta = 0f
                var start = 1f
                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) { released = change.changedToUpIgnoreConsumed(); break }
                        val distance = change.position - down.position
                        if (!dragging && (change.isConsumed || abs(distance.x) > abs(distance.y))) break
                        if (!dragging && distance.y * direction > viewConfiguration.touchSlop) {
                            dragging = true
                            start = progress.value
                            val openingTravel = when (style) {
                                SurfaceKind.Search -> -65 * density
                                SurfaceKind.Controls, SurfaceKind.Notifications -> -size.height * .58f
                                else -> 0f
                            }
                            val closingTravel = when (style) {
                                SurfaceKind.Search -> size.height * .22f
                                SurfaceKind.Controls, SurfaceKind.Notifications -> -size.height * .58f
                                else -> 0f
                            }
                            val previousBlend = if (start <= handoffProgress) start / handoffProgress.coerceAtLeast(.001f)
                                else (1f - start) / (1f - handoffProgress).coerceAtLeast(.001f)
                            val visibleTranslation = (1f - start) * (if (dismissDown) closingTravel else openingTravel) +
                                if (dismissDown) translationCorrection * previousBlend else 0f
                            translationCorrection = visibleTranslation - (1f - start) * closingTravel
                            handoffProgress = start
                            dismissDown = true
                            scope.launch { progress.stop() }
                        }
                        if (dragging) {
                            change.consume()
                            delta = (distance.y * direction).coerceAtLeast(0f)
                            fingerProgress = (start - delta / (size.height * .65f)).coerceIn(.05f, 1.05f)
                        }
                    } while (true)
                } finally {
                    if (dragging) {
                        val velocity = if (released) tracker.calculateVelocity().y * direction else 0f
                        val dismiss = released && dev.vitreo.launcher.core.shouldDismissSurface(delta / density, velocity / density)
                        val normalized = (-velocity / (size.height * .65f)).coerceIn(-8f, 8f)
                        scope.launch {
                            val value = fingerProgress ?: progress.value
                            progress.snapTo(value)
                            fingerProgress = null
                            if (dismiss && currentTarget != null) {
                                releaseVelocity = normalized
                                close()
                            } else if (currentTarget != null) {
                                progress.animateTo(1f, LauncherMotion.panel(cyber), initialVelocity = normalized)
                                dismissDown = false
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().then(gesture).graphicsLayer {
            val p = (fingerProgress ?: openingProgress ?: lastOpeningProgress ?: progress.value).coerceIn(-.05f, 1.06f)
            alpha = (p * 2.5f).coerceIn(0f, 1f)
            if (!reduced) {
                val start = when (style) {
                    SurfaceKind.Folder -> if (cyber) .94f else .25f
                    SurfaceKind.Category -> if (cyber) .97f else .72f
                    else -> 1f
                }
                scaleX = start + (1f - start) * p; scaleY = scaleX
                transformOrigin = if (style == SurfaceKind.Folder || style == SurfaceKind.Category) origin else TransformOrigin.Center
                translationY = when (style) {
                    SurfaceKind.Search -> (1f - p) * if (dismissDown) size.height * .22f else -65 * density
                    SurfaceKind.Controls, SurfaceKind.Notifications -> (1f - p) * size.height * -.58f
                    SurfaceKind.Page -> (1f - p) * size.height * .16f
                    else -> 0f
                }
                if (dismissDown && translationCorrection != 0f) {
                    // Preserve the visible position if a drag interrupts entry.
                    // The correction vanishes at either resting endpoint.
                    val blend = if (p <= handoffProgress) p / handoffProgress.coerceAtLeast(.001f)
                        else (1f - p) / (1f - handoffProgress).coerceAtLeast(.001f)
                    translationY += translationCorrection * blend
                }
            }
        }) {
            AnimatedContent(displayed, transitionSpec = {
                if (reduced) fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                else (fadeIn(tween(150)) + slideInHorizontally(LauncherMotion.panel(cyber)) { if (forward) it / 7 else -it / 10 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(LauncherMotion.close()) { if (forward) -it / 10 else it / 7 })
            }, label = "surface navigation") { content(it) }
        }
    }
}
