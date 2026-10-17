package dev.vitreo.launcher.ui

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord
import dev.vitreo.launcher.data.WidgetController

/** Stable keys let cells glide to their new slots when a widget changes size. */
@Composable
internal fun HomeWorkspace(page: HomeGridPage, rows: Int, rowHeight: Float, iconSize: Float, showLabels: Boolean,
    editing: Boolean, draggedApp: String?, draggedWidget: Int?, apps: Map<String, AppRecord>, widgets: WidgetController,
    now: Long, onClock: () -> Unit, onCalendar: () -> Unit, onOpen: (HomeEntry, Rect?) -> Unit,
    onActions: (HomeEntry, Rect?) -> Unit, onRemove: (HomeEntry) -> Unit, onEditWidget: (Int) -> Unit,
    onBounds: (String, Rect?) -> Unit) {
    val density = LocalDensity.current
    val reduced = LocalGlass.current.appearance.reducedEffects
    val arrival = LocalHomeArrival.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())
        .onGloballyPositioned { origin = it.positionInRoot() }) {
        val cellWidth = maxWidth / GRID_COLUMNS
        @Composable fun position(column: Int, row: Int): Modifier {
            val target = with(density) { IntOffset((cellWidth * column).roundToPx(), (rowHeight.dp * row).roundToPx()) }
            val offset = animateIntOffsetAsState(target, if (reduced) tween(0) else LauncherMotion.panel(), label = "home cell")
            return Modifier.offset { offset.value }
        }
        Box(Modifier.fillMaxWidth().height((rows * rowHeight).dp)) {
            if (page.builtins) HomeWidgets(now, Modifier.height((rowHeight * 2).dp).testTag("home_builtin_widgets").graphicsLayer {
                if (arrival.enabled) { val p = arrival.progress.value; alpha = (p * 3).coerceIn(0f, 1f); translationY = (1f - p) * 24.dp.toPx() }
            }, onClock, onCalendar)
            page.apps.forEach { cell -> key(cell.entry.id) {
                LauncherTile(cell.entry, apps, iconSize.dp, showLabels,
                    position(cell.column, cell.row).width(cellWidth).height(rowHeight.dp)
                        .then(homeArrival(origin + with(density) { Offset((cellWidth * (cell.column + .5f)).toPx(), (rowHeight.dp * cell.row + iconSize.dp / 2).toPx()) },
                            with(density) { iconSize.dp.toPx() })).testTag("home_entry:${cell.entry.id}"),
                    editing, draggedApp == cell.entry.id, onClick = { onOpen(cell.entry, it) },
                    onActions = { onActions(cell.entry, it) }, onBounds = { onBounds(cell.entry.id, it) },
                    onRemove = { onRemove(cell.entry) })
                DisposableEffect(cell.entry.id) { onDispose { onBounds(cell.entry.id, null) } }
            } }
            page.widgets.forEach { placement -> key("widget:${placement.id}") {
                Column(position(placement.column, placement.row).width(cellWidth * placement.columns)
                    .height((rowHeight * placement.rows).dp).padding(horizontal = 5.dp, vertical = 5.dp)
                    .testTag("home_widget:${placement.id}").onGloballyPositioned { onBounds("widget:${placement.id}", it.boundsInRoot()) }
                    .graphicsLayer {
                        val p = if (arrival.enabled) arrival.progress.value else 1f
                        alpha = if (draggedWidget == placement.id) .25f else (p * 3).coerceIn(0f, 1f)
                        translationY = (1f - p) * 24.dp.toPx()
                    }) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NativeWidgetContent(widgets, placement.id, Modifier.fillMaxSize(), fitToBounds = true)
                        // In edit mode the native provider must not consume the drag
                        // or launch an app while its card is being moved.
                        if (editing) Box(Modifier.fillMaxSize().clickable { onEditWidget(placement.id) }
                            .background(Color.Transparent))
                    }
                    WidgetCaption(widgets, placement.id) { onEditWidget(placement.id) }
                }
                DisposableEffect(placement.id) { onDispose { onBounds("widget:${placement.id}", null) } }
            } }
        }
    }
}
