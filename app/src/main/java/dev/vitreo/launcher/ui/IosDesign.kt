package dev.vitreo.launcher.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.homePageIndicatorStart
import kotlin.math.abs

/** Original Android implementation; reference decisions and pinned sources are in docs/IOS_DESIGN.md. */
internal object IosDesign {
    const val HomeInset = 20f
    const val DockInset = 12f
    const val DockInnerInset = 8f
    const val SearchHeight = 50f
    const val CardRadius = 32f
    fun dockHeight(icon: Float) = maxOf(90f, icon + 30f)
}

internal val LocalSurfaceReveal = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(1f) }

/** Read animation only in the draw phase; no per-frame bitmap work or staggered coroutines. */
@Composable
internal fun Modifier.iosControlReveal(index: Int): Modifier {
    if (LocalCyber.current || LocalGlass.current.appearance.reducedEffects) return this
    val progress = LocalSurfaceReveal.current
    return graphicsLayer {
        val delay = index.coerceIn(0, 5) * .025f
        val p = ((progress.value - delay) / (1f - delay)).coerceIn(0f, 1f)
        alpha = (p * 2.5f).coerceAtMost(1f)
        scaleX = .94f + .06f * p; scaleY = scaleX
        translationY = (1f - p) * 18.dp.toPx()
        transformOrigin = TransformOrigin(.5f, 0f)
    }
}

@Composable
internal fun HomePageControl(position: () -> Float, pages: Int, showPages: Boolean, onSearch: () -> Unit) {
    val ink = LocalGlassContentColor.current
    val reduced = LocalGlass.current.appearance.reducedEffects
    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
        GlassPanel(radius = 18f, tint = .1f, interactive = !showPages) {
            AnimatedContent(showPages, transitionSpec = {
                (fadeIn(tween(if (reduced) 0 else 120)) togetherWith fadeOut(tween(if (reduced) 0 else 90)))
                    .using(SizeTransform { _, _ -> if (reduced) tween(0) else LauncherMotion.panel() })
            }, label = "Home search / pages") { dots ->
                if (dots) {
                    val count = pages.coerceIn(1, 7)
                    Canvas(Modifier.width((count * 13 + 18).dp).height(32.dp).testTag("home_page_indicator")
                        .semantics { contentDescription = "Home page ${position().toInt().coerceIn(1, pages.coerceAtLeast(1))} of $pages" }) {
                        val offset = position()
                        val first = homePageIndicatorStart(offset, pages)
                        val gap = 13.dp.toPx()
                        repeat(count) { i ->
                            val active = (1f - abs(offset - (first + i + 1))).coerceIn(0f, 1f)
                            val edge = if (pages > count && (i == 0 && first > 0 || i == count - 1 && first + count < pages)) .72f else 1f
                            drawCircle(ink.copy(alpha = .32f + active * .68f), (2.6f + active * .5f).dp.toPx() * edge,
                                Offset(size.width / 2 + (i - (count - 1) / 2f) * gap, size.height / 2))
                        }
                    }
                } else Row(Modifier.height(32.dp).testTag("library_button").clickable(onClick = onSearch)
                    .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LauncherSymbol(Icons.Outlined.Search, null, Modifier.size(14.dp), tint = ink)
                    Text("Search", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
    }
}
