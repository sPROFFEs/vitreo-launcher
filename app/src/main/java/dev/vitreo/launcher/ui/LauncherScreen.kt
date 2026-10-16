@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.vitreo.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
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
val AppReturnSerialKey = SemanticsPropertyKey<Int>("AppReturnSerial")
val AppOpeningKey = SemanticsPropertyKey<Boolean>("AppOpening")
val HomeRowsKey = SemanticsPropertyKey<Int>("HomeRows")
private sealed interface Sheet {
    data object Search : Sheet
    data object LibrarySearch : Sheet
    data object Settings : Sheet
    data object Licenses : Sheet
    data object Widgets : Sheet
    data object Controls : Sheet
    data object Wallpaper : Sheet
    data class Folder(val id: String) : Sheet
    data class Category(val title: String, val apps: List<AppRecord>) : Sheet
    data class PairWith(val app: String) : Sheet
    data class AddToFolder(val app: String) : Sheet
}
private data class DragSession(val entry: HomeEntry, val position: Offset)

@Composable
fun LauncherScreen(model: LauncherViewModel, homeSerial: Int, appReturnSerial: Int, defaultHome: Boolean,
    onSetHome: () -> Unit, onLaunch: (String, Rect?) -> Boolean, onShortcut: (ShortcutInfo, Rect?) -> Boolean, onAppInfo: (String) -> Unit,
    onConfigureWidget: (AppWidgetHost, Int, (Boolean) -> Unit) -> Unit) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val frame by model.wallpaper.collectAsStateWithLifecycle()
    val photoDraft by model.photoDraft.collectAsStateWithLifecycle()
    val photoBusy by model.photoBusy.collectAsStateWithLifecycle()
    val apps = remember(ui.apps) { ui.apps.associateBy { it.id } }
    val suggestions = remember(ui.apps, ui.recentApps, ui.layout.dock) {
        (ui.recentApps + ui.layout.dock + ui.apps.map { it.id }).distinct().mapNotNull(apps::get)
    }
    val appearance = ui.layout.appearance.copy(reducedEffects = ui.layout.appearance.reducedEffects || ui.powerSaver)
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val pageHistory = remember { mutableStateListOf<Sheet>() }
    val savedPages = rememberSaveableStateHolder()
    var actionTarget by remember { mutableStateOf<AppActionTarget?>(null) }
    var choiceSheet by remember { mutableStateOf<Sheet?>(null) }
    var actionReturn by remember { mutableStateOf<AppActionTarget?>(null) }
    var launching by remember { mutableStateOf(false) }
    val departure = remember { Animatable(0f) }
    var surfaceProgress by remember { mutableFloatStateOf(0f) }
    val modalPresent = sheet != null || actionTarget != null || choiceSheet != null
    var editing by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(Viewport()) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var workspaceSize by remember { mutableStateOf(IntSize(1080, 1800)) }
    var workspaceWasEditing by remember { mutableStateOf(false) }
    var workspaceBounds by remember { mutableStateOf(Rect.Zero) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var folderTarget by remember { mutableStateOf<String?>(null) }
    var folderAnchor by remember { mutableStateOf(TransformOrigin.Center) }
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
    val geometry = remember(workspaceSize, workspaceWasEditing, appearance.iconSize, density.density, density.fontScale) {
        // The edit toolbar uses 33dp more space. Keep the same rows/pages when
        // entering edit mode, and only tighten their spacing in that space.
        workspaceGeometry(workspaceSize.width / density.density - 36f,
            workspaceSize.height / density.density + if (workspaceWasEditing) 33f else 0f,
            LayoutPreset(iconSize = appearance.iconSize), density.fontScale)
    }
    val rowHeight = if (workspaceWasEditing) (geometry.cells.rowHeight - 33f / geometry.rows)
        .coerceAtLeast(geometry.iconSize + 20f * density.fontScale + 8f) else geometry.cells.rowHeight
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
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.preparePhoto(it) } }
    LaunchedEffect(photoDraft) {
        if (photoDraft != null) sheet = Sheet.Wallpaper
        else if (sheet == Sheet.Wallpaper) sheet = Sheet.Settings
    }
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
        try { context.startActivity(Intent(action)) }
        catch (_: Exception) { model.message("No app is available for this action.") }
    }
    fun launch(id: String, source: Rect? = null) {
        if (launching) return
        focusManager.clearFocus(); keyboard?.hide()
        if (onLaunch(id, source)) {
            launching = true; actionTarget = null; editing = false
            scope.launch { departure.animateTo(1f, if (appearance.reducedEffects) tween(0) else tween(240)) }
        }
    }
    fun actions(id: String, source: Rect?) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        actionTarget = AppActionTarget(HomeEntry.App(id), source)
    }
    fun origin(bounds: Rect?) = bounds?.center?.let {
        TransformOrigin(((it.x - rootPosition.x) / viewport.width).coerceIn(0f, 1f),
            ((it.y - rootPosition.y) / viewport.height).coerceIn(0f, 1f))
    } ?: TransformOrigin.Center
    fun pushPage(next: Sheet) {
        sheet?.let { if (it != next) pageHistory.add(it) }
        sheet = next
    }
    fun closeSurface() {
        when {
            choiceSheet != null -> { choiceSheet = null; actionTarget = actionReturn; actionReturn = null }
            actionTarget != null -> actionTarget = null
            sheet == Sheet.Wallpaper -> if (!photoBusy) { model.discardPhoto(); sheet = Sheet.Settings }
            else -> {
                if (sheet == Sheet.Settings) savedPages.removeState("settings")
                sheet = if (pageHistory.isNotEmpty()) pageHistory.removeAt(pageHistory.lastIndex) else null
            }
        }
    }
    LaunchedEffect(appReturnSerial) {
        launching = false
        departure.animateTo(0f, if (appearance.reducedEffects) tween(0) else LauncherMotion.panel())
    }
    LaunchedEffect(launching) {
        if (launching) {
            delay(1_500)
            // Recover if an OEM refused the task switch without throwing an exception.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                launching = false; departure.animateTo(0f, LauncherMotion.close())
            }
        }
    }
    fun openCalendar() {
        val app = ui.apps.firstOrNull { "calendar" in it.packageName || "calendario" in searchKey(it.label) }
        if (app != null) launch(app.id, null) else model.message("No calendar app is installed.")
    }
    val opaqueScreen = sheet == Sheet.Settings || sheet == Sheet.Widgets || sheet == Sheet.Licenses || sheet == Sheet.Wallpaper ||
        sheet is Sheet.PairWith || sheet is Sheet.AddToFolder
    val lightWallpaper = (frame?.brightness ?: 0f) * (1f - appearance.wallpaperDim) > .62f
    val lightChrome = opaqueScreen && sheet != Sheet.Wallpaper && MaterialTheme.colorScheme.background.luminance() > .5f ||
        sheet == null && lightWallpaper
    SideEffect {
        (context as? Activity)?.let { activity -> WindowCompat.getInsetsController(activity.window, localView).apply {
            isAppearanceLightStatusBars = lightChrome; isAppearanceLightNavigationBars = lightChrome
        } }
    }
    LaunchedEffect(Unit) { model.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(sheet, actionTarget) {
        if (sheet != Sheet.Search && sheet != Sheet.LibrarySearch || actionTarget != null) { focusManager.clearFocus(); keyboard?.hide() }
    }
    LaunchedEffect(homeSerial) { sheet = null; pageHistory.clear(); savedPages.removeState("settings"); actionTarget = null; choiceSheet = null; actionReturn = null; model.discardPhoto(); editing = false; drag = null; focusManager.clearFocus(); keyboard?.hide(); pager.scrollToPage(1) }
    var previousHomePages by remember { mutableIntStateOf(homePages) }
    LaunchedEffect(pager.currentPage, homePages, editing) {
        val shrank = homePages < previousHomePages
        previousHomePages = homePages
        if (editing && shrank && pager.currentPage > homePages) pager.scrollToPage(homePages)
        else if (pager.currentPage !in 1..homePages) { editing = false; drag = null; folderTarget = null }
    }
    BackHandler {
        when {
            modalPresent -> closeSurface()
            editing -> { editing = false; drag = null }
            pager.currentPage != 1 -> scope.launch { pager.animateScrollToPage(1) }
        }
    }
    val openEntry: (HomeEntry, Rect?) -> Unit = { entry, source ->
        if (editing) actionTarget = AppActionTarget(entry, source)
        else when (entry) {
            is HomeEntry.App -> launch(entry.id, source)
            is HomeEntry.Folder -> {
                folderAnchor = origin(source)
                sheet = Sheet.Folder(entry.id)
            }
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
    val gestureModifier = if (modalPresent || launching || !onHomePage) Modifier else if (editing) Modifier.pointerInput(editing) {
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
    val menuBlur = animateFloatAsState(if (actionTarget != null) 1f else 0f,
        if (appearance.reducedEffects) tween(0) else LauncherMotion.close(), label = "menu backdrop")
    val wallpaperText = if (lightWallpaper) Color(0xff151518) else Color.White
    CompositionLocalProvider(LocalGlass provides GlassEnvironment(frame, viewport, appearance), LocalHomeClock provides now,
        LocalGlassContentColor provides wallpaperText) {
        Box(Modifier.fillMaxSize().testTag("launcher_root").semantics {
            this[LauncherReadyKey] = ui.ready && frame != null
            this[HomeRowsKey] = geometry.rows
            this[AppReturnSerialKey] = appReturnSerial
            this[AppOpeningKey] = launching
        }
            .background(Color(0xff142f68)).graphicsLayer {
                if (!appearance.reducedEffects) {
                    scaleX = 1f - departure.value * .045f; scaleY = scaleX
                    alpha = 1f - departure.value * .18f
                }
            }.then(gestureModifier).onGloballyPositioned { coordinates ->
                val screen = IntArray(2); val window = IntArray(2)
                localView.getLocationOnScreen(screen); localView.getLocationInWindow(window)
                val origin = coordinates.positionInWindow()
                viewport = Viewport(coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
                    origin.x + screen[0] - window[0], origin.y + screen[1] - window[1])
                model.wallpaperViewport(coordinates.size.width, coordinates.size.height)
                rootPosition = coordinates.positionInRoot()
            }) {
            frame?.let { Image(it.sharp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = appearance.wallpaperDim)))
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(top = if (editing) 42.dp else 9.dp)
                .then(if (modalPresent) Modifier.clearAndSetSemantics {} else Modifier)
                .graphicsLayer {
                    // Read the animation in the drawing phase. Rebuilding the entire
                    // workspace for every blur frame causes avoidable layout/recomposition.
                    alpha = if (opaqueScreen && surfaceProgress >= .999f) 0f else 1f
                    val blurRadius = if (appearance.reducedEffects || opaqueScreen) 0f else maxOf(surfaceProgress, menuBlur.value) * 20.dp.toPx()
                    renderEffect = if (blurRadius > .05f) BlurEffect(blurRadius, blurRadius, TileMode.Clamp) else null
                }) {
                Box(Modifier.weight(1f).fillMaxWidth().testTag("home_workspace")
                    .pointerInput(onHomePage, modalPresent) {
                        detectTapGestures(onLongPress = { if (!modalPresent && onHomePage) {
                            editing = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } })
                    }
                    // Today/Library use the space left by the hidden dock;
                    // their height must not change the home page count.
                    .onSizeChanged { if (onHomePage) { workspaceSize = it; workspaceWasEditing = editing } }
                    .onGloballyPositioned { workspaceBounds = it.boundsInRoot() }) {
                    if (!ui.ready) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    else HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = drag == null && !modalPresent && !launching,
                        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = LauncherMotion.panel()),
                        key = { page -> when (page) { 0 -> "today"; homePages + 1 -> "library"; else -> "home:$page" } },
                        beyondViewportPageCount = 0) { page ->
                        when (page) {
                            0 -> TodayPage(now, widgets, suggestions, apps, onSearch = ::openSearch,
                                onSettings = { sheet = Sheet.Settings }, onWidgets = { sheet = Sheet.Widgets },
                                onClock = { system(AlarmClock.ACTION_SHOW_ALARMS) },
                                onCalendar = ::openCalendar, onLaunch = ::launch)
                            homePages + 1 -> AppLibrary(ui.apps, apps, suggestions, { sheet = Sheet.LibrarySearch }, ::launch,
                                ::actions, { title, records, bounds -> folderAnchor = origin(bounds); sheet = Sheet.Category(title, records) })
                            else -> {
                                val index = page - 1
                                val capacity = if (index == 0) firstCapacity else geometry.pageSize
                                val begin = if (index == 0) 0 else firstCapacity + (index - 1) * geometry.pageSize
                                val entries = ui.layout.home.drop(begin).take(capacity)
                                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
                                    if (index == 0 && withWidgets) HomeWidgets(now, Modifier.height((rowHeight * 2).dp),
                                        onClock = { system(AlarmClock.ACTION_SHOW_ALARMS) }, onCalendar = ::openCalendar)
                                    repeat(capacity / GRID_COLUMNS) { row ->
                                        Row(Modifier.fillMaxWidth().height(rowHeight.dp)) {
                                            repeat(GRID_COLUMNS) { column ->
                                                val entry = entries.getOrNull(row * GRID_COLUMNS + column)
                                                if (entry == null) Spacer(Modifier.weight(1f).fillMaxHeight())
                                                else key(entry.id) {
                                                    LauncherTile(entry, apps, geometry.iconSize.dp, appearance.showLabels,
                                                        Modifier.weight(1f).fillMaxHeight().testTag("home_entry:${entry.id}"), editing, drag?.entry?.id == entry.id,
                                                        onClick = { openEntry(entry, it) }, onActions = { actionTarget = AppActionTarget(entry, it) },
                                                        onBounds = { cellBounds[entry.id] = it }, onRemove = {
                                                            if (entry is HomeEntry.App) model.removeApp(entry.id) else model.dissolve(entry.id)
                                                        })
                                                    DisposableEffect(entry.id) { onDispose { cellBounds.remove(entry.id) } }
                                                }
                                            }
                                        }
                                    }
                                    if (entries.isEmpty() && !withWidgets) Text("Swipe left to see your apps", color = Color.White.copy(alpha = .7f),
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
                        } else GlassPanel(Modifier.testTag("library_button").clickable { openSearch() }, radius = 18f, tint = .1f, interactive = true) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Search, null, Modifier.size(14.dp), tint = Color.White)
                                Text("Search", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 5.dp))
                            }
                        }
                    }
                    GlassPanel(Modifier.padding(horizontal = 11.dp).fillMaxWidth().testTag("dock")
                        .onGloballyPositioned { dockBounds = it.boundsInRoot() }, radius = 35f, tint = .16f) {
                        Row(Modifier.fillMaxWidth().height(maxOf(91f, geometry.iconSize + 28f).dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(DOCK_SLOTS) { slot ->
                                val id = ui.layout.dock.getOrNull(slot)
                                if (id == null) Box(Modifier.weight(1f).fillMaxHeight().clickable { openSearch() }, contentAlignment = Alignment.Center) {
                                    if (editing) Icon(Icons.Outlined.Add, "Add to Dock", tint = Color.White.copy(alpha = .6f))
                                } else key(id) {
                                    LauncherTile(HomeEntry.App(id), apps, geometry.iconSize.dp, false,
                                        Modifier.weight(1f).fillMaxHeight(), editing, drag?.entry?.id == id,
                                        onClick = { openEntry(HomeEntry.App(id), it) }, onActions = { actions(id, it) },
                                        onBounds = { cellBounds[id] = it }, onRemove = { model.addHome(id) })
                                    DisposableEffect(id) { onDispose { cellBounds.remove(id) } }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
            if (editing && !modalPresent) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassPanel(radius = 20f) { Text("Add Widgets", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.clickable { sheet = Sheet.Widgets }.padding(horizontal = 14.dp, vertical = 10.dp)) }
                Row {
                    IconButton(onClick = { sheet = Sheet.Settings }, modifier = Modifier.testTag("settings_button")) { Icon(Icons.Outlined.Settings, "Settings", tint = LocalGlassContentColor.current) }
                    GlassPanel(radius = 20f) { Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("edit_done").clickable { editing = false }.padding(horizontal = 17.dp, vertical = 10.dp)) }
                }
            }
            drag?.let { session ->
                val px = with(density) { geometry.iconSize.dp.toPx() }
                EntryIcon(session.entry, apps, geometry.iconSize.dp, Modifier.offset {
                    IntOffset((session.position.x - rootPosition.x - px / 2).roundToInt(), (session.position.y - rootPosition.y - px / 2).roundToInt()) })
            }
            // Scrim stays in screen space while the panel grows from its icon.
            Box(Modifier.fillMaxSize().then(if (sheet != null && !opaqueScreen) Modifier.pointerInput(sheet) {
                detectTapGestures(onTap = { closeSurface() })
            } else Modifier).drawBehind {
                if (!opaqueScreen) drawRect(Color.Black.copy(alpha = surfaceProgress * if (lightWallpaper) .58f else .28f))
            })
            SurfaceHost(sheet, kind = {
                when (it) {
                    is Sheet.Folder -> SurfaceKind.Folder
                    is Sheet.Category -> SurfaceKind.Category
                    Sheet.Search, Sheet.LibrarySearch -> SurfaceKind.Search
                    Sheet.Controls -> SurfaceKind.Controls
                    else -> SurfaceKind.Page
                }
            }, origin = folderAnchor, onClose = ::closeSurface, progressChanged = { surfaceProgress = it }) { current ->
                Box(Modifier.fillMaxSize().then(if (actionTarget != null || choiceSheet != null) Modifier.clearAndSetSemantics {} else Modifier)
                    .graphicsLayer {
                        val r = if (appearance.reducedEffects) 0f else menuBlur.value * 9.dp.toPx()
                        renderEffect = if (r > .05f) BlurEffect(r, r, TileMode.Clamp) else null
                    }) {
                    // Consume taps in modal gaps as well as on its controls.
                    // A transparent/blurred background must never launch an app.
                    Box(Modifier.fillMaxSize().pointerInput(current) { detectTapGestures(onTap = { closeSurface() }) })
                    when (current) {
                        Sheet.Search, Sheet.LibrarySearch -> Spotlight(ui.apps, apps, suggestions,
                            { focusManager.clearFocus(); keyboard?.hide(); sheet = null }, ::launch, ::actions, current == Sheet.LibrarySearch)
                        Sheet.Settings -> savedPages.SaveableStateProvider("settings") {
                            SettingsSheet(ui, defaultHome, ::closeSurface, model::appearance, onSetHome,
                            { photoPicker.launch(arrayOf("image/*")) }, { model.preparePhoto() }, photoBusy,
                            { backupExport.launch("vitreo-layout-${BuildConfig.VERSION_NAME}.json") },
                            { backupImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            { reportExport.launch("vitreo-diagnostics-${BuildConfig.VERSION_NAME}.json") }, model::refresh, model::resetLayout,
                            { pushPage(Sheet.Licenses) }, { pushPage(Sheet.Widgets) })
                        }
                        Sheet.Wallpaper -> {
                            var cachedDraft by remember { mutableStateOf(photoDraft) }
                            SideEffect { if (photoDraft != null) cachedDraft = photoDraft }
                            (photoDraft ?: cachedDraft)?.let { draft -> WallpaperEditor(draft, photoBusy,
                                { model.discardPhoto(); sheet = Sheet.Settings }, model::savePhoto) }
                        }
                        Sheet.Widgets -> WidgetGallery(widgets, appearance.showWidgets, { model.appearance(ui.layout.appearance.copy(showWidgets = it)) },
                            onConfigureWidget, ::closeSurface, model::message)
                        Sheet.Licenses -> LicensesSheet(::closeSurface)
                        Sheet.Controls -> ControlCenter(MaterialTheme.colorScheme.background.luminance() < .5f, { sheet = null }, ::system,
                            { model.appearance(ui.layout.appearance.copy(interfaceStyle = if (it) 2 else 1)) }, { pushPage(Sheet.Settings) }, { pushPage(Sheet.Widgets) })
                        is Sheet.Folder -> {
                            val live = ui.layout.home.filterIsInstance<HomeEntry.Folder>().find { it.id == current.id }
                            var snapshot by remember(current.id) { mutableStateOf(live) }
                            SideEffect { if (live != null) snapshot = live }
                            LaunchedEffect(live) { if (live == null && sheet == current) sheet = null }
                            val folder = live ?: snapshot
                            if (folder != null) FolderSheet(folder, apps, { sheet = null }, ::launch, ::actions, { model.renameFolder(folder.id, it) })
                        }
                        is Sheet.Category -> CategoryScreen(current.title, current.apps, apps, { sheet = null }, ::launch, ::actions)
                        is Sheet.PairWith, is Sheet.AddToFolder -> Unit
                    }
                }
            }
            AppContextMenu(actionTarget, ui.layout, apps, { actionTarget = null }, ::launch,
                { shortcut, bounds ->
                    if (!launching && onShortcut(shortcut, bounds)) {
                        launching = true; actionTarget = null
                        scope.launch { departure.animateTo(1f, if (appearance.reducedEffects) tween(0) else tween(240)) }
                    }
                },
                { model.addHome(it); actionTarget = null }, { model.pinDock(it); actionTarget = null },
                { model.removeApp(it); actionTarget = null }, { actionTarget = null; onAppInfo(it) },
                { actionReturn = actionTarget; actionTarget = null; choiceSheet = Sheet.PairWith(it) },
                { actionReturn = actionTarget; actionTarget = null; choiceSheet = Sheet.AddToFolder(it) },
                { actionTarget = null; sheet = Sheet.Folder(it) }, { model.dissolve(it); actionTarget = null }, {
                    scope.launch { pager.scrollToPage(pager.currentPage.coerceIn(1, homePages)); editing = true; sheet = null; actionTarget = null }
                })
            SurfaceHost(choiceSheet, { SurfaceKind.Page }, TransformOrigin.Center, ::closeSurface) { choice ->
                when (choice) {
                    is Sheet.PairWith -> ChoiceSheet("Create Folder", ui.layout.home.filterIsInstance<HomeEntry.App>().filter { it.id != choice.app }
                        .map { it.id to entryLabel(it, apps) }, ::closeSurface) { model.folder(choice.app, it); choiceSheet = null; actionReturn = null }
                    is Sheet.AddToFolder -> ChoiceSheet("Move to Folder", ui.layout.home.filterIsInstance<HomeEntry.Folder>().filter { choice.app !in it.apps && it.apps.size < MAX_FOLDER_APPS }
                        .map { it.id to it.title }, ::closeSurface) { model.addToFolder(choice.app, it); choiceSheet = null; actionReturn = null }
                    else -> Unit
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp))
        }
    }
}
