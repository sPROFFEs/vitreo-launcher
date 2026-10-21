package dev.vitreo.launcher.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord
import dev.vitreo.launcher.data.WidgetController

@Composable
fun TodayPage(now: Long, widgets: WidgetController, suggestions: List<AppRecord>, apps: Map<String, AppRecord>,
    appearance: Appearance, onAppearance: (Appearance) -> Unit, editing: Boolean, onEditing: (Boolean) -> Unit,
    onSearch: () -> Unit, onSettings: () -> Unit,
    onWidgets: () -> Unit, onControls: () -> Unit, onClock: () -> Unit, onCalendar: () -> Unit,
    onLaunch: (String, Rect?) -> Unit, onEditWidget: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val ink = LocalGlassContentColor.current
    val density = LocalDensity.current.density
    val latestAppearance by rememberUpdatedState(appearance)
    val latestChange by rememberUpdatedState(onAppearance)
    val sections = appearance.todaySections.filter { it !in appearance.hiddenTodaySections && (it != "widgets" || widgets.todayIds.isNotEmpty()) }
    val cards = sections.flatMap { id ->
        if (id == "widgets") (if (editing) listOf(id) else emptyList()) + widgets.todayIds.map { "native:$it" }
        else listOf(id)
    }
    fun title(id: String) = when (id) { "clock_calendar" -> "Clock & Calendar"; "suggestions" -> "Suggestions"; else -> "App Widgets" }
    fun move(id: String, delta: Int) {
        val visible = latestAppearance.todaySections.filter {
            it !in latestAppearance.hiddenTodaySections && (it != "widgets" || widgets.todayIds.isNotEmpty())
        }
        val source = visible.indexOf(id)
        if (source < 0) return
        val target = visible.getOrNull(source + delta) ?: return
        latestChange(latestAppearance.copy(todaySections = moveTodaySection(latestAppearance.todaySections, id,
            latestAppearance.todaySections.indexOf(target))))
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 21.dp).testTag("today_page"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ink)
                TextButton(onClick = { onEditing(!editing) }, modifier = Modifier.testTag("edit_today")) {
                    Text(if (editing) "Done" else "Edit", color = ink)
                }
                if (appearance.controlCenter) IconButton(onClick = onControls, modifier = Modifier.testTag("today_controls")) {
                    LauncherSymbol(Icons.Outlined.Tune, "Control Center", tint = ink)
                }
                IconButton(onClick = onSettings, modifier = Modifier.testTag("settings_button")) {
                    LauncherSymbol(Icons.Outlined.Settings, "Settings", tint = ink)
                }
            }
            GlassPanel(Modifier.fillMaxWidth().height(IosDesign.SearchHeight.dp).clickable(onClick = onSearch), radius = 25f, tint = .13f, interactive = true) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LauncherSymbol(Icons.Outlined.Search, null, Modifier.size(21.dp), tint = ink.copy(alpha = .8f))
                    Text("Search", color = ink.copy(alpha = .8f), fontSize = 17.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        items(cards, key = { it }) { id ->
            Column(Modifier.fillMaxWidth().testTag("today_section:$id")
                .animateItem(fadeInSpec = if (appearance.reducedEffects) tween(0) else tween(150),
                    fadeOutSpec = if (appearance.reducedEffects) tween(0) else tween(120),
                    placementSpec = if (appearance.reducedEffects) tween(0) else LauncherMotion.panel())
                .zIndex(if (dragging == id) 1f else 0f)
                .graphicsLayer { translationY = if (dragging == id) dragOffset else 0f }) {
                if (editing && !id.startsWith("native:")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (id != "widgets") IconButton(onClick = {
                            onAppearance(appearance.copy(hiddenTodaySections = appearance.hiddenTodaySections + id))
                        }, modifier = Modifier.testTag("remove_today:$id")) {
                            LauncherSymbol(Icons.Outlined.RemoveCircle, "Remove ${title(id)}", tint = Color(0xffff453a))
                        }
                        Text(title(id), Modifier.weight(1f), color = ink, fontSize = 13.sp, maxLines = 1)
                        IconButton(onClick = { move(id, -1) }, enabled = sections.indexOf(id) > 0,
                            modifier = Modifier.testTag("today_up:$id")) { LauncherSymbol(Icons.Outlined.KeyboardArrowUp, "Move ${title(id)} up", tint = ink) }
                        IconButton(onClick = { move(id, 1) }, enabled = sections.indexOf(id) < sections.lastIndex,
                            modifier = Modifier.testTag("today_down:$id")) { LauncherSymbol(Icons.Outlined.KeyboardArrowDown, "Move ${title(id)} down", tint = ink) }
                        Box(Modifier.size(44.dp).testTag("today_drag:$id").semantics { contentDescription = "Drag ${title(id)} to move" }
                            .pointerInput(id) {
                                detectVerticalDragGestures(onDragStart = { dragging = id; dragOffset = 0f },
                                    onVerticalDrag = { change, amount -> change.consume(); dragOffset += amount },
                                    onDragEnd = {
                                        val amount = dragOffset / density
                                        dragging = null; dragOffset = 0f
                                        if (amount > 45f) move(id, 1) else if (amount < -45f) move(id, -1)
                                    }, onDragCancel = { dragging = null; dragOffset = 0f })
                            }, contentAlignment = Alignment.Center) {
                            LauncherSymbol(Icons.Outlined.DragHandle, null, tint = ink.copy(alpha = .7f))
                        }
                    }
                }
                when (id) {
                    "clock_calendar" -> {
                        HomeWidgets(now, Modifier.height(207.dp), { if (!editing) onClock() }, { if (!editing) onCalendar() })
                        if (editing) TextButton(onClick = {
                            onAppearance(appearance.copy(showWidgets = true, hiddenTodaySections = appearance.hiddenTodaySections + id))
                        }, modifier = Modifier.testTag("today_move_home")) { Text("Move to Home Screen", color = ink) }
                    }
                    "suggestions" -> GlassPanel(Modifier.fillMaxWidth(), radius = 28f, tint = .12f) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Suggestions", color = ink.copy(alpha = .8f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                            Row(Modifier.fillMaxWidth()) {
                                suggestions.take(4).forEach { app ->
                                    LauncherTile(HomeEntry.App(app.id), apps, 53.dp, true, Modifier.weight(1f),
                                        onClick = { if (!editing) onLaunch(app.id, it) }, onActions = {})
                                }
                            }
                        }
                    }
                    else -> if (id.startsWith("native:")) {
                        val widgetId = id.removePrefix("native:").toInt()
                        Box {
                            NativeWidget(widgets, widgetId) { onEditWidget(widgetId) }
                            if (editing) Box(Modifier.matchParentSize().clickable { onEditWidget(widgetId) })
                        }
                    }
                }
            }
        }
        item(key = "editor") {
            if (editing) {
                Text("Drag a handle or use the arrows to reorder. Removed cards can be added again below.",
                    color = ink.copy(alpha = .8f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                CompositionLocalProvider(LocalContentColor provides ink) {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 24f, tint = .2f) {
                        Column {
                            listOf("clock_calendar", "suggestions").forEach { id ->
                                IosToggleRow(title(id), id !in appearance.hiddenTodaySections,
                                    modifier = Modifier.testTag("restore_today:$id")) { visible ->
                                    onAppearance(appearance.copy(hiddenTodaySections = if (visible) appearance.hiddenTodaySections - id else appearance.hiddenTodaySections + id))
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { onAppearance(appearance.copy(todaySections = DEFAULT_TODAY_SECTIONS, hiddenTodaySections = emptySet())) },
                    modifier = Modifier.testTag("reset_today")) { Text("Restore Default Cards", color = ink) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onWidgets, modifier = Modifier.testTag("today_add_widgets").semantics { contentDescription = "Add Widgets" }) {
                    LauncherSymbol(Icons.Outlined.Add, null, tint = ink)
                    Text("Add Widgets", color = ink, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
