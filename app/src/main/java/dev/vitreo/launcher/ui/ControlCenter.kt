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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    enabled: Boolean = true, color: Color = Color(0xff0a84ff), onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassPanel(Modifier.size(60.dp).testTag("control:$title").semantics {
            contentDescription = title
            stateDescription = if (!enabled) "Unavailable" else if (active) "On" else "Open"
        }.clickable(enabled = enabled, onClick = onClick), radius = 30f, tint = .21f, interactive = enabled) {
            Box(Modifier.fillMaxSize().background(if (active) color.copy(alpha = .85f) else Color.Transparent)
                .graphicsLayer { alpha = if (enabled) 1f else .35f }, contentAlignment = Alignment.Center) {
                LauncherSymbol(icon, null, Modifier.size(25.dp), tint = Color.White)
            }
        }
        Text(title, color = Color.White.copy(alpha = if (enabled) .95f else .5f), fontSize = 11.sp,
            maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp).heightIn(min = 26.dp))
    }
}

/** Accessible vertical slider; drawing/gesture state stays local to this control. */
@Composable
private fun ControlSlider(title: String, icon: ImageVector, value: Float, enabled: Boolean = true,
    detail: String = "${(value * 100).roundToInt()}%", onChange: (Float) -> Unit, onFinish: (Float) -> Unit = {}) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val rangeValue = value.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassPanel(Modifier.fillMaxWidth().height(176.dp).testTag("control_slider:$title")
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
        Text(title, color = Color.White, fontSize = 11.sp, maxLines = 2, lineHeight = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
fun ControlCenter(appearance: Appearance, dark: Boolean, onAppearance: (Appearance) -> Unit,
    onClose: () -> Unit, onSystem: (String) -> Unit, onUtility: (String) -> Unit,
    onSettings: () -> Unit, onWidgets: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
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
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 23.dp).testTag("control_center")) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Control Center", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(DateFormat.getTimeFormat(context).format(Date(LocalHomeClock.current)) +
                    if (device.battery >= 0) "  •  ${device.battery}%" else "", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
            }
            TextButton(onClick = { editing = !editing }, modifier = Modifier.testTag("edit_controls")) {
                Text(if (editing) "Done" else "Edit", color = Color.White)
            }
            IconButton(onClick = onClose) { LauncherSymbol(Icons.Outlined.Close, "Close", tint = Color.White) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editing) {
                Text("Choose your controls", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Tap a switch to add or remove a shortcut. Use the arrows to change its position.", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                ControlShortcutEditor(appearance, onAppearance)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassPanel(Modifier.weight(1f).height(169.dp), radius = 30f, tint = .24f) {
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NetworkButton("Airplane", Icons.Outlined.AirplanemodeActive, Modifier.weight(1f), device.airplane, Color(0xffff9f0a)) { onSystem(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
                                NetworkButton("Internet", Icons.Outlined.Wifi, Modifier.weight(1f), device.wifi) { onSystem(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NetworkButton("Bluetooth", Icons.Outlined.Bluetooth, Modifier.weight(1f), device.bluetooth) { onSystem(Settings.ACTION_BLUETOOTH_SETTINGS) }
                                NetworkButton("Hotspot", Icons.Outlined.WifiTethering, Modifier.weight(1f)) { onSystem("android.settings.TETHER_SETTINGS") }
                            }
                        }
                    }
                    GlassPanel(Modifier.weight(1f).height(169.dp), radius = 30f, tint = .24f) {
                        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                LauncherSymbol(Icons.Outlined.MusicNote, null, Modifier.size(21.dp), tint = Color.White)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { onUtility("music") }, modifier = Modifier.size(36.dp)) {
                                    LauncherSymbol(Icons.Outlined.OpenInNew, "Open music app", Modifier.size(18.dp), tint = Color.White)
                                }
                            }
                            Text(if (device.musicActive) "Playing audio" else "Not Playing", color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("System media controls", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(Triple(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Icons.Outlined.SkipPrevious, "Previous track"),
                                    Triple(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, if (device.musicActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play or pause"),
                                    Triple(KeyEvent.KEYCODE_MEDIA_NEXT, Icons.Outlined.SkipNext, "Next track")).forEach { (code, icon, label) ->
                                    IconButton(onClick = { if (!controller.mediaKey(code)) onError("Android could not send the media command.") }, modifier = Modifier.size(38.dp)) {
                                        LauncherSymbol(icon, label, Modifier.size(25.dp), tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RoundControl("Rotation", Icons.Outlined.ScreenRotation, Modifier.weight(1f)) { onSystem(Settings.ACTION_DISPLAY_SETTINGS) }
                            RoundControl(if (LocalCyber.current) "HUD Effects" else "Dark Mode", if (LocalCyber.current) Icons.Outlined.Tune else Icons.Outlined.DarkMode,
                                Modifier.weight(1f), active = if (LocalCyber.current) appearance.cyberEffects else dark) {
                                onAppearance(if (appearance.themePreset == 1) appearance.copy(cyberEffects = !appearance.cyberEffects)
                                    else appearance.copy(interfaceStyle = if (dark) 1 else 2))
                            }
                        }
                        GlassPanel(Modifier.fillMaxWidth().height(68.dp).clickable { onSystem("android.settings.ZEN_MODE_SETTINGS") }, radius = 27f, tint = .24f, interactive = true) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                LauncherSymbol(Icons.Outlined.Bedtime, null, Modifier.size(25.dp), tint = Color.White)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text("Focus", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text("Android settings", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                                }
                            }
                        }
                        TextButton(onClick = { brightness = -1f; onAppearance(appearance.copy(homeBrightness = -1f)) },
                            modifier = Modifier.testTag("system_brightness").height(30.dp), contentPadding = PaddingValues(0.dp)) {
                            Text("Use system brightness", color = Color.White.copy(alpha = .85f), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    Box(Modifier.weight(1f)) { ControlSlider("Home brightness", Icons.Outlined.BrightnessHigh,
                        if (brightness < 0) device.systemBrightness else brightness,
                        detail = if (brightness < 0) "System" else "${(brightness * 100).roundToInt()}%",
                        onChange = { brightness = it.coerceIn(.02f, 1f) },
                        onFinish = { onAppearance(latestAppearance.copy(homeBrightness = it.coerceIn(.02f, 1f))) }) }
                    Box(Modifier.weight(1f)) { ControlSlider("Media volume", if (device.volume == 0f) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                        device.volume, !device.volumeFixed, onChange = {
                            if (!controller.volume(it) && !volumeErrorShown) {
                                volumeErrorShown = true
                                onError("Android did not allow the volume change. Use Sound settings.")
                            }
                        }) }
                }
                appearance.controlShortcuts.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { id ->
                            val item = shortcut(id)
                            RoundControl(item.title, item.icon, Modifier.weight(1f),
                                active = id == "flashlight" && device.torchOn,
                                enabled = id != "flashlight" || device.torchAvailable) { activate(id) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("Network and device shortcuts open Android panels. Brightness and appearance apply to Vitreo; media volume follows Android.",
                    color = Color.White.copy(alpha = .7f), fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(42.dp).testTag("controls_dismiss_handle"), contentAlignment = Alignment.Center) {
            Box(Modifier.width(70.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .75f)))
        }
    }
}

@Composable
private fun NetworkButton(title: String, icon: ImageVector, modifier: Modifier, active: Boolean? = null,
    activeColor: Color = Color(0xff0a84ff), onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).height(66.dp).semantics {
        contentDescription = "$title settings"
        stateDescription = when (active) { true -> "On"; false -> "Off"; else -> "Android settings" }
    },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(38.dp).background(if (active == true) activeColor else Color.White.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
            LauncherSymbol(icon, null, Modifier.size(22.dp), tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ControlShortcutEditor(appearance: Appearance, onChange: (Appearance) -> Unit) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        GlassPanel(Modifier.fillMaxWidth(), radius = 26f, tint = .28f) {
            Column(Modifier.padding(vertical = 8.dp)) {
                val all = appearance.controlShortcuts + CONTROL_SHORTCUTS.filter { it !in appearance.controlShortcuts }
                all.forEach { id ->
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
        TextButton(onClick = { onChange(appearance.copy(controlShortcuts = DEFAULT_CONTROL_SHORTCUTS)) }) {
            Text("Restore Default Controls", color = Color.White)
        }
    }
}
