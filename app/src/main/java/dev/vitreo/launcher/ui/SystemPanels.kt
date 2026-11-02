package dev.vitreo.launcher.ui

import android.app.KeyguardManager
import androidx.compose.ui.graphics.asImageBitmap
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.data.ControlCenterController
import dev.vitreo.launcher.data.NowPlaying
import dev.vitreo.launcher.data.SystemBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class StatusSnapshot(val battery: Int = -1, val charging: Boolean = false, val saving: Boolean = false,
    val wifi: Boolean = false, val cellular: Boolean = false, val connected: Boolean = false)

@Composable
private fun rememberStatusSnapshot(): StatusSnapshot {
    val context = LocalContext.current
    var state by remember(context) {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        mutableStateOf(StatusSnapshot(battery = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            saving = context.getSystemService(PowerManager::class.java).isPowerSaveMode))
    }
    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        fun network() {
            val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            state = state.copy(wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
                connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                state = state.copy(saving = context.getSystemService(PowerManager::class.java).isPowerSaveMode)
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                state = state.copy(battery = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                    charging = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) { android.os.Handler(context.mainLooper).post { network() } }
            override fun onLost(n: Network) { android.os.Handler(context.mainLooper).post { network() } }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val registered = runCatching { connectivity.registerDefaultNetworkCallback(callback); network(); true }.getOrDefault(false)
        onDispose { context.unregisterReceiver(receiver); if (registered) connectivity.unregisterNetworkCallback(callback) }
    }
    return state
}

@Composable
internal fun HomeStatusBar(now: Long, modifier: Modifier = Modifier, onNotices: (() -> Unit)?, onControls: (() -> Unit)?, locked: Boolean = false) {
    val context = LocalContext.current
    val state = rememberStatusSnapshot()
    val ink = LocalGlassContentColor.current
    Row(modifier.fillMaxWidth().cyberStatusChrome(state.saving)
        .testTag("home_status_bar").padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxHeight().then(if (onNotices != null) Modifier.clickable(onClick = onNotices) else Modifier), contentAlignment = Alignment.CenterStart) {
            if (locked) StatusLock(Modifier.size(17.dp), "Device locked", ink)
            else Text(SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)), color = ink, fontSize = if (LocalCyber.current) 14.sp else 15.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = if (LocalCyber.current) CyberFont else androidx.compose.ui.text.font.FontFamily.SansSerif)
        }
        Row(Modifier.fillMaxHeight().then(if (onControls != null) Modifier.clickable(onClick = onControls) else Modifier)
            .semantics { contentDescription = "${if (state.wifi) "Wi-Fi" else if (state.cellular) "Mobile network" else "No active network"}, ${if (state.connected) "connected" else "offline"}. Battery ${state.battery}%${if (state.charging) ", charging" else ""}" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            // Connectivity, not an invented signal-strength reading: Android does not expose
            // cellular bars without telephony permissions. M identifies a mobile transport.
            NetworkIndicator(state, ink)
            if (state.battery >= 0) StatusBattery(state.battery, state.charging, Modifier.testTag("home_battery"), tint = ink, powerSave = state.saving)
        }
    }
}

@Composable
private fun NetworkIndicator(state: StatusSnapshot, ink: Color) {
    if (state.wifi) StatusWifi(Modifier.size(19.dp), tint = ink)
    else Text(if (state.cellular) "M" else if (LocalCyber.current) "OFF" else "Offline", color = ink.copy(alpha = .8f), fontSize = 10.sp,
        fontFamily = if (LocalCyber.current) CyberFont else androidx.compose.ui.text.font.FontFamily.SansSerif)
}

@Composable
internal fun SystemBatteryIndicator(tint: Color = LocalGlassContentColor.current) {
    val state = rememberStatusSnapshot()
    if (state.battery >= 0) StatusBattery(state.battery, state.charging, tint = tint, powerSave = state.saving)
}

@Composable
internal fun LockScreenStatusBar(preview: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberStatusSnapshot()
    val band = statusBandHeight(LocalGlass.current.appearance)
    val palette = LocalCyberPalette.current
    val ink = if (LocalCyber.current) palette.text else Color.White
    Column(modifier.fillMaxWidth().testTag("lock_header")) {
        Row(Modifier.fillMaxWidth().height(band).cyberStatusChrome(state.saving).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (preview) TextButton(onClick = onClose, modifier = Modifier.testTag("lock_close").semantics { contentDescription = "Close preview" }, contentPadding = PaddingValues(0.dp)) {
                ControlSymbol(Icons.Outlined.Close, null, Modifier.size(14.dp), ink)
                Text("Preview", color = ink, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            } else Text(if (state.wifi) "Wi-Fi" else if (state.cellular) "Mobile" else "Offline", color = ink, fontSize = 12.sp,
                fontFamily = if (LocalCyber.current) CyberFont else androidx.compose.ui.text.font.FontFamily.SansSerif)
            Spacer(Modifier.weight(1f))
            Row(Modifier.testTag("lock_status_icons").semantics { contentDescription = "${if (state.connected) "Connected" else "Offline"}. Battery ${state.battery} percent" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NetworkIndicator(state, ink)
                if (state.battery >= 0) StatusBattery(state.battery, state.charging, tint = ink, powerSave = state.saving)
            }
        }
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            if (LocalCyber.current) Box(Modifier.size(32.dp, 26.dp).background(palette.background.copy(alpha = .6f),
                androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
            if (preview) StatusLock(Modifier.size(16.dp, 21.dp).testTag("lock_glyph"), tint = ink)
            else IconButton(onClick = onClose, modifier = Modifier.size(44.dp, 32.dp).testTag("lock_close")) {
                StatusLock(Modifier.size(16.dp, 21.dp).testTag("lock_glyph"), "Use system lock screen", ink)
            }
        }
    }
}

@Composable
internal fun PlayingWaveform(modifier: Modifier = Modifier, color: Color = Color.White) {
    val infinite = rememberInfiniteTransition(label = "waveform")
    val b1 by infinite.animateFloat(0.35f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b1")
    val b2 by infinite.animateFloat(0.85f, 0.25f, infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse), label = "b2")
    val b3 by infinite.animateFloat(0.45f, 0.95f, infiniteRepeatable(tween(510, easing = LinearEasing), RepeatMode.Reverse), label = "b3")
    Row(modifier.size(16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.width(3.dp).fillMaxHeight(b1).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.width(3.dp).fillMaxHeight(b2).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.width(3.dp).fillMaxHeight(b3).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationCenter(onClose: () -> Unit, onAccess: () -> Unit, onError: (String) -> Unit,
    showStatusBar: Boolean = false, onCamera: (() -> Unit)? = null) {
    val context = LocalContext.current
    val bridge by SystemBridge.state.collectAsStateWithLifecycle()
    val locked = context.getSystemService(KeyguardManager::class.java).isDeviceLocked
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    val now = LocalHomeClock.current
    val appearance = LocalGlass.current.appearance
    val reduced = appearance.reducedEffects
    val controller = remember { ControlCenterController(context) }
    var torchCamera by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controller) {
        torchCamera = withContext(Dispatchers.IO) { controller.discoverTorch() }
        controller.start(torchCamera)
    }
    DisposableEffect(controller) { onDispose { controller.stop() } }

    val datePattern = remember {
        try { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE, MMMM d") }
        catch (_: Exception) { "EEEE, MMMM d" }
    }
    val dateText = remember(now, datePattern) {
        try {
            SimpleDateFormat(datePattern, Locale.getDefault()).format(Date(now))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } catch (_: Exception) {
            SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(Date(now))
        }
    }
    val timeText = remember(now) {
        SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(if (showStatusBar) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        else WindowInsets.safeDrawing).testTag("notification_center")) {
        if (showStatusBar) CompositionLocalProvider(LocalGlassContentColor provides Color.White) {
            HomeStatusBar(now, Modifier.height(statusBandHeight(LocalGlass.current.appearance)), null, null)
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notification Center", Modifier.weight(1f), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onClose) { ControlSymbol(Icons.Outlined.Close, "Close", tint = Color.White) }
            }
            Text(dateText, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .4f), Offset(0f, 2f), 10f)),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
            Text(timeText, color = Color.White,
                fontSize = if (LocalCyber.current) 68.sp else 76.sp, letterSpacing = (-2).sp,
                fontWeight = if (LocalCyber.current) FontWeight.Light else FontWeight.SemiBold,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .45f), Offset(0f, 3f), 14f)),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp))

            if (bridge.media.available) {
                val media = bridge.media
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("notification_media_player"),
                    radius = if (appearance.iosClassic) 20f else 24f,
                    tint = if (appearance.iosClassic) .34f else .24f, material = GlassMaterial.Regular) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .1f))
                                .clickable { if (!SystemBridge.openPlayer()) runCatching { context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)) } },
                                contentAlignment = Alignment.Center) {
                                if (media.artwork != null) Image(media.artwork.asImageBitmap(), "Album artwork", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else ControlSymbol(Icons.Outlined.MusicNote, "Music", Modifier.size(24.dp), tint = Color.White.copy(alpha = .8f))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(media.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (media.artist.isNotBlank()) Text(media.artist, color = Color.White.copy(alpha = .7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (media.playing) PlayingWaveform(Modifier.padding(end = 4.dp), color = Color.White.copy(alpha = .9f))
                            IconButton(onClick = { if (!SystemBridge.openPlayer()) runCatching { context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)) } }, modifier = Modifier.size(32.dp)) {
                                ControlSymbol(Icons.Outlined.Podcasts, "AirPlay", Modifier.size(20.dp), tint = Color.White.copy(alpha = .75f))
                            }
                        }
                        if (media.duration > 0) {
                            val elapsed = if (media.playing && media.updatedAt > 0) (android.os.SystemClock.elapsedRealtime() - media.updatedAt).coerceAtLeast(0) * media.speed else 0f
                            val position = (media.position + elapsed.toLong()).coerceIn(0, media.duration)
                            Column(Modifier.padding(top = 6.dp)) {
                                IosSlider(position.toFloat() / media.duration, { SystemBridge.seek((it * media.duration).toLong()) },
                                    enabled = media.supports(PlaybackState.ACTION_SEEK_TO), onDark = true)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)) {
                                    Text("${position / 60000}:${(position / 1000 % 60).toString().padStart(2, '0')}", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("-${(media.duration - position) / 60000}:${((media.duration - position) / 1000 % 60).toString().padStart(2, '0')}", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { SystemBridge.command(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                                enabled = media.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS), modifier = Modifier.size(36.dp)) {
                                ControlSymbol(Icons.Outlined.SkipPrevious, "Previous track", Modifier.size(24.dp), tint = Color.White.copy(alpha = if (media.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) 1f else .3f))
                            }
                            IconButton(onClick = { SystemBridge.command(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                                modifier = Modifier.size(44.dp)) {
                                Box(Modifier.size(38.dp).background(Color.White.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                                    ControlSymbol(if (media.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play/Pause", Modifier.size(24.dp), tint = Color.White)
                                }
                            }
                            IconButton(onClick = { SystemBridge.command(KeyEvent.KEYCODE_MEDIA_NEXT) },
                                enabled = media.supports(PlaybackState.ACTION_SKIP_TO_NEXT), modifier = Modifier.size(36.dp)) {
                                ControlSymbol(Icons.Outlined.SkipNext, "Next track", Modifier.size(24.dp), tint = Color.White.copy(alpha = if (media.supports(PlaybackState.ACTION_SKIP_TO_NEXT)) 1f else .3f))
                            }
                        }
                    }
                }
            }

            if (!bridge.access) {
                GlassPanel(Modifier.fillMaxWidth(), material = GlassMaterial.Regular) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Your notifications, on Home", fontWeight = FontWeight.SemiBold)
                        Text("Allow notification access in Android Settings to see and dismiss alerts here. Content stays on this device and is never saved by Vitreo.", fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                        TextButton(onClick = onAccess, modifier = Modifier.testTag("notification_access")) { Text("Open Android Settings") }
                    }
                }
            } else if (locked) {
                Text("Unlock your device to view notifications", color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (bridge.connected) "Recent" else "Connecting to Android…", Modifier.weight(1f), color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                    if (bridge.notices.any { it.clearable }) TextButton(onClick = SystemBridge::clear, modifier = Modifier.testTag("clear_notifications")) { Text("Clear", color = Color.White) }
                }
                if (bridge.connected && bridge.notices.isEmpty() && !bridge.media.available) {
                    Text("No notifications", color = Color.White.copy(alpha = .7f), modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                }
                val grouped = remember(bridge.notices) { bridge.notices.groupBy { it.packageName } }
                LazyColumn(Modifier.weight(1f).testTag("notification_list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    grouped.forEach { (pkg, groupNotices) ->
                        val isExpandedGroup = pkg in expandedGroups || groupNotices.size == 1
                        val displayedNotices = if (isExpandedGroup) groupNotices else listOf(groupNotices.first())
                        items(displayedNotices, key = { it.key }) { notice ->
                            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart && notice.clearable) SystemBridge.dismiss(notice.key)
                                false // Android's removal callback is the source of truth.
                            })
                            SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = false, enableDismissFromEndToStart = notice.clearable,
                                backgroundContent = { Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color(0xffff3b30)).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ControlSymbol(Icons.Outlined.Delete, "Clear", Modifier.size(16.dp), tint = Color.White)
                                        Text("Clear", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                } }) {
                                GlassPanel(Modifier.fillMaxWidth().testTag("notice:${notice.key}")
                                    .animateContentSize(if (reduced) tween(0) else LauncherMotion.panel()),
                                    radius = if (appearance.iosClassic) 20f else 24f,
                                    tint = if (appearance.iosClassic) .36f else .28f, material = GlassMaterial.Regular) {
                                    Column(Modifier.fillMaxWidth().clickable {
                                        val intent = notice.intent
                                        if (intent != null) { if (dev.vitreo.launcher.data.launchNotification(context, intent)) onClose() else onError("This notification is no longer available.") }
                                        else expanded = if (notice.key in expanded) expanded - notice.key else expanded + notice.key
                                    }.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(30.dp).clip(if (LocalCyber.current) CyberShape else RoundedCornerShape(7.dp))
                                                .background(LocalGlassContentColor.current.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                                                if (notice.appIcon != null) Image(notice.appIcon.asImageBitmap(), "${notice.app} app icon", Modifier.fillMaxSize())
                                                else ControlSymbol(Icons.Outlined.Notifications, "${notice.app} notification", Modifier.size(18.dp))
                                            }
                                            Text(notice.app, Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val timeSpan = remember(notice.time) {
                                                if (notice.time > 0) android.text.format.DateUtils.getRelativeTimeSpanString(notice.time, System.currentTimeMillis(), 60_000L).toString()
                                                else DateFormat.getTimeFormat(context).format(Date(notice.time))
                                            }
                                            Text(timeSpan, fontSize = 11.sp, color = LocalGlassContentColor.current.copy(alpha = .65f))
                                            if (notice.clearable) IconButton(onClick = { SystemBridge.dismiss(notice.key) }, modifier = Modifier.size(28.dp)) {
                                                ControlSymbol(Icons.Outlined.Close, "Clear ${notice.app} notification", Modifier.size(13.dp))
                                            }
                                        }
                                        if (notice.title.isNotEmpty()) Text(notice.title, Modifier.padding(top = 6.dp), fontSize = 15.sp,
                                            lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (notice.text.isNotEmpty()) Text(notice.text, fontSize = 14.sp, lineHeight = 19.sp,
                                            color = LocalGlassContentColor.current.copy(alpha = .85f), maxLines = if (notice.key in expanded) 12 else 2, overflow = TextOverflow.Ellipsis)
                                        if (notice.actions.isNotEmpty() || notice.key in expanded) {
                                            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = { expanded = if (notice.key in expanded) expanded - notice.key else expanded + notice.key },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(30.dp)) {
                                                    Text(if (notice.key in expanded) "Less" else "More", fontSize = 12.sp, color = Color.White.copy(alpha = .8f))
                                                }
                                                notice.actions.forEach { action ->
                                                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .14f))
                                                        .clickable { if (!dev.vitreo.launcher.data.launchNotification(context, action.intent)) onError("This action is no longer available.") }
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)) {
                                                        Text(action.title, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (groupNotices.size > 1) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                                    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .12f))
                                        .clickable { expandedGroups = if (pkg in expandedGroups) expandedGroups - pkg else expandedGroups + pkg }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(if (pkg in expandedGroups) "Collapse ${groupNotices.first().app} stack" else "${groupNotices.size} notifications from ${groupNotices.first().app}",
                                            fontSize = 12.sp, color = Color.White.copy(alpha = .85f), fontWeight = FontWeight.Medium)
                                        ControlSymbol(if (pkg in expandedGroups) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            "Toggle group", Modifier.size(16.dp), tint = Color.White.copy(alpha = .85f))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val torchOn = controller.state.torchOn
                Box(Modifier.size(50.dp).clip(CircleShape)
                    .background(if (torchOn) Color.White else Color.White.copy(alpha = .18f))
                    .clickable(role = Role.Button, onClickLabel = "Toggle flashlight") {
                        if (!controller.toggleTorch()) onError("Flashlight is unavailable.")
                    }, contentAlignment = Alignment.Center) {
                    ControlSymbol(if (torchOn) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                        "Flashlight", Modifier.size(24.dp), tint = if (torchOn) Color(0xff151518) else Color.White)
                }
                Box(Modifier.height(48.dp).weight(1f).testTag("notifications_dismiss_handle")
                    .clickable(role = Role.Button, onClickLabel = "Dismiss notification center", onClick = onClose),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(70.dp, 5.dp).background(Color.White.copy(alpha = .75f), CircleShape))
                }
                Box(Modifier.size(50.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = .18f))
                    .clickable(role = Role.Button, onClickLabel = "Camera") {
                        if (onCamera != null) onCamera()
                        else runCatching {
                            context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            onClose()
                        }
                    }, contentAlignment = Alignment.Center) {
                    ControlSymbol(Icons.Outlined.PhotoCamera, "Camera", Modifier.size(24.dp), tint = Color.White)
                }
            }
        }
    }
}
