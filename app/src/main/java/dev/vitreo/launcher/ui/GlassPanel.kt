package dev.vitreo.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.graphics.GlassView
import dev.vitreo.launcher.graphics.Viewport
import dev.vitreo.launcher.graphics.WallpaperFrame

data class GlassEnvironment(val frame: WallpaperFrame?, val viewport: Viewport, val appearance: Appearance)
val LocalGlass = staticCompositionLocalOf { GlassEnvironment(null, Viewport(), Appearance()) }

@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Float = 28f, tint: Float = .18f, content: @Composable BoxScope.() -> Unit) {
    val glass = LocalGlass.current
    Box(modifier.clip(RoundedCornerShape(radius.dp))) {
        AndroidView(factory = { GlassView(it) }, modifier = Modifier.matchParentSize(),
            update = { it.configure(glass.frame, glass.viewport, glass.appearance, radius, tint) })
        content()
    }
}
