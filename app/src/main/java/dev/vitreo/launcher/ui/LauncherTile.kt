@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.vitreo.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.data.AppRecord

fun entryLabel(entry: HomeEntry, apps: Map<String, AppRecord>): String = when (entry) {
    is HomeEntry.Folder -> entry.title
    is HomeEntry.App -> apps[entry.id]?.label ?: entry.id.substringBefore('/').substringAfterLast('.')
}

@Composable
fun EntryIcon(entry: HomeEntry, apps: Map<String, AppRecord>, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26)
    if (entry is HomeEntry.Folder) {
        Column(modifier.size(size).clip(shape).background(Color.White.copy(alpha = .17f))
            .border(1.dp, Color.White.copy(alpha = .28f), shape).padding(5.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            repeat(3) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(3) { column ->
                        val icon = entry.apps.getOrNull(row * 3 + column)?.let { apps[it]?.icon }
                        val cell = (size - 16.dp) / 3
                        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(cell).clip(RoundedCornerShape(3.dp)))
                        else Spacer(Modifier.size(cell))
                    }
                }
            }
        }
    } else {
        val icon = apps[entry.id]?.icon
        if (icon != null) Image(icon.asImageBitmap(), null, modifier.size(size).clip(shape))
        else Box(modifier.size(size).clip(shape).background(Color(0xff445267)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Android, null, Modifier.size(size * .58f), tint = Color(0xffc9d6de))
        }
    }
}

@Composable
fun LauncherTile(
    entry: HomeEntry,
    apps: Map<String, AppRecord>,
    iconSize: Dp,
    labels: Boolean,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    dragging: Boolean = false,
    onClick: () -> Unit,
    onActions: () -> Unit,
    onBounds: (Rect) -> Unit = {},
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    val label = entryLabel(entry, apps)
    Column(modifier.semantics {
        if (!labels) contentDescription = label
        if (entry is HomeEntry.App && apps[entry.id] == null) stateDescription = "No disponible en este perfil"
    }.onGloballyPositioned { bounds = it.boundsInRoot(); onBounds(bounds) }
        .pointerInput(entry.id, editing) {
            if (editing) detectDragGesturesAfterLongPress(
                onDragStart = { start(bounds.topLeft + it) },
                onDrag = { change, amount -> change.consume(); drag(amount) },
                onDragEnd = { end() }, onDragCancel = { cancel() },
            )
        }
        .combinedClickable(role = Role.Button, onClick = onClick,
            onLongClickLabel = "Acciones de $label", onLongClick = if (editing) null else onActions)
        .alpha(if (dragging) .35f else if (entry is HomeEntry.App && apps[entry.id] == null) .5f else 1f)
        .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        EntryIcon(entry, apps, iconSize)
        if (labels) {
            Spacer(Modifier.height(6.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(), color = Color.White,
                style = TextStyle(fontSize = 12.sp, shadow = Shadow(Color.Black.copy(alpha = .9f), Offset(0f, 1f), 5f)))
        }
    }
}
