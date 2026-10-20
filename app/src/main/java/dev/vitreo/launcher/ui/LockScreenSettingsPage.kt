package dev.vitreo.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.LockScreenSettings
import dev.vitreo.launcher.core.WALLPAPER_NAMES
import dev.vitreo.launcher.graphics.HomeBackground
import dev.vitreo.launcher.graphics.WallpaperFrame
import dev.vitreo.launcher.graphics.WallpaperStore
import dev.vitreo.launcher.lock.LockScreenActivity
import dev.vitreo.launcher.lock.LockScreenRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun LockScreenSettingsPage(a: Appearance, onAppearance: (Appearance) -> Unit, onSetHome: () -> Unit) {
    val context = LocalContext.current
    val enabled by LockScreenRuntime.enabled.collectAsState()
    val options = a.lockScreen
    var error by remember { mutableStateOf<String?>(null) }
    fun change(value: LockScreenSettings) { onAppearance(a.copy(lockScreen = value.sanitized())) }
    LockScreenPreviewCard(a) { LockScreenActivity.preview(context, a) }
    IosSectionLabel("On this device")
    IosGroup {
        IosToggleRow("Custom lock screen", enabled, modifier = Modifier.testTag("lock_enable")) {
            LockScreenRuntime.configure(a)
            error = LockScreenRuntime.setEnabled(context, it)
        }
        IosDivider()
        IosRow("Full-screen preview", onClick = { LockScreenActivity.preview(context, a) })
    }
    LockFootnote(error ?: "Optional and off by default. Android / GrapheneOS keeps your PIN, password, fingerprint and security features. Vitreo never reads or stores your credentials.")
    if (error != null) IosGroup {
        IosRow("Choose default launcher", onClick = onSetHome); IosDivider()
        IosRow("Android security settings", onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } })
    }
    IosSectionLabel("Style")
    IosGroup {
        IosSegmented(listOf("Follow Home", "iOS", "Cyberpunk"), options.theme, Modifier.padding(12.dp)) { change(options.copy(theme = it)) }
        if (options.appearance(a).themePreset == 1) {
            IosDivider()
            IosToggleRow("Immersive HUD", options.immersive) { change(options.copy(immersive = it)) }
        }
    }
    LockFootnote("Cyberpunk Classic stays still. Immersive adds native rain, scanning light and HUD motion. Battery Saver and Reduce Motion pause these effects; the screen is never kept awake.")
    IosSectionLabel("Clock")
    IosGroup {
        IosSegmented(if (options.appearance(a).themePreset == 1) listOf("HUD", "Minimal", "Bold") else listOf("Rounded", "Light", "Bold"), options.clockStyle, Modifier.padding(12.dp)) { change(options.copy(clockStyle = it)) }
        LockSlider("Size", options.clockSize, "${options.clockSize.roundToInt()} pt", 72f..132f) { change(options.copy(clockSize = it)) }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Pearl" to 0xfff5f8ff, "Ice" to 0xffb9eaff, "Blush" to 0xffffcee3, "Gold" to 0xffffe0a6, "Neon" to 0xff63f5f7, "Graphite" to 0xff101b2b).forEach { (name, color) ->
                val selected = options.clockColor == color.toInt()
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(color)).semantics {
                    contentDescription = "$name clock"; this.selected = selected
                }.clickable { change(options.copy(clockColor = color.toInt())) }, contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Outlined.Check, null, tint = Color(0xff16202a))
                }
            }
        }
        if (options.appearance(a).themePreset != 1) IosToggleRow("Glass clock", options.glassClock) { change(options.copy(glassClock = it)) }
        IosDivider()
        IosToggleRow("Show date", options.showDate) { change(options.copy(showDate = it)) }
    }
    LockFootnote("The clock follows Android's 12/24-hour setting and fits the available width, including larger accessibility text.")
    IosSectionLabel("Wallpaper")
    IosGroup {
        IosRow("Use Home wallpaper", if (options.wallpaper == -1) "Selected" else "Choose") { change(options.copy(wallpaper = -1)) }
        IosDivider()
        Row(Modifier.fillMaxWidth().testTag("lock_wallpaper_choices").horizontalScroll(rememberScrollState()).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WALLPAPER_NAMES.forEachIndexed { i, title ->
                OutlinedButton(onClick = { change(options.copy(wallpaper = i)) }, modifier = Modifier.semantics { selected = options.wallpaper == i }) { Text(title) }
            }
        }
        LockSlider("Dim", options.dim, "${(options.dim * 100).roundToInt()}%", 0f..0.65f) { change(options.copy(dim = it)) }
    }
    LockFootnote("Follow Home includes your photo and its crop. This changes Vitreo's surface only; your system lock wallpaper stays under Android's control.")
    IosSectionLabel("Widgets & privacy")
    IosGroup {
        IosToggleRow("Battery", options.showBattery) { change(options.copy(showBattery = it)) }; IosDivider()
        IosToggleRow("Next alarm", options.showAlarm) { change(options.copy(showAlarm = it)) }; IosDivider()
        IosToggleRow("Notification indicator", options.notificationCount) { change(options.copy(notificationCount = it)) }
    }
    LockFootnote("The indicator uses the notification access already enabled in Status Bar & Notifications. Names, message text, artwork and actions remain hidden until you unlock. Third-party widgets are kept on Home and Today to protect private content.")
    IosSectionLabel("Pixel & GrapheneOS")
    LockFootnote("Use Vitreo as your default Home and keep a secure Android lock enabled. The surface is prepared when you turn the display off after Android locks. Swipe up or tap Unlock to use Android authentication. The lock button, Back and Emergency always give you a route to the system screen.")
    LockFootnote("After a restart, if Android stops Vitreo, during delayed locking, or while another app needs the lock screen, Android's native screen remains available. No Google Play services, overlay, accessibility or device-admin access is needed. Activation stays on this device and is never enabled by importing a backup.")
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LockFootnote(value: String) {
    Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
}

@Composable
private fun LockSlider(title: String, value: Float, detail: String, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 17.dp, vertical = 12.dp)) {
        Row { Text(title, Modifier.weight(1f)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        IosSlider(value, onChange, valueRange = range)
    }
}

@Composable
private fun LockScreenPreviewCard(a: Appearance, onClick: () -> Unit) {
    val context = LocalContext.current
    val options = a.lockScreen
    val appearance = options.appearance(a)
    val frame by produceState<WallpaperFrame?>(null, appearance.palette, appearance.usePhoto, appearance.photoTransform) {
        value = withContext(Dispatchers.IO) { WallpaperStore(context).load(appearance.usePhoto, appearance.palette, appearance.photoTransform) }
    }
    val backdrop by produceState<android.graphics.Bitmap?>(null, frame, appearance.wallpaperDim, appearance.themePreset, options.immersive) {
        val source = frame
        delay(64) // Coalesce wallpaper slider changes before rendering another bitmap.
        if (source != null) value = withContext(Dispatchers.Default) { HomeBackground.render(source, appearance, 600, 470, 1.5f) }
    }
    Box(Modifier.fillMaxWidth().height(235.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xff101b2b))
        .clickable(onClickLabel = "Open lock screen preview", onClick = onClick).testTag("lock_preview"), contentAlignment = Alignment.Center) {
        Crossfade(backdrop, Modifier.fillMaxSize(), tween(if (LocalGlass.current.appearance.reducedEffects) 0 else 180), label = "Lock wallpaper preview") { image ->
            image?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        VitreoTheme(style = 2, preset = appearance.themePreset) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                if (options.showDate) Text(java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.ENGLISH).format(java.util.Date()), color = Color(options.clockColor), fontSize = 14.sp)
                LockClock(System.currentTimeMillis(), appearance.copy(lockScreen = options.copy(clockSize = minOf(82f, options.clockSize))), Modifier.fillMaxWidth())
                Text(if (appearance.themePreset == 1) "SECURE // PREVIEW" else "Tap to preview", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
            }
        }
    }
}
