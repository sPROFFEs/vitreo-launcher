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
            addGroup(name = "Proportions", scaleY = if (name == "Vitreo.Status.Wifi") .9f else 1f, pivotY = 12f)
            addPath(PathParser().parsePathString(data).toNodes(), pathFillType = PathFillType.EvenOdd,
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = if (filled) null else SolidColor(Color.White), strokeLineWidth = 2.2f,
                strokeLineCap = if (angular) StrokeCap.Square else StrokeCap.Round,
                strokeLineJoin = if (angular) StrokeJoin.Miter else StrokeJoin.Round)
            clearGroup()
        }.build()
    // Two filled bands and the rounded sector below, rather than three stroked arcs + a dot.
    val wifi = glyph("Vitreo.Status.Wifi", "M1.2 7.4 Q12 -1.4 22.8 7.4 Q23.2 7.8 22.8 8.2 L21.2 9.8 Q20.8 10.2 20.4 9.8 Q12 3 3.6 9.8 Q3.2 10.2 2.8 9.8 L1.2 8.2 Q0.8 7.8 1.2 7.4 Z M5.2 11.7 Q12 6.2 18.8 11.7 Q19.2 12.1 18.8 12.5 L17.2 14.1 Q16.8 14.5 16.4 14.1 Q12 10.6 7.6 14.1 Q7.2 14.5 6.8 14.1 L5.2 12.5 Q4.8 12.1 5.2 11.7 Z M9.1 16 Q12 13.7 14.9 16 Q15.3 16.4 14.9 16.8 L12.7 19.1 Q12 19.8 11.3 19.1 L9.1 16.8 Q8.7 16.4 9.1 16 Z", true)
    val cyberWifi = glyph("Vitreo.Status.CyberWifi", "M2 8 L7 4 L17 4 L22 8 M6 12 L9 9 L15 9 L18 12 M10 16 L12 14 L14 16 M12 20 L12 20.5", false, true)
    val lock = glyph("Vitreo.Status.Lock", "M6.5 10 L6.5 7 A5.5 5.5 0 0 1 17.5 7 L17.5 10 L15 10 L15 7 A3 3 0 0 0 9 7 L9 10 Z M6 10 L18 10 Q20 10 20 12 L20 21 Q20 23 18 23 L6 23 Q4 23 4 21 L4 12 Q4 10 6 10 Z", true)
    val camera = glyph("Vitreo.Lock.Camera", "M4 6 L7 6 L8.5 3.5 L15.5 3.5 L17 6 L20 6 Q22 6 22 8 L22 19 Q22 21 20 21 L4 21 Q2 21 2 19 L2 8 Q2 6 4 6 Z M16.5 13.5 A4.5 4.5 0 1 0 7.5 13.5 A4.5 4.5 0 1 0 16.5 13.5 Z M14.8 13.5 A2.8 2.8 0 1 0 9.2 13.5 A2.8 2.8 0 1 0 14.8 13.5 Z", true)
    val flashlight = glyph("Vitreo.Lock.Flashlight", "M7 1 L17 1 Q18 1 18 2 L18 4 L6 4 L6 2 Q6 1 7 1 Z M6 5.5 L18 5.5 L15 10 L15 21 Q15 23 13 23 L11 23 Q9 23 9 21 L9 10 Z M11 12 L11 16 Q12 18 13 16 L13 12 Z", true)
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
internal fun statusBandHeight(appearance: dev.vitreo.launcher.core.Appearance? = null) = with(LocalDensity.current) {
    val minimum = if (appearance?.themePreset == 1) maxOf(28f, appearance.cyberStatusHeight).dp else 28.dp
    maxOf(minimum, WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp(), WindowInsets.displayCutout.getTop(this).toDp())
}

@Composable
internal fun StatusBattery(level: Int, charging: Boolean, modifier: Modifier = Modifier,
    percentage: Boolean = true, tint: Color = LocalGlassContentColor.current, powerSave: Boolean = false) {
    val cyber = LocalCyber.current
    val value = level.coerceIn(0, 100)
    if (cyber && percentage) {
        Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value.toString(), color = CyberColors.Cyan, fontFamily = CyberFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            StatusBattery(value, charging, percentage = false, tint = tint, powerSave = powerSave)
        }
        return
    }
    val paint = remember(cyber) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(if (cyber) "monospace" else "sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    } }
    Canvas(modifier.size(27.dp, 13.dp).semantics {
        contentDescription = "Battery $value percent${if (charging) ", charging" else ""}"
    }) {
        val unit = size.height / 13f
        val bodyWidth = size.width - 3f * unit
        val radius = if (cyber) CornerRadius.Zero else CornerRadius(3.6f * unit)
        val ink = if (cyber) CyberColors.Cyan else tint
        val fill = when { powerSave && !charging -> if (cyber) CyberColors.Yellow else Color(0xffffd60a)
            value <= 20 && !charging -> if (cyber) CyberColors.Red else Color(0xffff453a)
            charging -> if (cyber) CyberColors.Yellow else Color(0xff30d158)
            else -> ink }
        drawRoundRect(ink.copy(alpha = .5f), topLeft = Offset(.65f * unit, .65f * unit),
            size = Size(bodyWidth - 1.3f * unit, size.height - 1.3f * unit), cornerRadius = radius, style = Stroke(1.3f * unit))
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
