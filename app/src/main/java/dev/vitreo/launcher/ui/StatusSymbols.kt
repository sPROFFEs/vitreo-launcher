package dev.vitreo.launcher.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/** Original status-sized geometry, separately drawn for the rounded and angular themes. */
internal object StatusSymbols {
    private fun glyph(name: String, data: String, filled: Boolean, angular: Boolean = false) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(PathParser().parsePathString(data).toNodes(), pathFillType = PathFillType.EvenOdd,
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = if (filled) null else SolidColor(Color.White), strokeLineWidth = 2.2f,
                strokeLineCap = if (angular) StrokeCap.Square else StrokeCap.Round,
                strokeLineJoin = if (angular) StrokeJoin.Miter else StrokeJoin.Round)
        }.build()
    val wifi = glyph("Vitreo.Status.Wifi", "M2.5 8.5 Q12 1 21.5 8.5 M6 12 Q12 7.2 18 12 M9.3 15.5 Q12 13.2 14.7 15.5 M11.9 19 L12.1 19", false)
    val cyberWifi = glyph("Vitreo.Status.CyberWifi", "M2 8 L7 4 L17 4 L22 8 M6 12 L9 9 L15 9 L18 12 M10 16 L12 14 L14 16 M12 20 L12 20.5", false, true)
    val lock = glyph("Vitreo.Status.Lock", "M8 10 L8 7 A4 4 0 0 1 16 7 L16 10 L18 10 L18 7 A6 6 0 0 0 6 7 L6 10 Z M6 10 L18 10 Q20 10 20 12 L20 20 Q20 22 18 22 L6 22 Q4 22 4 20 L4 12 Q4 10 6 10 Z M12 13 A1.2 1.2 0 1 0 12 15.4 A1.2 1.2 0 1 0 12 13 Z", true)
    val cyberLock = glyph("Vitreo.Status.CyberLock", "M7 10 L7 6 L10 3 L14 3 L17 6 L17 10 M5 10 L19 10 L21 12 L21 21 L3 21 L3 12 Z M12 14 L12 18", false, true)
}

@Composable
internal fun StatusWifi(modifier: Modifier = Modifier, description: String? = null, tint: Color = LocalGlassContentColor.current) {
    val cyber = LocalCyber.current
    Icon(if (cyber) StatusSymbols.cyberWifi else StatusSymbols.wifi, description, modifier,
        if (cyber) CyberColors.Cyan else tint)
}

@Composable
internal fun StatusLock(modifier: Modifier = Modifier, description: String? = null, tint: Color = LocalGlassContentColor.current) {
    val cyber = LocalCyber.current
    Icon(if (cyber) StatusSymbols.cyberLock else StatusSymbols.lock, description, modifier,
        if (cyber) CyberColors.Cyan else tint)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun statusBandHeight() = with(LocalDensity.current) {
    maxOf(28.dp, WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp(), WindowInsets.displayCutout.getTop(this).toDp())
}

@Composable
internal fun StatusBattery(level: Int, charging: Boolean, modifier: Modifier = Modifier,
    percentage: Boolean = true, tint: Color = LocalGlassContentColor.current) {
    val cyber = LocalCyber.current
    val value = level.coerceIn(0, 100)
    if (cyber && percentage) {
        Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value.toString(), color = CyberColors.Cyan, fontFamily = CyberFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            StatusBattery(value, charging, percentage = false, tint = tint)
        }
        return
    }
    val paint = remember(cyber) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(if (cyber) "monospace" else "sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    } }
    Canvas(modifier.size(30.dp, 15.dp).semantics {
        contentDescription = "Battery $value percent${if (charging) ", charging" else ""}"
    }) {
        val unit = size.height / 15f
        val bodyWidth = size.width - 3f * unit
        val radius = if (cyber) CornerRadius.Zero else CornerRadius(3.4f * unit)
        val ink = if (cyber) CyberColors.Cyan else tint
        val fill = when { value <= 20 && !charging -> if (cyber) CyberColors.Red else Color(0xffff453a)
            charging -> if (cyber) CyberColors.Yellow else Color(0xff30d158)
            else -> ink }
        drawRoundRect(ink.copy(alpha = .5f), size = Size(bodyWidth, size.height), cornerRadius = radius, style = Stroke(unit))
        val innerWidth = (bodyWidth - 4f * unit).coerceAtLeast(0f)
        if (cyber) {
            val gap = unit
            val segment = ((innerWidth - gap * 4) / 5).coerceAtLeast(0f)
            repeat(5) { i ->
                drawRect(fill.copy(alpha = if (value >= (i + 1) * 20 || i == 0 && value > 0) .95f else .12f),
                    Offset(2f * unit + i * (segment + gap), 2f * unit), Size(segment, size.height - 4f * unit))
            }
        } else drawRoundRect(fill, Offset(2f * unit, 2f * unit), Size(innerWidth * value / 100, size.height - 4f * unit), CornerRadius(1.7f * unit))
        drawRoundRect(ink.copy(alpha = .55f), Offset(size.width - 1.6f * unit, size.height * .33f),
            Size(1.6f * unit, size.height * .34f), if (cyber) CornerRadius.Zero else CornerRadius(unit))
        if (percentage) drawIntoCanvas { canvas ->
            paint.textSize = 9f * unit
            val baseline = (size.height - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2
            val native = canvas.nativeCanvas
            paint.color = ink.toArgb()
            native.drawText(value.toString(), bodyWidth / 2, baseline, paint)
            val saved = native.save()
            native.clipRect(2f * unit, 2f * unit, 2f * unit + innerWidth * value / 100, size.height - 2f * unit)
            paint.color = (if (fill.luminance() > .4f) Color.Black else Color.White).toArgb()
            native.drawText(value.toString(), bodyWidth / 2, baseline, paint)
            native.restoreToCount(saved)
        }
    }
}
