@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.vitreo.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.BuildConfig
import dev.vitreo.launcher.LauncherViewModel
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord
import dev.vitreo.launcher.data.WidgetController
import dev.vitreo.launcher.graphics.Viewport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

val LauncherReadyKey = SemanticsPropertyKey<Boolean>("LauncherReady")
private sealed interface Sheet {
    data object Search : Sheet
    data object Settings : Sheet
    data object Licenses : Sheet
    data object Widgets : Sheet
    data object Controls : Sheet
    data class Folder(val id: String) : Sheet
    data class Actions(val entry: HomeEntry) : Sheet
    data class Category(val title: String, val apps: List<AppRecord>) : Sheet
    data class PairWith(val app: String) : Sheet
    data class AddToFolder(val app: String) : Sheet
}
private data class DragSession(val entry: HomeEntry, val position: Offset)

@Composable
fun LauncherScreen(model: LauncherViewModel, homeSerial: Int, defaultHome: Boolean,
    onSetHome: () -> Unit, onLaunch: (String) -> Unit, onAppInfo: (String) -> Unit,
    onConfigureWidget: (AppWidgetHost, Int, (Boolean) -> Unit) -> Unit) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val frame by model.wallpaper.collectAsStateWithLifecycle()
    val apps = remember(ui.apps) { ui.apps.associateBy { it.id } }
    val suggestions = remember(ui.apps, ui.recentApps, ui.layout.dock) {
        (ui.recentApps + ui.layout.dock + ui.apps.map { it.id }).distinct().mapNotNull(apps::get)
    }
    val appearance = ui.layout.appearance.copy(reducedEffects = ui.layout.appearance.reducedEffects || ui.powerSaver)
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var editing by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(Viewport()) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var workspaceSize by remember { mutableStateOf(IntSize(1080, 1800)) }
    var workspaceBounds by remember { mutableStateOf(Rect.Zero) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var folderTarget by remember { mutableStateOf<String?>(null) }
    val cellBounds = remember { mutableStateMapOf<String, Rect>() }
    val context = LocalContext.current
    val localView = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val widgets = remember { WidgetController(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(widgets, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { widgets.refresh(); widgets.host.startListening() }
            if (event == Lifecycle.Event.ON_STOP) widgets.host.stopListening()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) widgets.host.startListening()
        onDispose { lifecycle.removeObserver(observer); widgets.host.stopListening() }
    }
    val geometry = remember(workspaceSize, appearance.iconSize, density.density, density.fontScale) {
        workspaceGeometry(workspaceSize.width / density.density, workspaceSize.height / density.density,
            LayoutPreset(iconSize = appearance.iconSize), density.fontScale)
    }
    val withWidgets = appearance.showWidgets && geometry.rows >= 4
    val firstCapacity = if (withWidgets) (geometry.rows - 2) * GRID_COLUMNS else geometry.pageSize
    val homePages = if (ui.layout.home.size <= firstCapacity) 1
        else 1 + pageCount(ui.layout.home.size - firstCapacity, geometry.pageSize)
    val pager = rememberPagerState(initialPage = 1, pageCount = { homePages + 2 })
    var widgetCount by remember { mutableIntStateOf(widgets.ids.size) }
    LaunchedEffect(widgets.ids.size) {
        if (widgets.ids.size > widgetCount) pager.animateScrollToPage(0)
        widgetCount = widgets.ids.size
    }
    val onHomePage = pager.currentPage in 1..homePages
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importPhoto) }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::exportLayout) }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importLayout) }
    val reportExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::exportDiagnostics) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle, appearance.reducedEffects) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val interval = if (appearance.reducedEffects) 60_000L else 1_000L
            while (true) { now = System.currentTimeMillis(); delay(interval - now % interval) }
        }
    }
    fun openSearch() { editing = false; sheet = Sheet.Search }
    fun system(action: String) {
        try { context.startActivity(Intent(action)); sheet = null }
        catch (_: Exception) { model.message("No se encontró una aplicación para esta acción.") }
    }
    fun launch(id: String) { sheet = null; onLaunch(id) }
    fun openCalendar() {
        val app = ui.apps.firstOrNull { "calendar" in it.packageName || "calendario" in searchKey(it.label) }
        if (app != null) launch(app.id) else model.message("No se encontró una aplicación de calendario.")
    }
    val opaqueScreen = sheet == Sheet.Settings || sheet == Sheet.Widgets || sheet == Sheet.Licenses ||
        sheet is Sheet.PairWith || sheet is Sheet.AddToFolder
    val lightChrome = opaqueScreen && MaterialTheme.colorScheme.background.luminance() > .5f
    SideEffect {
        (context as? Activity)?.let { activity -> WindowCompat.getInsetsController(activity.window, localView).apply {
            isAppearanceLightStatusBars = lightChrome; isAppearanceLightNavigationBars = lightChrome
        } }
    }
    LaunchedEffect(Unit) { model.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(homeSerial) { sheet = null; editing = false; drag = null; focusManager.clearFocus(); keyboard?.hide(); pager.scrollToPage(1) }
    LaunchedEffect(homePages) { if (pager.currentPage > homePages + 1) pager.scrollToPage(homePages) }
    BackHandler {
        when {
            sheet != null -> sheet = null
            editing -> { editing = false; drag = null }
            pager.currentPage != 1 -> scope.launch { pager.animateScrollToPage(1) }
        }
    }
    val openEntry: (HomeEntry) -> Unit = { entry ->
        if (editing) sheet = Sheet.Actions(entry)
        else when (entry) {
            is HomeEntry.App -> launch(entry.id)
            is HomeEntry.Folder -> sheet = Sheet.Folder(entry.id)
        }
    }
    val hovered = drag?.let { session ->
        if (session.entry !is HomeEntry.App) null else ui.layout.home.firstOrNull { entry ->
            entry.id != session.entry.id && cellBounds[entry.id]?.let { rect ->
                (rect.center - session.position).getDistance() < minOf(rect.width, rect.height) * .32f
            } == true
        }?.id
    }
    LaunchedEffect(hovered, drag?.entry?.id) {
        folderTarget = null
        if (hovered != null) { delay(650); folderTarget = hovered; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    val edge = drag?.let {
        when { it.position.x < workspaceBounds.left + 26 * density.density -> -1
            it.position.x > workspaceBounds.right - 26 * density.density -> 1
            else -> 0 }
    } ?: 0
    LaunchedEffect(edge, pager.currentPage, drag?.entry?.id) {
        if (drag != null && edge != 0) {
            delay(650)
            val target = (pager.currentPage + edge).coerceIn(1, homePages)
            if (target != pager.currentPage) { folderTarget = null; pager.animateScrollToPage(target) }
        }
    }
    val drop by rememberUpdatedState<() -> Unit>({
        drag?.let { session ->
            val entry = session.entry
            val target = ui.layout.home.firstOrNull { it.id == folderTarget }
            when {
                dockBounds.contains(session.position) && entry is HomeEntry.App -> {
                    val slot = ((session.position.x - dockBounds.left) / (dockBounds.width / DOCK_SLOTS)).toInt().coerceIn(0, DOCK_SLOTS - 1)
                    model.dockDrop(entry.id, slot)
                }
                entry is HomeEntry.App && target is HomeEntry.Folder -> model.addToFolder(entry.id, target.id)
                entry is HomeEntry.App && target is HomeEntry.App -> model.folder(entry.id, target.id)
                workspaceBounds.contains(session.position) -> {
                    val id = ui.layout.home.firstOrNull { it.id != entry.id && cellBounds[it.id]?.contains(session.position) == true }?.id
                    if (id != null) model.moveToSlot(entry.id, id)
                    else if (entry is HomeEntry.App) model.addHome(entry.id)
                    else model.move(entry.id, Int.MAX_VALUE)
                }
            }
        }
        drag = null; folderTarget = null
    })
    val startDrag by rememberUpdatedState<(Offset) -> Unit>({ point ->
        val global = point + rootPosition
        val id = cellBounds.entries.firstOrNull { it.value.contains(global) }?.key
        val entry = ui.layout.home.firstOrNull { it.id == id } ?: id?.takeIf { it in ui.layout.dock }?.let(HomeEntry::App)
        if (entry != null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); drag = DragSession(entry, global) }
    })
    val currentPage by rememberUpdatedState(pager.currentPage)
    val currentHomePages by rememberUpdatedState(homePages)
    val currentSheet by rememberUpdatedState(sheet)
    val gestureModifier = if (editing) Modifier.pointerInput(editing) {
        detectDragGesturesAfterLongPress(onDragStart = { startDrag(it) },
            onDrag = { change, amount -> if (drag != null) { change.consume(); drag = drag?.let { it.copy(position = it.position + amount) } } },
            onDragEnd = { drop() }, onDragCancel = { drag = null; folderTarget = null })
    } else Modifier.pointerInput(editing) {
        // Observe before the home scroll container. It otherwise owns vertical
        // drags even when all rows fit, preventing the Spotlight gesture.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (currentSheet != null || currentPage !in 1..currentHomePages) return@awaitEachGesture
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val distance = change.position - down.position
                if (abs(distance.x) > viewConfiguration.touchSlop && abs(distance.x) > abs(distance.y)) break
                if (distance.y > 50.dp.toPx() && distance.y > abs(distance.x)) {
                    change.consume()
                    sheet = if (down.position.y < 100.dp.toPx() && down.position.x > size.width * .65f) Sheet.Controls else Sheet.Search
                    break
                }
            }
        }
    }
    CompositionLocalProvider(LocalGlass provides GlassEnvironment(frame, viewport, appearance), LocalHomeClock provides now) {
        Box(Modifier.fillMaxSize().testTag("launcher_root").semantics { this[LauncherReadyKey] = ui.ready && frame != null }
            .background(Color(0xff142f68)).then(gestureModifier).onGloballyPositioned { coordinates ->
                val screen = IntArray(2); val window = IntArray(2)
                localView.getLocationOnScreen(screen); localView.getLocationInWindow(window)
                val origin = coordinates.positionInWindow()
                viewport = Viewport(coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
                    origin.x + screen[0] - window[0], origin.y + screen[1] - window[1])
                rootPosition = coordinates.positionInRoot()
            }) {
            frame?.let { Image(it.sharp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Transparent, Color.Black.copy(alpha = .13f)))))
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = if (editing) 42.dp else 9.dp)
                .blur(if (sheet != null && !opaqueScreen && !appearance.reducedEffects) 18.dp else 0.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().testTag("home_workspace")
                    .pointerInput(onHomePage, sheet) {
                        detectTapGestures(onLongPress = { if (sheet == null && onHomePage) {
                            editing = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } })
                    }
                    // Today/Library use the space left by the hidden dock;
                    // their height must not change the home page count.
                    .onSizeChanged { if (onHomePage) workspaceSize = it }.onGloballyPositioned { workspaceBounds = it.boundsInRoot() }) {
                    if (!ui.ready) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    else HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = drag == null,
                        beyondViewportPageCount = 0) { page ->
                        when (page) {
                            0 -> TodayPage(now, widgets, suggestions, apps, onSearch = ::openSearch,
                                onSettings = { sheet = Sheet.Settings }, onWidgets = { sheet = Sheet.Widgets },
                                onClock = { system(AlarmClock.ACTION_SHOW_ALARMS) },
                                onCalendar = ::openCalendar, onLaunch = ::launch)
                            homePages + 1 -> AppLibrary(ui.apps, apps, suggestions, ::openSearch, ::launch,
                                { sheet = Sheet.Actions(HomeEntry.App(it)) }, { title, records -> sheet = Sheet.Category(title, records) })
                            else -> {
                                val index = page - 1
                                val capacity = if (index == 0) firstCapacity else geometry.pageSize
                                val begin = if (index == 0) 0 else firstCapacity + (index - 1) * geometry.pageSize
                                val entries = ui.layout.home.drop(begin).take(capacity)
                                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
                                    if (index == 0 && withWidgets) HomeWidgets(now, Modifier.height((geometry.cells.rowHeight * 2).dp),
                                        onClock = { system(AlarmClock.ACTION_SHOW_ALARMS) }, onCalendar = ::openCalendar)
                                    repeat(capacity / GRID_COLUMNS) { row ->
                                        Row(Modifier.fillMaxWidth().height(geometry.cells.rowHeight.dp)) {
                                            repeat(GRID_COLUMNS) { column ->
                                                val entry = entries.getOrNull(row * GRID_COLUMNS + column)
                                                if (entry == null) Spacer(Modifier.weight(1f).fillMaxHeight())
                                                else key(entry.id) {
                                                    LauncherTile(entry, apps, geometry.iconSize.dp, appearance.showLabels,
                                                        Modifier.weight(1f).fillMaxHeight().testTag("home_entry:${entry.id}"), editing, drag?.entry?.id == entry.id,
                                                        onClick = { openEntry(entry) }, onActions = { sheet = Sheet.Actions(entry) },
                                                        onBounds = { cellBounds[entry.id] = it }, onRemove = {
                                                            if (entry is HomeEntry.App) model.removeApp(entry.id) else model.dissolve(entry.id)
                                                        })
                                                    DisposableEffect(entry.id) { onDispose { cellBounds.remove(entry.id) } }
                                                }
                                            }
                                        }
                                    }
                                    if (entries.isEmpty() && !withWidgets) Text("Desliza hacia la izquierda para ver tus apps", color = Color.White.copy(alpha = .7f),
                                        fontSize = 14.sp, modifier = Modifier.padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                if (onHomePage) {
                    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                        if (editing || pager.isScrollInProgress) GlassPanel(radius = 20f, tint = .12f) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                repeat(homePages.coerceAtMost(12)) { index ->
                                    Box(Modifier.size(6.dp).background(Color.White.copy(alpha = if (index + 1 == pager.currentPage) 1f else .35f), CircleShape))
                                }
                            }
                        } else GlassPanel(Modifier.testTag("library_button").clickable { openSearch() }, radius = 18f, tint = .1f) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Search, null, Modifier.size(14.dp), tint = Color.White)
                                Text("Buscar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 5.dp))
                            }
                        }
                    }
                    GlassPanel(Modifier.padding(horizontal = 11.dp).fillMaxWidth().testTag("dock")
                        .onGloballyPositioned { dockBounds = it.boundsInRoot() }, radius = 35f, tint = .16f) {
                        Row(Modifier.fillMaxWidth().height(91.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(DOCK_SLOTS) { slot ->
                                val id = ui.layout.dock.getOrNull(slot)
                                if (id == null) Box(Modifier.weight(1f).fillMaxHeight().clickable { openSearch() }, contentAlignment = Alignment.Center) {
                                    if (editing) Icon(Icons.Outlined.Add, "Añadir al dock", tint = Color.White.copy(alpha = .6f))
                                } else key(id) {
                                    LauncherTile(HomeEntry.App(id), apps, minOf(geometry.iconSize, 62f).dp, false,
                                        Modifier.weight(1f).fillMaxHeight(), editing, drag?.entry?.id == id,
                                        onClick = { openEntry(HomeEntry.App(id)) }, onActions = { sheet = Sheet.Actions(HomeEntry.App(id)) },
                                        onBounds = { cellBounds[id] = it }, onRemove = { model.addHome(id) })
                                    DisposableEffect(id) { onDispose { cellBounds.remove(id) } }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
            if (editing && sheet == null) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassPanel(radius = 20f) { Text("Añadir widgets", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.clickable { sheet = Sheet.Widgets }.padding(horizontal = 14.dp, vertical = 10.dp)) }
                Row {
                    IconButton(onClick = { sheet = Sheet.Settings }, modifier = Modifier.testTag("settings_button")) { Icon(Icons.Outlined.Settings, "Ajustes", tint = Color.White) }
                    GlassPanel(radius = 20f) { Text("OK", color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("edit_done").clickable { editing = false }.padding(horizontal = 17.dp, vertical = 10.dp)) }
                }
            }
            drag?.let { session ->
                val px = with(density) { geometry.iconSize.dp.toPx() }
                EntryIcon(session.entry, apps, geometry.iconSize.dp, Modifier.offset {
                    IntOffset((session.position.x - rootPosition.x - px / 2).roundToInt(), (session.position.y - rootPosition.y - px / 2).roundToInt()) })
            }
            AnimatedContent(sheet, transitionSpec = {
                (fadeIn(tween(if (appearance.reducedEffects) 0 else 180)) + scaleIn(initialScale = .96f)).togetherWith(fadeOut(tween(120)))
            }, modifier = Modifier.fillMaxSize(), label = "surface") { current ->
                if (current != null) {
                    if (!opaqueScreen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)))
                    when (current) {
                        Sheet.Search -> Spotlight(ui.apps, apps, suggestions, { focusManager.clearFocus(); keyboard?.hide(); sheet = null }, ::launch, { sheet = Sheet.Actions(HomeEntry.App(it)) })
                        Sheet.Settings -> SettingsSheet(ui, defaultHome, { sheet = null }, model::appearance, onSetHome,
                            { photoPicker.launch(arrayOf("image/*")) }, { backupExport.launch("vitreo-layout-${BuildConfig.VERSION_NAME}.json") },
                            { backupImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            { reportExport.launch("vitreo-diagnostics-${BuildConfig.VERSION_NAME}.json") }, model::refresh, model::resetLayout,
                            { sheet = Sheet.Licenses }, { sheet = Sheet.Widgets })
                        Sheet.Widgets -> WidgetGallery(widgets, appearance.showWidgets, { model.appearance(ui.layout.appearance.copy(showWidgets = it)) },
                            onConfigureWidget, { sheet = null }, model::message)
                        Sheet.Licenses -> LicensesSheet { sheet = Sheet.Settings }
                        Sheet.Controls -> ControlCenter(MaterialTheme.colorScheme.background.luminance() < .5f, { sheet = null }, ::system,
                            { model.appearance(ui.layout.appearance.copy(interfaceStyle = if (it) 2 else 1)) }, { sheet = Sheet.Settings }, { sheet = Sheet.Widgets })
                        is Sheet.Folder -> {
                            val folder = ui.layout.home.filterIsInstance<HomeEntry.Folder>().find { it.id == current.id }
                            if (folder != null) FolderSheet(folder, apps, { sheet = null }, ::launch, { sheet = Sheet.Actions(HomeEntry.App(it)) }, { model.renameFolder(folder.id, it) })
                        }
                        is Sheet.Category -> CategoryScreen(current.title, current.apps, apps, { sheet = null }, ::launch, { sheet = Sheet.Actions(HomeEntry.App(it)) })
                        is Sheet.Actions -> ActionsSheet(current.entry, ui.layout, apps, { sheet = null },
                            { model.addHome(it); sheet = null }, { model.pinDock(it); sheet = null }, { model.removeApp(it); sheet = null }, model::move,
                            { sheet = null; onAppInfo(it) }, { sheet = Sheet.PairWith(it) }, { sheet = Sheet.AddToFolder(it) },
                            { id, title -> model.renameFolder(id, title); sheet = null }, { model.dissolve(it); sheet = null }, { sheet = null; editing = true })
                        is Sheet.PairWith -> ChoiceSheet("Crear carpeta", ui.layout.home.filterIsInstance<HomeEntry.App>().filter { it.id != current.app }
                            .map { it.id to entryLabel(it, apps) }, { sheet = null }) { model.folder(current.app, it); sheet = null }
                        is Sheet.AddToFolder -> ChoiceSheet("Mover a una carpeta", ui.layout.home.filterIsInstance<HomeEntry.Folder>().filter { current.app !in it.apps }
                            .map { it.id to it.title }, { sheet = null }) { model.addToFolder(current.app, it); sheet = null }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp))
        }
    }
}
