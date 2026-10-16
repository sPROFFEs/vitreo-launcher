@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.vitreo.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.data.AppRecord

fun entryLabel(entry: HomeEntry, apps: Map<String, AppRecord>): String = when (entry) {
    is HomeEntry.Folder -> entry.title
    is HomeEntry.App -> apps[entry.id]?.label ?: entry.id.substringBefore('/').substringAfterLast('.')
}

@Composable
fun EntryIcon(entry: HomeEntry, apps: Map<String, AppRecord>, size: Dp, modifier: Modifier = Modifier) {
    val appearance = LocalGlass.current.appearance
    Box(modifier.size(size).testTag("icon_art:${entry.id}").shadow(size * .05f, IconShape, ambientColor = Color.Black.copy(alpha = .2f),
        spotColor = Color.Black.copy(alpha = .22f)).clip(IconShape)) {
        if (entry is HomeEntry.Folder) {
            GlassPanel(Modifier.fillMaxSize(), radius = size.value * .25f, tint = .12f) {}
            Column(Modifier.fillMaxSize().padding(size * .10f), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(3) { column ->
                            val id = entry.apps.getOrNull(row * 3 + column)
                            val cell = size * .22f
                            if (id == null) Spacer(Modifier.size(cell))
                            else EntryIcon(HomeEntry.App(id), apps, cell)
                        }
                    }
                }
            }
        } else {
            val app = apps[entry.id]
            val kind = if (appearance.iosIcons) commonIconKind(app?.packageName.orEmpty()) else null
            val style = appearance.iconStyle
            if (kind != null) {
                IosAppIcon(kind, style, Color(appearance.tintColor), Modifier.fillMaxSize())
            } else {
            if (style == 3) GlassPanel(Modifier.fillMaxSize(), radius = size.value * .25f, tint = .08f) {}
            else Box(Modifier.fillMaxSize().background(if (style == 1 || style == 2) Color(0xff1e2229) else Color.White))
            val image = if (style != 0) app?.glyph ?: app?.icon else app?.icon
            if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(),
                colorFilter = when {
                    style == 2 && app?.glyph != null -> ColorFilter.tint(Color(appearance.tintColor))
                    style == 3 && app?.glyph != null -> ColorFilter.tint(Color.White)
                    style > 1 -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    else -> null
                })
            else Icon(Icons.Outlined.Android, null, Modifier.size(size * .58f).align(Alignment.Center),
                tint = if (style == 0) Color(0xff7c8999) else Color(appearance.tintColor))
            if (style > 0) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .12f), Color.Transparent, Color.Black.copy(alpha = .07f)))))
            }
        }
    }
}

@Composable
fun LauncherTile(entry: HomeEntry, apps: Map<String, AppRecord>, iconSize: Dp, labels: Boolean,
    modifier: Modifier = Modifier, editing: Boolean = false, dragging: Boolean = false,
    onClick: (Rect?) -> Unit, onActions: (Rect?) -> Unit, onBounds: (Rect) -> Unit = {}, onRemove: (() -> Unit)? = null) {
    val appearance = LocalGlass.current.appearance
    val angle = if (editing && !appearance.reducedEffects) {
        val transition = rememberInfiniteTransition(label = "jiggle")
        val value by transition.animateFloat(-1.1f, 1.1f,
            infiniteRepeatable(tween(150 + (entry.id.hashCode().ushr(1) % 60)), RepeatMode.Reverse), label = "icon")
        value
    } else 0f
    val label = entryLabel(entry, apps)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var iconBounds by remember { mutableStateOf<Rect?>(null) }
    val scale by animateFloatAsState(if (pressed && !editing && !appearance.reducedEffects) .91f else 1f,
        if (appearance.reducedEffects) tween(0) else spring(dampingRatio = .72f, stiffness = 650f), label = "icon press")
    val contentColor = LocalGlassContentColor.current
    Column(modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }
        .semantics {
            if (!labels) contentDescription = label
            if (entry is HomeEntry.App && apps[entry.id] == null) stateDescription = "Unavailable"
        }.combinedClickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = { onClick(iconBounds) },
            onLongClickLabel = "Actions for $label", onLongClick = if (editing) null else ({ onActions(iconBounds) }))
        .alpha(if (dragging) .2f else if (entry is HomeEntry.App && apps[entry.id] == null) .5f else 1f)
        .padding(top = 7.dp, bottom = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.onGloballyPositioned { iconBounds = it.boundsInRoot() }.rotate(angle).scale(scale)) {
            EntryIcon(entry, apps, iconSize)
            if (editing && onRemove != null) Box(Modifier.offset((-5).dp, (-5).dp).size(23.dp)
                .background(Color(0xffd5d7db), CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Remove, "Remove $label", Modifier.size(17.dp), tint = Color(0xff303238))
            }
        }
        if (labels) Text(label, Modifier.fillMaxWidth().padding(top = 6.dp, start = 1.dp, end = 1.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp, color = contentColor,
                shadow = if (contentColor == Color.White) Shadow(Color.Black.copy(alpha = .6f), blurRadius = 6f) else null))
    }
}
