package dev.vitreo.launcher.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.ControlCenterController
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.roundToInt

private data class ControlShortcut(val title: String, val icon: ImageVector)
private fun shortcut(id: String) = when (id) {
    "flashlight" -> ControlShortcut("Flashlight", Icons.Outlined.FlashlightOn)
    "timer" -> ControlShortcut("Timer", Icons.Outlined.Timer)
    "calculator" -> ControlShortcut("Calculator", Icons.Outlined.Calculate)
    "camera" -> ControlShortcut("Camera", Icons.Outlined.PhotoCamera)
    "battery" -> ControlShortcut("Battery Saver", Icons.Outlined.BatterySaver)
    "cast" -> ControlShortcut("Screen Cast", Icons.Outlined.Cast)
    "widgets" -> ControlShortcut("Widgets", Icons.Outlined.Widgets)
    "nfc" -> ControlShortcut("NFC", Icons.Outlined.Nfc)
    "accessibility" -> ControlShortcut("Accessibility", Icons.Outlined.AccessibilityNew)
    "alarms" -> ControlShortcut("Alarms", Icons.Outlined.Alarm)
    "display" -> ControlShortcut("Display", Icons.Outlined.BrightnessMedium)
    else -> ControlShortcut("Vitreo Settings", Icons.Outlined.Settings)
}

@Composable
private fun RoundControl(title: String, icon: ImageVector, modifier: Modifier = Modifier, active: Boolean = false,
    enabled: Boolean = true, color: Color = Color(0xff0a84ff), wide: Boolean = false, onClick: () -> Unit) {
    val shape = if (wide) RoundedCornerShape(26.dp) else CircleShape
    GlassPanel(modifier.clip(shape).testTag("control:$title").semantics {
        contentDescription = title
        stateDescription = if (!enabled) "Unavailable" else if (active) "On" else "Open"
    }.clickable(enabled = enabled, onClick = onClick), radius = if (wide) 26f else 100f, tint = .19f, interactive = enabled) {
        Row(Modifier.fillMaxSize().background(if (active) color.copy(alpha = .86f) else Color.Transparent)
            .graphicsLayer { alpha = if (enabled) 1f else .35f }.padding(if (wide) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (wide) Arrangement.spacedBy(12.dp) else Arrangement.Center) {
            LauncherSymbol(icon, null, Modifier.size(28.dp), tint = Color.White)
            if (wide) Text(title, color = Color.White, fontSize = 14.sp, maxLines = 2, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Accessible vertical slider; drawing/gesture state stays local to this control. */
@Composable
private fun ControlSlider(title: String, icon: ImageVector, value: Float, enabled: Boolean = true,
    detail: String = "${(value * 100).roundToInt()}%", height: androidx.compose.ui.unit.Dp = 176.dp, onChange: (Float) -> Unit, onFinish: (Float) -> Unit = {}) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val rangeValue = value.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassPanel(Modifier.fillMaxWidth().height(height).testTag("control_slider:$title")
            .semantics {
                contentDescription = title; stateDescription = if (enabled) detail else "Fixed by Android"
                progressBarRangeInfo = ProgressBarRangeInfo(rangeValue, 0f..1f)
                if (enabled) setProgress { requested ->
                    val next = requested.coerceIn(0f, 1f); change(next); finish(next); true
                } else disabled()
            }
            .then(if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(); down.consume()
                    var last = (1f - down.position.y / size.height).coerceIn(0f, 1f)
                    change(last)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) break
                            last = (1f - pointer.position.y / size.height).coerceIn(0f, 1f)
                            pointer.consume(); change(last)
                        }
                    } finally { finish(last) }
                }
            }), radius = 31f, tint = .23f) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(rangeValue).align(Alignment.BottomCenter)
                    .background(if (LocalCyber.current) CyberColors.Cyan.copy(alpha = .8f) else Color.White.copy(alpha = .94f)))
                LauncherSymbol(icon, null, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(27.dp),
                    tint = if (rangeValue > .18f) Color(0xff33343a) else Color.White)
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCenter(appearance: Appearance, dark: Boolean, onAppearance: (Appearance) -> Unit,
    onClose: () -> Unit, onSystem: (String) -> Unit, onUtility: (String) -> Unit,
    onSettings: () -> Unit, onWidgets: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val controller = remember { ControlCenterController(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var torchCamera by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controller) {
        torchCamera = withContext(Dispatchers.IO) { controller.discoverTorch() }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.start(torchCamera)
    }
    val currentTorch by rememberUpdatedState(torchCamera)
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) controller.start(currentTorch)
            if (event == Lifecycle.Event.ON_RESUME) controller.refresh()
            if (event == Lifecycle.Event.ON_STOP) controller.stop()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { controller.refresh() }
        }
        // A system notification refreshes read-back values; the broadcast carries no trusted state.
        ContextCompat.registerReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION").apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.start(currentTorch)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver); controller.stop() }
    }
    val device = controller.state
    var editing by remember { mutableStateOf(false) }
    BackHandler(editing) { editing = false }
    var volumeErrorShown by remember { mutableStateOf(false) }
    var brightness by remember(appearance.homeBrightness) { mutableFloatStateOf(appearance.homeBrightness) }
    val latestAppearance by rememberUpdatedState(appearance)
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            if (window.attributes.screenBrightness != brightness)
                window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
    }
    DisposableEffect(Unit) {
        onDispose { (context as? Activity)?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = latestAppearance.homeBrightness }
        } }
    }
    fun activate(id: String) {
        when (id) {
            "flashlight" -> if (!controller.toggleTorch()) onError("Flashlight is unavailable. Another app or Android may be using it.")
            "battery" -> onSystem(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "cast" -> onSystem(Settings.ACTION_CAST_SETTINGS)
            "nfc" -> onSystem(Settings.ACTION_NFC_SETTINGS)
            "accessibility" -> onSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "display" -> onSystem(Settings.ACTION_DISPLAY_SETTINGS)
            "settings" -> onSettings()
            "widgets" -> onWidgets()
            else -> onUtility(id)
        }
    }
    var choosing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalGlass.current.appearance.reducedEffects
    fun pageTo(index: Int) { scope.launch { if (reduceMotion) pager.scrollToPage(index) else pager.animateScrollToPage(index) } }
    BackHandler(choosing) { choosing = false }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp).testTag("control_center")) {
        val gap = 12.dp
        val rail = 22.dp
        val unit = minOf((maxWidth - rail - 14.dp - gap * 3) / 4, 80.dp)
        val double = unit * 2 + gap
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { editing = !editing }, modifier = Modifier.testTag("edit_controls")) {
                    LauncherSymbol(if (editing) Icons.Outlined.Check else Icons.Outlined.Add, if (editing) "Done" else "Edit controls", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onSystem(Settings.ACTION_BATTERY_SAVER_SETTINGS) }) {
                    LauncherSymbol(Icons.Outlined.PowerSettingsNew, "Android power settings", Modifier.size(23.dp), tint = Color.White)
                }
                IconButton(onClick = onClose) { LauncherSymbol(Icons.Outlined.Close, "Close", Modifier.size(21.dp), tint = Color.White.copy(alpha = .7f)) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = rail, top = 5.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(DateFormat.getTimeFormat(context).format(Date(LocalHomeClock.current)), color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                if (device.battery >= 0) Text("${device.battery}%", color = Color.White, fontSize = 13.sp)
                LauncherSymbol(Icons.Outlined.BatteryStd, null, Modifier.padding(start = 4.dp).size(19.dp), tint = Color.White)
            }
            Row(Modifier.weight(1f)) {
                VerticalPager(pager, Modifier.weight(1f).testTag("control_pages"), userScrollEnabled = !editing,
                    pageSpacing = 24.dp) { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 7.dp, top = 7.dp, end = 7.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                        when (page) {
                            0 -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    ConnectivityTile(Modifier.size(double), device.airplane, device.wifi, device.bluetooth, onSystem,
                                        onExpand = { pageTo(2) })
                                    MediaTile(Modifier.size(double), device.musicActive, onUtility, { if (!controller.mediaKey(it)) onError("Android could not send the media command.") },
                                        onExpand = { pageTo(1) })
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    Column(Modifier.width(double), verticalArrangement = Arrangement.spacedBy(gap)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                            RoundControl("Rotation", Icons.Outlined.ScreenRotation, Modifier.size(unit)) { onSystem(Settings.ACTION_DISPLAY_SETTINGS) }
                                            RoundControl(if (LocalCyber.current) "HUD Effects" else "Dark Mode", if (LocalCyber.current) Icons.Outlined.Tune else Icons.Outlined.DarkMode,
                                                Modifier.size(unit), active = if (LocalCyber.current) appearance.cyberEffects else dark) {
                                                onAppearance(if (appearance.themePreset == 1) appearance.copy(cyberEffects = !appearance.cyberEffects)
                                                    else appearance.copy(interfaceStyle = if (dark) 1 else 2))
                                            }
                                        }
                                        RoundControl("Focus", Icons.Outlined.Bedtime, Modifier.width(double).height(unit), wide = true) { onSystem("android.settings.ZEN_MODE_SETTINGS") }
                                    }
                                    Box(Modifier.width(unit)) { ControlSlider("Home brightness", Icons.Outlined.BrightnessHigh,
                                        if (brightness < 0) device.systemBrightness else brightness, height = double,
                                        detail = if (brightness < 0) "System" else "${(brightness * 100).roundToInt()}%",
                                        onChange = { brightness = it.coerceIn(.02f, 1f) },
                                        onFinish = { onAppearance(latestAppearance.copy(homeBrightness = it.coerceIn(.02f, 1f))) }) }
                                    Box(Modifier.width(unit)) { ControlSlider("Media volume", if (device.volume == 0f) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                        device.volume, !device.volumeFixed, height = double, onChange = {
                                            if (!controller.volume(it) && !volumeErrorShown) {
                                                volumeErrorShown = true; onError("Android did not allow the volume change. Use Sound settings.")
                                            }
                                        }) }
                                }
                                controlRows(appearance.controlShortcuts, appearance.controlWide).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                        row.forEach { id ->
                                            val item = shortcut(id)
                                            val wide = id in appearance.controlWide
                                            Box(Modifier.width(if (wide) double else unit).height(unit)) {
                                                RoundControl(item.title, item.icon, Modifier.fillMaxSize().then(if (!editing) Modifier else Modifier.pointerInput(id) {
                                                    var distance = 0f
                                                    detectDragGesturesAfterLongPress(onDragStart = { distance = 0f }) { change, delta ->
                                                        change.consume(); distance += delta.x + delta.y
                                                        if (kotlin.math.abs(distance) > 36.dp.toPx()) {
                                                            val current = latestAppearance
                                                            val index = current.controlShortcuts.indexOf(id)
                                                            val next = (index + if (distance > 0) 1 else -1).coerceIn(0, current.controlShortcuts.lastIndex)
                                                            onAppearance(current.copy(controlShortcuts = moveTodaySection(current.controlShortcuts, id, next)))
                                                            distance = 0f
                                                        }
                                                    }
                                                }), active = id == "flashlight" && device.torchOn, wide = wide,
                                                    enabled = editing || id != "flashlight" || device.torchAvailable) { if (!editing) activate(id) }
                                                if (editing) {
                                                    IconButton(onClick = { onAppearance(appearance.copy(controlShortcuts = appearance.controlShortcuts - id)) },
                                                        modifier = Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp).size(30.dp).background(Color(0xffd4d6da), CircleShape)) {
                                                        Icon(Icons.Outlined.Remove, "Remove ${item.title}", Modifier.size(18.dp), tint = Color(0xff303137))
                                                    }
                                                    IconButton(onClick = { onAppearance(appearance.copy(controlWide = if (wide) appearance.controlWide - id else appearance.controlWide + id)) },
                                                        modifier = Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(30.dp).testTag("resize_control:$id")) {
                                                        Icon(if (wide) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull, "Resize ${item.title}", Modifier.size(19.dp), tint = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (editing) {
                                    TextButton(onClick = { choosing = true; query = "" }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("add_control")) {
                                        LauncherSymbol(Icons.Outlined.AddCircleOutline, null, Modifier.size(18.dp), tint = Color.White)
                                        Text("  Add a Control", color = Color.White)
                                    }
                                    Text("Hold and drag a shortcut to reorder it. Use its corner to resize.", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
                                }
                                TextButton(onClick = { brightness = -1f; onAppearance(appearance.copy(homeBrightness = -1f)) },
                                    modifier = Modifier.testTag("system_brightness").heightIn(min = 40.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text(if (brightness < 0) "Brightness · System" else "Use system brightness", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                                }
                            }
                            1 -> {
                                MediaTile(Modifier.fillMaxWidth().heightIn(min = double * 2 + gap), device.musicActive, onUtility,
                                    { if (!controller.mediaKey(it)) onError("Android could not send the media command.") }, expanded = true)
                                Text("Media", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                                Text("Playback commands go to Android's active media app. Open your player for track details.", color = Color.White.copy(alpha = .65f), fontSize = 13.sp)
                                RoundControl("Open music app", Icons.Outlined.MusicNote, Modifier.fillMaxWidth().height(unit), wide = true) { onUtility("music") }
                            }
                            2 -> {
                                Text("Connectivity", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                                listOf(Triple("Airplane", Icons.Outlined.AirplanemodeActive, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                                    Triple("Internet", Icons.Outlined.Wifi, Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
                                    Triple("Bluetooth", Icons.Outlined.Bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
                                    Triple("Hotspot", Icons.Outlined.WifiTethering, "android.settings.TETHER_SETTINGS"),
                                    Triple("NFC", Icons.Outlined.Nfc, Settings.ACTION_NFC_SETTINGS),
                                    Triple("Screen Cast", Icons.Outlined.Cast, Settings.ACTION_CAST_SETTINGS)).forEach { (title, icon, action) ->
                                    RoundControl(title, icon, Modifier.fillMaxWidth().height(unit), wide = true,
                                        active = when (title) { "Airplane" -> device.airplane == true; "Internet" -> device.wifi == true; "Bluetooth" -> device.bluetooth == true; else -> false }) { onSystem(action) }
                                }
                                Text("Network controls open Android settings. Their state is read from the device.", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
                            }
                        }
                    }
                }
                Column(Modifier.width(rail).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf(Icons.Outlined.FavoriteBorder, Icons.Outlined.MusicNote, Icons.Outlined.Wifi).forEachIndexed { index, icon ->
                        IconButton(onClick = { editing = false; pageTo(index) }, modifier = Modifier.width(rail).height(44.dp).testTag("control_page:$index")) {
                            LauncherSymbol(icon, listOf("Favorites page", "Media page", "Connectivity page")[index], Modifier.size(if (pager.currentPage == index) 19.dp else 15.dp),
                                tint = Color.White.copy(alpha = if (pager.currentPage == index) 1f else .38f))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(42.dp).testTag("controls_dismiss_handle"), contentAlignment = Alignment.Center) {
                Box(Modifier.width(70.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .75f)))
            }
        }
    }
    if (choosing) ModalBottomSheet(onDismissRequest = { choosing = false }, containerColor = Color(0xff202b38), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).padding(20.dp).testTag("control_picker")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add a Control", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { focusManager.clearFocus(); choosing = false }) { Text("Done", color = Color.White) }
            }
            OutlinedTextField(query, { query = it.take(50) }, Modifier.fillMaxWidth().padding(vertical = 14.dp).testTag("control_search"),
                placeholder = { Text("Search Controls", color = Color.White.copy(alpha = .65f)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Color.White) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ControlShortcutEditor(appearance, onAppearance, query)
                Text("Network controls open Android panels. Brightness applies to Vitreo; media volume follows Android.", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConnectivityTile(modifier: Modifier, airplane: Boolean?, wifi: Boolean?, bluetooth: Boolean?,
    onSystem: (String) -> Unit, onExpand: () -> Unit) {
    GlassPanel(modifier, radius = 30f, tint = .2f) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkButton("Airplane", Icons.Outlined.AirplanemodeActive, Modifier.weight(1f), airplane, Color(0xffff9f0a)) { onSystem(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
                NetworkButton("Internet", Icons.Outlined.Wifi, Modifier.weight(1f), wifi) { onSystem(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkButton("Bluetooth", Icons.Outlined.Bluetooth, Modifier.weight(1f), bluetooth) { onSystem(Settings.ACTION_BLUETOOTH_SETTINGS) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onExpand) { LauncherSymbol(Icons.Outlined.MoreHoriz, "Expand connectivity", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(modifier: Modifier, active: Boolean, onUtility: (String) -> Unit, onMedia: (Int) -> Unit,
    expanded: Boolean = false, onExpand: () -> Unit = {}) {
    GlassPanel(modifier, radius = 30f, tint = .2f) {
        Column(Modifier.fillMaxSize().padding(if (expanded) 22.dp else 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (expanded) 100.dp else 35.dp).background(Color.White.copy(alpha = .1f), RoundedCornerShape(9.dp)).clickable { onUtility("music") }, contentAlignment = Alignment.Center) {
                    LauncherSymbol(Icons.Outlined.MusicNote, "Open music app", Modifier.size(if (expanded) 44.dp else 22.dp), tint = Color.White.copy(alpha = .7f))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = if (expanded) { { onUtility("music") } } else onExpand, modifier = Modifier.size(32.dp)) {
                    LauncherSymbol(Icons.Outlined.Podcasts, if (expanded) "Open player" else "Expand media", Modifier.size(20.dp), tint = Color.White)
                }
            }
            Text(if (active) "Playing audio" else "Not Playing", color = Color.White, fontSize = if (expanded) 24.sp else 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Triple(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Icons.Outlined.SkipPrevious, "Previous track"),
                    Triple(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, if (active) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play or pause"),
                    Triple(KeyEvent.KEYCODE_MEDIA_NEXT, Icons.Outlined.SkipNext, "Next track")).forEach { (code, icon, label) ->
                    IconButton(onClick = { onMedia(code) }, modifier = Modifier.size(if (expanded) 56.dp else 34.dp)) {
                        LauncherSymbol(icon, label, Modifier.size(if (expanded) 36.dp else 25.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkButton(title: String, icon: ImageVector, modifier: Modifier, active: Boolean? = null,
    activeColor: Color = Color(0xff0a84ff), onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).height(51.dp).semantics {
        contentDescription = "$title settings"
        stateDescription = when (active) { true -> "On"; false -> "Off"; else -> "Android settings" }
    },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(38.dp).background(if (active == true) activeColor else Color.White.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
            LauncherSymbol(icon, null, Modifier.size(22.dp), tint = Color.White)
        }

    }
}

@Composable
private fun ControlShortcutEditor(appearance: Appearance, onChange: (Appearance) -> Unit, query: String = "") {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        GlassPanel(Modifier.fillMaxWidth(), radius = 26f, tint = .28f) {
            Column(Modifier.padding(vertical = 8.dp)) {
                val all = appearance.controlShortcuts + CONTROL_SHORTCUTS.filter { it !in appearance.controlShortcuts }
                all.filter { shortcut(it).title.contains(query.trim(), ignoreCase = true) }.forEach { id ->
                    val position = appearance.controlShortcuts.indexOf(id)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            IosToggleRow(shortcut(id).title, position >= 0, modifier = Modifier.testTag("control_option:$id")) { visible ->
                                onChange(appearance.copy(controlShortcuts = if (visible) appearance.controlShortcuts + id else appearance.controlShortcuts - id))
                            }
                        }
                        if (position >= 0) {
                            IconButton(onClick = { onChange(appearance.copy(controlShortcuts = appearance.controlShortcuts.toMutableList().apply {
                                add(position - 1, removeAt(position)) })) }, enabled = position > 0, modifier = Modifier.size(34.dp)) {
                                LauncherSymbol(Icons.Outlined.KeyboardArrowUp, "Move ${shortcut(id).title} up", Modifier.size(20.dp), tint = Color.White)
                            }
                            IconButton(onClick = { onChange(appearance.copy(controlShortcuts = appearance.controlShortcuts.toMutableList().apply {
                                add(position + 1, removeAt(position)) })) }, enabled = position < appearance.controlShortcuts.lastIndex, modifier = Modifier.size(34.dp)) {
                                LauncherSymbol(Icons.Outlined.KeyboardArrowDown, "Move ${shortcut(id).title} down", Modifier.size(20.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { onChange(appearance.copy(controlShortcuts = DEFAULT_CONTROL_SHORTCUTS, controlWide = emptySet())) }) {
            Text("Restore Default Controls", color = Color.White)
        }
    }
}
