package dev.vitreo.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Keep the physical camera inset, but paint only a narrow HUD rail behind its symbols. */
@Composable
internal fun Modifier.cyberStatusChrome(powerSave: Boolean): Modifier {
    if (!LocalCyber.current) return this
    val appearance = LocalGlass.current.appearance
    val phase = LocalCyberPhase.current
    val moving = appearance.cyberImmersive && appearance.cyberEffects && !appearance.reducedEffects && !powerSave
    return drawWithContent {
        val height = minOf(size.height, 22.dp.toPx())
        val top = (size.height - height) / 2f
        val bottom = top + height
        drawRect(Brush.horizontalGradient(listOf(CyberColors.Background.copy(alpha = .72f),
            CyberColors.Panel.copy(alpha = .38f), CyberColors.Background.copy(alpha = .72f))),
            topLeft = Offset(0f, top), size = Size(size.width, height))
        drawLine(CyberColors.Cyan.copy(alpha = .26f), Offset(12.dp.toPx(), bottom),
            Offset(size.width - 12.dp.toPx(), bottom), .5.dp.toPx())
        repeat(3) { i ->
            val x = (5 + i * 3).dp.toPx()
            drawLine(CyberColors.Cyan.copy(alpha = .55f), Offset(x, top + 5.dp.toPx()), Offset(x, top + 9.dp.toPx()), .7.dp.toPx())
            drawLine(CyberColors.Red.copy(alpha = .55f), Offset(size.width - x, bottom - 5.dp.toPx()),
                Offset(size.width - x, bottom - 9.dp.toPx()), .7.dp.toPx())
        }
        if (moving) {
            // Reuse the existing lifecycle/focus-gated ambience clock; no second animation loop.
            val width = 36.dp.toPx()
            val x = phase.value * (size.width + width * 2) - width
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, CyberColors.Cyan.copy(alpha = .8f), Color.Transparent), x - width, x + width),
                Offset(x - width, bottom), Offset(x + width, bottom), 1.dp.toPx())
        }
        drawContent()
    }
}
