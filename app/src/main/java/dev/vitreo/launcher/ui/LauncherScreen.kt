@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.vitreo.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.BuildConfig
import dev.vitreo.launcher.LauncherViewModel
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.graphics.Viewport
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Test-observable readiness; it does not expose the app inventory. */
val LauncherReadyKey = SemanticsPropertyKey<Boolean>("LauncherReady")

private sealed interface Sheet {
    data object Library : Sheet
    data object Settings : Sheet
    data object Licenses : Sheet
    data class Folder(val id: String) : Sheet
    data class Actions(val entry: HomeEntry) : Sheet
    data class PairWith(val app: String) : Sheet
    data class AddToFolder(val app: String) : Sheet
}
private data class DragSession(val entry: HomeEntry, val position: Offset)

@Composable
fun LauncherScreen(
    model: LauncherViewModel,
    homeSerial: Int,
    defaultHome: Boolean,
    onSetHome: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val frame by model.wallpaper.collectAsStateWithLifecycle()
    val apps = remember(ui.apps) { ui.apps.associateBy { it.id } }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var editing by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(Viewport()) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var workspaceSize by remember { mutableStateOf(IntSize(1080, 1440)) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    val cellBounds = remember { mutableMapOf<String, Rect>() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val localView = LocalView.current
    val density = LocalDensity.current
    val geometry = remember(workspaceSize, ui.layout.appearance.iconSize, density.density, density.fontScale) {
        workspaceGeometry(workspaceSize.width / density.density, workspaceSize.height / density.density,
            LayoutPreset(iconSize = ui.layout.appearance.iconSize), density.fontScale)
    }
    val count = pageCount(ui.layout.home.size, geometry.pageSize)
    val pager = rememberPagerState(pageCount = { count })
    val appearance = ui.layout.appearance.copy(reducedEffects = ui.layout.appearance.reducedEffects || ui.powerSaver)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importPhoto) }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::exportLayout) }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importLayout) }
    val reportExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::exportDiagnostics) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L); value = System.currentTimeMillis() }
    }
    LaunchedEffect(Unit) { model.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(homeSerial) { sheet = null; editing = false; drag = null; pager.scrollToPage(0) }
    LaunchedEffect(count) { if (pager.currentPage >= count) pager.scrollToPage(count - 1) }
    BackHandler {
        when {
            sheet != null -> sheet = null
            editing -> { editing = false; drag = null }
            pager.currentPage > 0 -> scope.launch { pager.animateScrollToPage(0) }
            else -> Unit
        }
    }
    val openEntry: (HomeEntry) -> Unit = { entry ->
        if (editing) sheet = Sheet.Actions(entry)
        else when (entry) {
            is HomeEntry.App -> onLaunch(entry.id)
            is HomeEntry.Folder -> sheet = Sheet.Folder(entry.id)
        }
    }
    val drop: () -> Unit = {
        drag?.let { session ->
            if (dockBounds.contains(session.position) && session.entry is HomeEntry.App) model.pinDock(session.entry.id)
            else {
                val valid = ui.layout.home.map { it.id }.toSet()
                cellBounds.entries.firstOrNull { (id, rect) ->
                    id != session.entry.id && id in valid && rect.width > 1 && rect.height > 1 && rect.contains(session.position)
                }?.key?.let { model.moveToSlot(session.entry.id, it) }
            }
        }
        drag = null
    }

    CompositionLocalProvider(LocalGlass provides GlassEnvironment(frame, viewport, appearance)) {
        Box(Modifier.fillMaxSize().testTag("launcher_root").semantics { this[LauncherReadyKey] = ui.ready && frame != null }.background(Color(0xff101b2b)).onGloballyPositioned { coordinates ->
            val screen = IntArray(2); val window = IntArray(2)
            localView.getLocationOnScreen(screen); localView.getLocationInWindow(window)
            val origin = coordinates.positionInWindow()
            viewport = Viewport(coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
                origin.x + screen[0] - window[0], origin.y + screen[1] - window[1])
            rootPosition = coordinates.positionInRoot()
        }) {
            frame?.let { Image(it.sharp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .20f), Color.Transparent, Color.Black.copy(alpha = .13f)))))
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(DateFormat.getTimeFormat(context).format(Date(now)), color = Color.White,
                            fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now)),
                            color = Color.White.copy(alpha = .88f), fontSize = 13.sp)
                    }
                    IconButton(onClick = { editing = !editing; drag = null }, enabled = ui.ready,
                        modifier = Modifier.testTag("edit_button")) {
                        Icon(if (editing) Icons.Outlined.Done else Icons.Outlined.Edit,
                            if (editing) "Terminar edici\u00f3n" else "Editar escritorio", tint = Color.White)
                    }
                    IconButton(onClick = { sheet = Sheet.Settings }, modifier = Modifier.testTag("settings_button")) {
                        Icon(Icons.Outlined.Tune, "Ajustes de Vitreo", tint = Color.White)
                    }
                }
                if (!defaultHome) {
                    TextButton(onClick = onSetHome, modifier = Modifier.fillMaxWidth().testTag("set_home_button")) {
                        Text("Usar Vitreo como app de inicio", color = Color.White)
                    }
                }
                if (editing) {
                    Text("Mant\u00e9n pulsado y arrastra. Toca un icono para m\u00e1s acciones.",
                        color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { workspaceSize = it }) {
                    if (!ui.ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else if (ui.layout.home.isEmpty()) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tu escritorio est\u00e1 vac\u00edo", color = Color.White)
                            TextButton(onClick = { sheet = Sheet.Library }) { Text("Abrir aplicaciones") }
                        }
                    } else HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = drag == null, beyondViewportPageCount = 0) { page ->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            val entries = ui.layout.home.drop(page * geometry.pageSize).take(geometry.pageSize)
                            repeat(geometry.rows) { row ->
                                Row(Modifier.fillMaxWidth().height(geometry.cells.rowHeight.dp)) {
                                    repeat(GRID_COLUMNS) { column ->
                                        val entry = entries.getOrNull(row * GRID_COLUMNS + column)
                                        if (entry == null) Spacer(Modifier.weight(1f).fillMaxHeight())
                                        else key(entry.id) {
                                            LauncherTile(entry, apps, geometry.iconSize.dp, ui.layout.appearance.showLabels,
                                                modifier = Modifier.weight(1f).fillMaxHeight(), editing = editing,
                                                dragging = drag?.entry?.id == entry.id,
                                                onClick = { openEntry(entry) }, onActions = { sheet = Sheet.Actions(entry) },
                                                onBounds = { cellBounds[entry.id] = it },
                                                onDragStart = { drag = DragSession(entry, it) },
                                                onDrag = { delta -> drag = drag?.let { it.copy(position = it.position + delta) } },
                                                onDragEnd = drop, onDragCancel = { drag = null })
                                            DisposableEffect(entry.id) { onDispose { cellBounds.remove(entry.id) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    if (count <= 9) repeat(count) { page ->
                        Box(Modifier.padding(4.dp).size(if (pager.currentPage == page) 7.dp else 5.dp)
                            .background(Color.White.copy(alpha = if (pager.currentPage == page) .95f else .4f), CircleShape))
                    } else Text("${pager.currentPage + 1} / $count", color = Color.White, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                    GlassPanel(radius = 24f) {
                        TextButton(onClick = { sheet = Sheet.Library }, modifier = Modifier.testTag("library_button"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp)); Text("Buscar", color = Color.White)
                        }
                    }
                }
                GlassPanel(Modifier.fillMaxWidth().testTag("dock").onGloballyPositioned { dockBounds = it.boundsInRoot() }, radius = 30f) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(DOCK_SLOTS) { slot ->
                            val id = ui.layout.dock.getOrNull(slot)
                            if (id == null) Box(Modifier.weight(1f).height(68.dp), contentAlignment = Alignment.Center) {
                                IconButton(onClick = { sheet = Sheet.Library }) { Icon(Icons.Outlined.Add, "A\u00f1adir al dock", tint = Color.White.copy(alpha = .7f)) }
                            } else LauncherTile(HomeEntry.App(id), apps, minOf(geometry.iconSize, 58f).dp, false,
                                modifier = Modifier.weight(1f).height(68.dp),
                                onClick = { if (editing) sheet = Sheet.Actions(HomeEntry.App(id)) else onLaunch(id) },
                                onActions = { sheet = Sheet.Actions(HomeEntry.App(id)) })
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            drag?.let { session ->
                val iconPx = with(density) { geometry.iconSize.dp.toPx() }
                EntryIcon(session.entry, apps, geometry.iconSize.dp,
                    Modifier.offset { IntOffset((session.position.x - rootPosition.x - iconPx / 2).roundToInt(),
                        (session.position.y - rootPosition.y - iconPx / 2).roundToInt()) })
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 108.dp))
        }

        val current = sheet
        if (current != null) {
            ModalBottomSheet(onDismissRequest = { sheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.Transparent, scrimColor = Color.Black.copy(alpha = .25f), dragHandle = null) {
                GlassPanel(Modifier.fillMaxWidth(), radius = 30f, tint = .70f) {
                    when (current) {
                        Sheet.Library -> LibrarySheet(ui.apps, apps, onClose = { sheet = null },
                            onLaunch = { sheet = null; onLaunch(it) }, onActions = { sheet = Sheet.Actions(HomeEntry.App(it)) })
                        Sheet.Settings -> SettingsSheet(ui, defaultHome, onClose = { sheet = null }, onAppearance = model::appearance,
                            onSetHome = onSetHome, onPhoto = { photoPicker.launch(arrayOf("image/*")) },
                            onExport = { backupExport.launch("vitreo-layout-${BuildConfig.VERSION_NAME}.json") },
                            onImport = { backupImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            onDiagnostics = { reportExport.launch("vitreo-diagnostics-${BuildConfig.VERSION_NAME}.json") },
                            onRefresh = model::refresh, onReset = model::resetLayout,
                            onLicenses = { sheet = Sheet.Licenses })
                        Sheet.Licenses -> LicensesSheet { sheet = Sheet.Settings }
                        is Sheet.Folder -> {
                            val folder = ui.layout.home.filterIsInstance<HomeEntry.Folder>().find { it.id == current.id }
                            if (folder != null) FolderSheet(folder, apps, onClose = { sheet = null },
                                onLaunch = { sheet = null; onLaunch(it) }, onActions = { sheet = Sheet.Actions(HomeEntry.App(it)) },
                                onEdit = { sheet = Sheet.Actions(folder) })
                            else { LaunchedEffect(current.id) { sheet = null } }
                        }
                        is Sheet.Actions -> ActionsSheet(current.entry, ui.layout, apps, onClose = { sheet = null },
                            onHome = { model.addHome(it); sheet = null }, onDock = { model.pinDock(it); sheet = null },
                            onRemove = { model.removeApp(it); sheet = null }, onMove = { id, delta -> model.move(id, delta) },
                            onInfo = { sheet = null; onAppInfo(it) }, onPair = { sheet = Sheet.PairWith(it) },
                            onChooseFolder = { sheet = Sheet.AddToFolder(it) },
                            onRename = { id, title -> model.renameFolder(id, title); sheet = null },
                            onDissolve = { model.dissolve(it); sheet = null })
                        is Sheet.PairWith -> ChoiceSheet("Crear carpeta con...", ui.layout.home.filterIsInstance<HomeEntry.App>()
                            .filterNot { it.id == current.app }.map { it.id to entryLabel(it, apps) }, onClose = { sheet = null }) {
                            model.folder(current.app, it); sheet = null
                        }
                        is Sheet.AddToFolder -> ChoiceSheet("A\u00f1adir a carpeta", ui.layout.home.filterIsInstance<HomeEntry.Folder>()
                            .filter { current.app !in it.apps && it.apps.size < MAX_FOLDER_APPS }.map { it.id to it.title }, onClose = { sheet = null }) {
                            model.addToFolder(current.app, it); sheet = null
                        }
                    }
                }
            }
        }
    }
}
