package dev.vitreo.launcher.ui

import android.app.KeyguardManager
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.data.SystemBridge
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
    val band = statusBandHeight()
    val ink = if (LocalCyber.current) CyberColors.Text else Color.White
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
            if (LocalCyber.current) Box(Modifier.size(32.dp, 26.dp).background(CyberColors.Background.copy(alpha = .6f),
                androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
            if (preview) StatusLock(Modifier.size(16.dp, 21.dp).testTag("lock_glyph"), tint = ink)
            else IconButton(onClick = onClose, modifier = Modifier.size(44.dp, 32.dp).testTag("lock_close")) {
                StatusLock(Modifier.size(16.dp, 21.dp).testTag("lock_glyph"), "Use system lock screen", ink)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationCenter(onClose: () -> Unit, onAccess: () -> Unit, onError: (String) -> Unit, showStatusBar: Boolean = false) {
    val context = LocalContext.current
    val bridge by SystemBridge.state.collectAsStateWithLifecycle()
    val locked = context.getSystemService(KeyguardManager::class.java).isDeviceLocked
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val now = LocalHomeClock.current
    Column(Modifier.fillMaxSize().windowInsetsPadding(if (showStatusBar) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        else WindowInsets.safeDrawing).testTag("notification_center")) {
        if (showStatusBar) CompositionLocalProvider(LocalGlassContentColor provides Color.White) {
            HomeStatusBar(now, Modifier.height(statusBandHeight()), null, null)
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notification Center", Modifier.weight(1f), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onClose) { ControlSymbol(Icons.Outlined.Close, "Close", tint = Color.White) }
            }
            Text(SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(Date(now)), color = Color.White,
                fontSize = 19.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp))
            Text(SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 68.sp,
                fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (bridge.connected) "Recent" else "Connecting to Android…", Modifier.weight(1f), color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                    if (bridge.notices.any { it.clearable }) TextButton(onClick = SystemBridge::clear, modifier = Modifier.testTag("clear_notifications")) { Text("Clear", color = Color.White) }
                }
                if (bridge.connected && bridge.notices.isEmpty()) Text("No notifications", color = Color.White.copy(alpha = .7f), modifier = Modifier.align(Alignment.CenterHorizontally).padding(30.dp))
                LazyColumn(Modifier.weight(1f).testTag("notification_list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(bridge.notices, key = { it.key }) { notice ->
                        val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart && notice.clearable) SystemBridge.dismiss(notice.key)
                            false // Android's removal callback is the source of truth.
                        })
                        SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = false, enableDismissFromEndToStart = notice.clearable,
                            backgroundContent = { Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.CenterEnd) { Text("Clear", color = Color.White) } }) {
                            GlassPanel(Modifier.fillMaxWidth().testTag("notice:${notice.key}"), radius = 26f, tint = .32f, material = GlassMaterial.Regular) {
                                Column(Modifier.fillMaxWidth().clickable {
                                    val intent = notice.intent
                                    if (intent != null) runCatching { intent.send(); onClose() }.onFailure { onError("This notification is no longer available.") }
                                    else expanded = if (notice.key in expanded) expanded - notice.key else expanded + notice.key
                                }.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val appIcon by produceState<android.graphics.Bitmap?>(null, notice.packageName) {
                                            value = withContext(Dispatchers.IO) { runCatching {
                                                context.packageManager.getApplicationIcon(notice.packageName).toBitmap(64, 64)
                                            }.getOrNull() }
                                        }
                                        appIcon?.let { Image(it.asImageBitmap(), null, Modifier.padding(end = 8.dp).size(26.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))) }
                                        Text(notice.app, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(DateFormat.getTimeFormat(context).format(Date(notice.time)), fontSize = 11.sp)
                                        if (notice.clearable) IconButton(onClick = { SystemBridge.dismiss(notice.key) }, modifier = Modifier.size(32.dp)) {
                                            ControlSymbol(Icons.Outlined.Close, "Clear ${notice.app} notification", Modifier.size(14.dp))
                                        }
                                    }
                                    if (notice.title.isNotEmpty()) Text(notice.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (notice.text.isNotEmpty()) Text(notice.text, fontSize = 14.sp, maxLines = if (notice.key in expanded) 12 else 2, overflow = TextOverflow.Ellipsis)
                                    Row {
                                        TextButton(onClick = { expanded = if (notice.key in expanded) expanded - notice.key else expanded + notice.key }) { Text(if (notice.key in expanded) "Less" else "More", fontSize = 12.sp) }
                                        if (notice.key in expanded) notice.actions.forEach { action ->
                                            TextButton(onClick = { runCatching { action.intent.send() }.onFailure { onError("This action is no longer available.") } }) { Text(action.title, fontSize = 12.sp, maxLines = 1) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
            Box(Modifier.fillMaxWidth().height(48.dp).testTag("notifications_dismiss_handle"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(70.dp, 5.dp).background(Color.White.copy(alpha = .75f), CircleShape))
            }
        }
    }
}
