package dev.vitreo.launcher.ui

import android.app.AlarmManager
import android.content.*
import android.os.BatteryManager
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.LockScreenGesture
import dev.vitreo.launcher.data.ControlCenterController
import dev.vitreo.launcher.data.SystemBridge
import dev.vitreo.launcher.graphics.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/** Also used by the editor: one layout, the same type sizing, materials and privacy in both modes. */
@Composable
fun LockScreenSurface(home: Appearance, preview: Boolean, busy: Boolean, message: String?,
    onUnlock: () -> Unit, onSystemLock: () -> Unit, onCamera: () -> Unit, onEmergency: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var interactive by remember { mutableStateOf(context.getSystemService(PowerManager::class.java).isInteractive) }
    var saving by remember { mutableStateOf(context.getSystemService(PowerManager::class.java).isPowerSaveMode) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var battery by remember { mutableIntStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                interactive = c.getSystemService(PowerManager::class.java).isInteractive
                saving = c.getSystemService(PowerManager::class.java).isPowerSaveMode
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    battery = (intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale).coerceIn(0, 100)
                    charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    val options = home.lockScreen.sanitized()
    val appearance = options.appearance(home).copy(reducedEffects = home.reducedEffects || saving || !interactive || !resumed)
    val cyber = appearance.themePreset == 1
    val active = resumed && interactive
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) { now = System.currentTimeMillis(); delay(60_000L - now % 60_000L) }
    }
    val frame by produceState<WallpaperFrame?>(null, appearance.usePhoto, appearance.palette, appearance.photoTransform) {
        value = withContext(Dispatchers.IO) { WallpaperStore(context).load(appearance.usePhoto, appearance.palette, appearance.photoTransform) }
    }
    val haptic = LocalHapticFeedback.current
    val metrics = LocalDensity.current
    var viewport by remember { mutableStateOf(Viewport()) }
    val ambient = cyberAmbientPhase(appearance, active)
    val background by produceState<android.graphics.Bitmap?>(null, frame, appearance.wallpaperDim, cyber, options.immersive, viewport.width, viewport.height) {
        val current = frame
        if (current != null && viewport.width > 1 && viewport.height > 1)
            value = withContext(Dispatchers.Default) { HomeBackground.render(current, appearance, viewport.width.toInt(), viewport.height.toInt(), metrics.density) }
    }
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(interactive, resumed, background != null, home.reducedEffects, saving) {
        if (!interactive || background == null) arrival.snapTo(0f)
        else if (!resumed || appearance.reducedEffects) arrival.snapTo(1f)
        else arrival.animateTo(1f, spring(dampingRatio = 1f, stiffness = 300f))
    }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val onCurrentUnlock by rememberUpdatedState(onUnlock)
    val controller = remember(context) { ControlCenterController(context) }
    var cameraId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active, preview) {
        if (active && !preview) {
            cameraId = withContext(Dispatchers.IO) { controller.discoverTorch() }
            controller.start(cameraId)
        } else controller.stop()
    }
    DisposableEffect(controller) { onDispose { controller.stop() } }
    val device = controller.state
    val bridge by SystemBridge.state.collectAsState()
    val alarm = remember(now / 60_000) { context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime }
    var localMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { localMessage = message }
    VitreoTheme(style = 2, preset = appearance.themePreset) {
        CompositionLocalProvider(LocalGlass provides GlassEnvironment(frame, viewport, appearance), LocalCyberPhase provides ambient) {
            Box(Modifier.fillMaxSize().background(Color(0xff101b2b)).onSizeChanged {
                viewport = Viewport(it.width.toFloat(), it.height.toFloat())
            }.testTag("lock_screen")) {
                background?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1f + drag.value / 4000f + (1f - arrival.value) * .025f; scaleY = scaleX
                    alpha = arrival.value
                }.testTag("lock_wallpaper"), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .40f), Color.Transparent, Color.Black.copy(alpha = .42f)))))
                if (cyber && options.immersive && active && !appearance.reducedEffects) CyberAmbient()
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)), horizontalAlignment = Alignment.CenterHorizontally) {
                    LockScreenStatusBar(preview, onSystemLock, Modifier.graphicsLayer { alpha = arrival.value })
                    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp).graphicsLayer {
                        translationY = (-drag.value * .65f + (1f - arrival.value) * 16f) * metrics.density
                        alpha = arrival.value * (1f - drag.value / 360f).coerceIn(.2f, 1f)
                    }.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(22.dp))
                        if (options.showDate) Text(SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(Date(now)),
                            color = Color(options.clockColor), fontSize = 19.sp, fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        LockClock(now, appearance, Modifier.fillMaxWidth().padding(vertical = 2.dp))
                        if (options.showBattery || options.showAlarm) {
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                                if (options.showBattery) LockAccessory(if (charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
                                    "$battery%", "${if (charging) "Charging, " else ""}$battery percent battery", Modifier.weight(1f), battery, charging)
                                if (options.showAlarm) LockAccessory(Icons.Outlined.Alarm,
                                    alarm?.let { DateFormat.getTimeFormat(context).format(Date(it)) } ?: "No alarm",
                                    alarm?.let { "Next alarm ${DateFormat.getTimeFormat(context).format(Date(it))}" } ?: "No alarm set", Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        if (options.notificationCount && bridge.access && bridge.connected && home.notificationCenter) {
                            GlassPanel(Modifier.fillMaxWidth().testTag("lock_notifications"), radius = 24f, tint = .22f) {
                                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LauncherSymbol(Icons.Outlined.NotificationsNone, null, tint = Color.White)
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(if (bridge.notices.none { it.visibility != android.app.Notification.VISIBILITY_SECRET }) "No notifications" else "Notifications available", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("Unlock to view", color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        if (cyber) Text("IDENTITY VERIFICATION // ANDROID", color = CyberColors.Cyan.copy(alpha = .7f),
                            fontFamily = CyberFont, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp))
                    }
                    val notice = localMessage
                    if (notice != null) Text(notice, color = Color.White, fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp).testTag("lock_message"))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        LockShortcut(if (device.torchOn) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            if (device.torchOn) "Turn flashlight off" else "Turn flashlight on", "lock_flashlight") {
                            if (preview) localMessage = "Flashlight is available on the real lock screen."
                            else if (!controller.toggleTorch()) localMessage = "Flashlight is unavailable."
                        }
                        TextButton(onClick = onEmergency, modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) { Text(if (preview) "Emergency preview" else "System / Emergency", color = Color.White.copy(alpha = .85f), fontSize = 13.sp) }
                        LockShortcut(Icons.Outlined.PhotoCamera, "Secure camera", "lock_camera", onCamera)
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 12.dp).pointerInput(busy, appearance.reducedEffects) {
                        val velocity = VelocityTracker()
                        var distance = 0f
                        detectVerticalDragGestures(onDragStart = {
                            distance = 0f; velocity.resetTracking(); scope.launch { drag.stop() }
                        }, onVerticalDrag = { change, delta ->
                            if (!busy) {
                                change.consume(); distance = (distance - delta / metrics.density).coerceIn(0f, 300f)
                                velocity.addPosition(change.uptimeMillis, change.position)
                                scope.launch { drag.snapTo(distance) }
                            }
                        }, onDragCancel = { scope.launch { drag.animateTo(0f, if (appearance.reducedEffects) tween(0) else spring(dampingRatio = 1f, stiffness = 500f)) } },
                            onDragEnd = {
                                val commit = !busy && LockScreenGesture.commits(distance, -velocity.calculateVelocity().y / metrics.density)
                                if (commit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onCurrentUnlock() }
                                scope.launch { drag.animateTo(0f, if (appearance.reducedEffects) tween(0) else spring(dampingRatio = 1f, stiffness = 500f)) }
                            })
                    }.testTag("lock_swipe"), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = onUnlock, enabled = !busy, modifier = Modifier.testTag("lock_unlock")) {
                            Text(if (busy) "Continue on Android…" else if (cyber) "SWIPE UP // AUTHENTICATE" else "Swipe up to unlock", color = Color.White, fontSize = 14.sp)
                        }
                        Box(Modifier.padding(bottom = 9.dp).size(126.dp, 5.dp).clip(CircleShape).background(Color.White.copy(alpha = .85f)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun LockClock(now: Long, a: Appearance, modifier: Modifier) {
    val options = a.lockScreen
    val cyber = a.themePreset == 1
    val time = DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hm")
        .let { SimpleDateFormat(it.replace("a", "").trim(), Locale.getDefault()).format(Date(now)) }
    val font = remember(cyber, options.clockStyle) {
        if (cyber && options.clockStyle != 1) CyberFont
        else if (!cyber && options.clockStyle == 0) FontFamily(androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.NORMAL)))
        else FontFamily.SansSerif
    }
    val clockColor by animateColorAsState(Color(options.clockColor),
        if (LocalGlass.current.appearance.reducedEffects) tween(0) else tween(180), label = "Lock clock color")
    val weight = when (options.clockStyle) { 1 -> FontWeight.Light; 2 -> FontWeight.Bold; else -> FontWeight.SemiBold }
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val available = (with(density) { maxWidth.toPx() } - 4f).coerceAtLeast(1f)
        fun typography(size: Float) = TextStyle(fontFamily = font, fontWeight = weight, fontSize = size.sp,
            lineHeight = (size * 1.2f).sp, letterSpacing = (-size * .025f).sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        // Measure the actual rendered style, including Android's nonlinear accessibility scaling.
        val size = remember(time, options.clockSize, font, weight, available, measurer) {
            fun fits(size: Float) = measurer.measure(time, typography(size), maxLines = 1, softWrap = false).size.width <= available
            if (fits(options.clockSize)) options.clockSize else {
                var low = 1f; var high = options.clockSize
                repeat(9) { val mid = (low + high) / 2; if (fits(mid)) low = mid else high = mid }
                low
            }
        }
        val shadow = Shadow(Color.Black.copy(alpha = .3f), Offset(0f, 3f), 18f)
        val style = if (options.glassClock && !cyber && !a.opaquePanels)
            typography(size).copy(brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .96f), clockColor.copy(alpha = .92f), Color.White.copy(alpha = .94f))), shadow = shadow)
            else typography(size).copy(color = clockColor, shadow = shadow)
        BasicText(time, style = style, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().testTag("lock_clock"))
    }
}

@Composable
private fun LockAccessory(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier,
    batteryLevel: Int? = null, charging: Boolean = false) {
    GlassPanel(modifier.heightIn(min = 64.dp).clearAndSetSemantics { contentDescription = label }, radius = 22f, tint = .15f) {
        Row(Modifier.align(Alignment.Center).padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (batteryLevel != null) StatusBattery(batteryLevel, charging, percentage = false, tint = Color.White)
            else ControlSymbol(icon, null, Modifier.size(23.dp), tint = Color.White)
            Text(value, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LockShortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    GlassPanel(Modifier.size(54.dp), radius = 27f, interactive = true) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize().testTag(tag)) {
            ControlSymbol(icon, label, Modifier.size(25.dp), tint = Color.White)
        }
    }
}
