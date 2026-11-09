package dev.vitreo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.data.AppRecord

/** Pointer movement updates only this layer; lifting and landing share its scale. */
@Composable
internal fun FloatingHomeIcon(entry: HomeEntry, apps: Map<String, AppRecord>, size: Dp,
    lifted: Boolean, center: () -> Offset) {
    val reduced = LocalGlass.current.appearance.reducedEffects
    val cyber = LocalCyber.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale = animateFloatAsState(if (lifted && shown && !reduced) 1.08f else 1f,
        if (reduced) tween(0) else spring(dampingRatio = .86f, stiffness = 800f), label = "Icon lift")
    Box(Modifier.size(size).testTag(if (lifted) "home_drag_icon" else "home_landing_icon").graphicsLayer {
        val point = center()
        translationX = point.x - this.size.width / 2
        translationY = point.y - this.size.height / 2
        scaleX = scale.value; scaleY = scaleX
        shape = if (cyber) CyberShape else IconShape
        shadowElevation = if (reduced) 0f else (if (lifted) 12.dp else 3.dp).toPx()
    }) { EntryIcon(entry, apps, size) }
}
