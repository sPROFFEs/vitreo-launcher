package dev.vitreo.launcher.ui

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Anchoring/placement and native shortcut query adapted from Folio's MIT AppContextMenu.kt.
// Copyright (c) 2026 Duo Launcher contributors. See third_party/folio/PROVENANCE.md.
data class AppActionTarget(val entry: HomeEntry, val bounds: Rect?)

@Composable
fun AppContextMenu(target: AppActionTarget?, state: LauncherState, apps: Map<String, AppRecord>,
    onClose: () -> Unit, onLaunch: (String, Rect?) -> Unit, onShortcut: (ShortcutInfo, Rect?) -> Unit,
    onHome: (String) -> Unit, onDock: (String) -> Unit, onRemove: (String) -> Unit,
    onInfo: (String) -> Unit, onPair: (String) -> Unit, onChooseFolder: (String) -> Unit,
    onRename: (String) -> Unit, onDissolve: (String) -> Unit, onEdit: () -> Unit) {
    var retained by remember { mutableStateOf(target) }
    val current = target ?: retained
    val progress = remember { Animatable(0f) }
    val reduced = LocalGlass.current.appearance.reducedEffects
    val context = LocalContext.current
    val density = LocalDensity.current
    LaunchedEffect(target, reduced) {
        if (target != null) retained = target
        progress.animateTo(if (target == null) 0f else 1f,
            if (reduced) tween(120) else if (target == null) LauncherMotion.close() else LauncherMotion.menu())
        if (target == null) retained = null
    }
    if (current == null) return
    val entry = current.entry
    var more by remember(entry.id) { mutableStateOf(false) }
    val shortcuts by produceState(emptyList<ShortcutInfo>(), entry.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (entry !is HomeEntry.App) return@runCatching emptyList()
                val launcher = context.getSystemService(LauncherApps::class.java)
                if (!launcher.hasShortcutHostPermission()) return@runCatching emptyList()
                val component = ComponentName.unflattenFromString(entry.id) ?: return@runCatching emptyList()
                launcher.getShortcuts(LauncherApps.ShortcutQuery().setPackage(component.packageName).setActivity(component)
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC),
                    Process.myUserHandle()).orEmpty().filter { it.isEnabled }.sortedBy { it.rank }.take(3)
            }.getOrDefault(emptyList())
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("context_menu")
        .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
        .background(Color.Black.copy(alpha = .14f))
        .clickable(remember { MutableInteractionSource() }, null, onClick = onClose)) {
        val w = with(density) { maxWidth.toPx() }; val h = with(density) { maxHeight.toPx() }
        val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat() + with(density) { 12.dp.toPx() }
        val safeBottom = WindowInsets.safeDrawing.getBottom(density).toFloat() + with(density) { 12.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        val icon = current.bounds?.takeIf { it.width > 0 && it.height > 0 }
            ?: Rect(w / 2 - 30 * density.density, h / 3, w / 2 + 30 * density.density, h / 3 + 60 * density.density)
        val iconSize = icon.width.coerceAtMost(90 * density.density)
        val iconTop = icon.top.coerceIn(safeTop, (h - safeBottom - iconSize).coerceAtLeast(safeTop))
        val iconLeft = icon.left.coerceIn(gap, (w - iconSize - gap).coerceAtLeast(gap))
        val menuW = minOf(280 * density.density, w - gap * 2)
        val belowSpace = h - safeBottom - iconTop - iconSize - gap
        val aboveSpace = iconTop - safeTop - gap
        val below = belowSpace >= 230 * density.density || belowSpace >= aboveSpace
        val available = (if (below) belowSpace else aboveSpace).coerceAtLeast(48 * density.density)
        val left = (iconLeft + iconSize / 2 - menuW / 2).coerceIn(gap, (w - menuW - gap).coerceAtLeast(gap))
        var menuHeight by remember { mutableIntStateOf(0) }
        EntryIcon(entry, apps, with(density) { iconSize.toDp() },
            Modifier.offset { IntOffset(iconLeft.roundToInt(), iconTop.roundToInt()) }
                .graphicsLayer {
                    val scale = if (reduced) 1f else 1f + .1f * progress.value
                    scaleX = scale; scaleY = scale
                    shadowElevation = 12 * density.density * progress.value
                }.testTag("context_icon").clickable(remember { MutableInteractionSource() }, null) {
                    if (entry is HomeEntry.App) onLaunch(entry.id, current.bounds) else onClose()
                })
        GlassPanel(Modifier.offset { IntOffset(left.roundToInt(),
            (if (below) iconTop + iconSize + gap else iconTop - gap - menuHeight).roundToInt()) }
            .width(with(density) { menuW.toDp() }).heightIn(max = with(density) { available.toDp() })
            .onSizeChanged { menuHeight = it.height }.graphicsLayer {
                transformOrigin = TransformOrigin(((iconLeft + iconSize / 2 - left) / menuW).coerceIn(0f, 1f), if (below) 0f else 1f)
                val scale = if (reduced) 1f else .65f + .35f * progress.value
                scaleX = scale; scaleY = scale
            }.clickable(remember { MutableInteractionSource() }, null) {}, radius = 24f, material = GlassMaterial.Regular) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(entryLabel(entry, apps), Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                AnimatedContent(more, label = "menu actions") { expanded ->
                    Column {
                        if (expanded) {
                            ContextRow("Back", Icons.Outlined.ChevronLeft) { more = false }
                            IosDivider()
                            when (entry) {
                                is HomeEntry.App -> {
                                    if (entry.id !in state.dock) ContextRow("Add to Dock", Icons.Outlined.Dock, state.dock.size < DOCK_SLOTS) { onDock(entry.id) }
                                    if (state.home.any { it is HomeEntry.App && it.id != entry.id }) ContextRow("Create Folder…", Icons.Outlined.CreateNewFolder) { onPair(entry.id) }
                                    if (state.home.any { it is HomeEntry.Folder && entry.id !in it.apps && it.apps.size < MAX_FOLDER_APPS }) ContextRow("Move to Folder…", Icons.Outlined.FolderOpen) { onChooseFolder(entry.id) }
                                    ContextRow("App Info", Icons.Outlined.Info, entry.id in apps) { onInfo(entry.id) }
                                }
                                is HomeEntry.Folder -> {
                                    ContextRow("Rename Folder", Icons.Outlined.Edit) { onRename(entry.id) }
                                    ContextRow("Ungroup Folder", Icons.Outlined.FolderOff, destructive = true) { onDissolve(entry.id) }
                                }
                            }
                        } else {
                            // Keep the primary actions reachable on short screens; extra shortcuts scroll.
                            ContextRow("Edit Home Screen", Icons.Outlined.Edit) { onEdit() }
                            if (entry is HomeEntry.App) {
                                if (entry.id in state.placedApps()) ContextRow("Remove from Home Screen", Icons.Outlined.RemoveCircleOutline, destructive = true) { onRemove(entry.id) }
                                else ContextRow("Add to Home Screen", Icons.Outlined.AddCircleOutline) { onHome(entry.id) }
                            }
                            ContextRow("More", Icons.Outlined.MoreHoriz) { more = true }
                            if (shortcuts.isNotEmpty()) IosDivider()
                            shortcuts.forEach { shortcut -> key(shortcut.id) {
                                ContextRow(shortcut.shortLabel?.toString().orEmpty(), Icons.Outlined.Launch) { onShortcut(shortcut, current.bounds) }
                            } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextRow(title: String, icon: ImageVector, enabled: Boolean = true, destructive: Boolean = false, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) .09f else 0f))
        .clickable(interaction, null, enabled = enabled, onClick = action).heightIn(min = 48.dp)
        .padding(horizontal = 17.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val color = if (destructive) Color(0xffff453a) else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f)
        Text(title, Modifier.weight(1f).padding(end = 16.dp), fontSize = 16.sp, color = color)
        Icon(icon, null, Modifier.size(20.dp), tint = color)
    }
}
