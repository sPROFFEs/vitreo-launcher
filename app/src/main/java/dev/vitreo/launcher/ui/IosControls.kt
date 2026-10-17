package dev.vitreo.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

/** Keep Material's keyboard, accessibility and drag handling, with an iOS-sized track and thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, onDark: Boolean = false) {
    val active = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .25f)
    val inactive = if (onDark) Color.White.copy(alpha = .22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .13f)
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Slider(value, onChange, modifier.heightIn(min = 44.dp), enabled = enabled, valueRange = valueRange,
        thumb = {
            Box(Modifier.size(27.dp).shadow(if (enabled) 3.dp else 0.dp, CircleShape)
                .background(if (enabled) Color.White else Color(0xffdedee3), CircleShape)
                .border(.5.dp, Color.Black.copy(alpha = .08f), CircleShape))
        }, track = {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val y = size.height / 2f
                drawLine(inactive, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
                drawLine(active, Offset(0f, y), Offset(size.width * fraction, y), 4.dp.toPx(), StrokeCap.Round)
            }
        })
}

/** Continuous corners, independent of the Android device's adaptive icon mask. */
object IconShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        repeat(97) { index ->
            val angle = index * 2.0 * PI / 96
            val x = cos(angle); val y = sin(angle)
            val px = (size.width * .5 * (1 + sign(x) * abs(x).pow(.42))).toFloat()
            val py = (size.height * .5 * (1 + sign(y) * abs(y).pow(.42))).toFloat()
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun IosSwitch(checked: Boolean, enabled: Boolean = true) {
    val reduced = LocalGlass.current.appearance.reducedEffects
    val x by animateDpAsState(if (checked) 22.dp else 2.dp,
        if (reduced) tween(0) else spring(dampingRatio = .85f, stiffness = 650f), label = "switch")
    Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(
        if (checked) Color(0xff34c759).copy(alpha = if (enabled) 1f else .45f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = .15f))) {
        Box(Modifier.offset { IntOffset(x.roundToPx(), 2.dp.roundToPx()) }.size(27.dp).shadow(2.dp, CircleShape)
            .background(Color.White, CircleShape))
    }
}

@Composable
fun IosGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surface), content = content)
}

@Composable
fun IosRow(title: String, detail: String? = null, enabled: Boolean = true,
           destructive: Boolean = false, onClick: (() -> Unit)? = null) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) .07f else 0f))
        .then(if (onClick != null) Modifier.clickable(interactions, null, enabled = enabled, onClick = onClick) else Modifier)
        .heightIn(min = 52.dp).padding(horizontal = 17.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp,
            color = if (destructive) Color(0xffff453a) else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f))
        detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) }
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp).padding(start = 3.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
    }
}

@Composable
fun IosToggleRow(title: String, checked: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier,
                 onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = 54.dp).padding(horizontal = 17.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp).alpha(if (enabled) 1f else .5f), fontSize = 16.sp)
        IosSwitch(checked, enabled)
    }
}

@Composable
fun IosDivider() {
    HorizontalDivider(Modifier.padding(start = 17.dp), thickness = .5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))
}

@Composable
fun IosSectionLabel(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 17.dp, top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, letterSpacing = .4.sp)
}

@Composable
fun IosSegmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val progress = animateFloatAsState(selected.toFloat(),
        if (LocalGlass.current.appearance.reducedEffects) tween(0) else spring(dampingRatio = .85f, stiffness = 650f), label = "segment")
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)).padding(3.dp)) {
        val segmentWidth = maxWidth / options.size
        Box(Modifier.matchParentSize().graphicsLayer {
            translationX = segmentWidth.toPx() * progress.value.coerceIn(0f, (options.size - 1).toFloat())
        }.padding(end = segmentWidth * (options.size - 1))
            .shadow(1.dp, RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)))
        Row(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, title ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .selectable(selected = selected == index, role = Role.Tab, onClick = { onSelect(index) })
                .padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(title, fontSize = 12.sp, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        }
    }
}
