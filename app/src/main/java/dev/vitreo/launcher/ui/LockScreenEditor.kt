package dev.vitreo.launcher.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.LockScreenSettings
import dev.vitreo.launcher.core.WALLPAPER_NAMES
import dev.vitreo.launcher.data.LockScreenCodec
import dev.vitreo.launcher.graphics.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

internal val LockClockColors = listOf("Pearl" to 0xfff5f8ff, "Ice" to 0xffb9eaff,
    "Blush" to 0xffffcee3, "Gold" to 0xffffe0a6, "Neon" to 0xff63f5f7, "Graphite" to 0xff101b2b)

/** Unlocked Settings editor only. Drafts never activate the surface or touch Android credentials. */
@Composable
internal fun LockScreenEditor(appearance: Appearance, onCancel: () -> Unit, onSave: (LockScreenSettings) -> Unit) {
    var draft by rememberSaveable(stateSaver = Saver<LockScreenSettings, String>(
        save = { LockScreenCodec.encode(it).toString() }, restore = { LockScreenCodec.decode(JSONObject(it)) })) {
        mutableStateOf(appearance.lockScreen)
    }
    var panel by rememberSaveable { mutableIntStateOf(0) }
    var lastPanel by rememberSaveable { mutableIntStateOf(1) }
    val shownPanel = if (panel == 0) lastPanel else panel
    SideEffect { if (panel != 0) lastPanel = panel }
    val cyber = draft.appearance(appearance).themePreset == 1
    val classic = !cyber && appearance.iosClassic
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view, appearance.homeStatusBar) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.attributes = it.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
                WindowCompat.getInsetsController(it, view).apply {
                    hide(WindowInsetsCompat.Type.navigationBars())
                    isAppearanceLightStatusBars = false
                    if (appearance.homeStatusBar) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
                }
            }
            onDispose { }
        }
        VitreoTheme(style = 2, preset = if (cyber) 1 else 0) {
            Box(Modifier.fillMaxSize().testTag("lock_editor")) {
                LockScreenSurface(appearance.copy(lockScreen = draft), preview = true, busy = false, message = null,
                    onUnlock = {}, onSystemLock = onCancel, onCamera = {}, onEmergency = {},
                    onEditClock = { panel = 1 }, onClockHeight = { draft = draft.copy(clockHeight = it) },
                    editorHeader = {
                        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                            Row(Modifier.fillMaxWidth().height(maxOf(44.dp, statusBandHeight(LocalGlass.current.appearance))).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onCancel, modifier = Modifier.testTag("lock_editor_cancel")) { Text("Cancel", color = Color.White) }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { onSave(draft.sanitized()) }, modifier = Modifier.testTag("lock_editor_save")) { Text("Done", color = if (cyber) CyberColors.Cyan else Color.White, fontWeight = FontWeight.Bold) }
                            }
                            Text("Lock Screen", color = Color.White.copy(alpha = .8f), fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally).height(32.dp).padding(top = 7.dp))
                        }
                    })
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(12.dp)) {
                    val duration = if (appearance.reducedEffects) 0 else 180
                    AnimatedVisibility(panel != 0, enter = fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 5 },
                        exit = fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { it / 5 }) {
                        Surface(Modifier.animateContentSize(tween(duration)), shape = RoundedCornerShape(24.dp), color = if (cyber) CyberColors.Panel else Color(0xff1c1c1e), contentColor = Color.White) {
                            Column(Modifier.fillMaxWidth().heightIn(max = 390.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(when (shownPanel) { 2 -> "Wallpaper"; 3 -> "Widgets"; else -> "Font & Color" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { panel = 0 }) { Text("Close") }
                                }
                                when (shownPanel) {
                                    1 -> {
                                        LockClockStylePicker(draft, cyber) { draft = it }
                                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                            LockClockColors.forEach { (name, value) ->
                                                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(value))
                                                    .border(if (draft.clockColor == value.toInt()) 3.dp else 0.dp, Color.White, CircleShape)
                                                    .semantics { contentDescription = "$name clock"; selected = draft.clockColor == value.toInt() }
                                                    .clickable { draft = draft.copy(clockColor = value.toInt()) })
                                            }
                                        }
                                        if (!cyber && !classic) {
                                            IosSegmented(listOf("Glass", "Solid"), if (draft.glassClock) 0 else 1) { draft = draft.copy(glassClock = it == 0) }
                                            Text("Clock height", Modifier.padding(top = 14.dp), fontSize = 13.sp)
                                            IosSlider(draft.clockHeight, { draft = draft.copy(clockHeight = it) }, Modifier.testTag("lock_editor_height"), valueRange = 1f..2.4f)
                                            Text("${(draft.clockHeight * 100).roundToInt()}% · or drag the clock's lower-right handle", fontSize = 12.sp, color = Color.White.copy(alpha = .65f))
                                        }
                                        IosToggleRow("Show date", draft.showDate) { draft = draft.copy(showDate = it) }
                                    }
                                    2 -> {
                                        TextButton(onClick = { draft = draft.copy(wallpaper = -1) }) { Text(if (draft.wallpaper == -1) "✓ Use Home wallpaper" else "Use Home wallpaper") }
                                        LockWallpaperStrip(draft.wallpaper) { draft = draft.copy(wallpaper = it) }
                                        Text("Dim", Modifier.padding(top = 12.dp), fontSize = 13.sp)
                                        IosSlider(draft.dim, { draft = draft.copy(dim = it) }, valueRange = 0f..0.65f)
                                    }
                                    3 -> {
                                        IosToggleRow("Battery", draft.showBattery) { draft = draft.copy(showBattery = it) }
                                        IosToggleRow("Next alarm", draft.showAlarm) { draft = draft.copy(showAlarm = it) }
                                        IosToggleRow("Notification indicator", draft.notificationCount) { draft = draft.copy(notificationCount = it) }
                                        Text("Private notification content stays hidden until Android unlocks.", fontSize = 12.sp, color = Color.White.copy(alpha = .65f))
                                    }
                                }
                            }
                        }
                    }
                    Surface(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(28.dp), color = Color.Black.copy(alpha = .5f)) {
                        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("Clock", "Wallpaper", "Widgets").forEachIndexed { i, title ->
                                TextButton(onClick = { panel = if (panel == i + 1) 0 else i + 1 }, modifier = Modifier.testTag("lock_editor_tab:${i + 1}")) { Text(title, color = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LockClockStylePicker(options: LockScreenSettings, cyber: Boolean, onChange: (LockScreenSettings) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(if (cyber) "HUD" else "Rounded", "Light", "Bold", "Serif", "Mono", "Condensed").forEachIndexed { i, name ->
            OutlinedButton(onClick = { onChange(options.copy(clockStyle = i)) },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (options.clockStyle == i) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color.Transparent),
                border = BorderStroke(if (options.clockStyle == i) 1.5.dp else 1.dp, if (options.clockStyle == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .2f)),
                modifier = Modifier.testTag("lock_clock_style:$i").semantics { selected = options.clockStyle == i }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("12", fontSize = 24.sp, fontFamily = lockClockFont(cyber, i),
                        fontWeight = when (i) { 1 -> FontWeight.Light; 2 -> FontWeight.Bold; else -> FontWeight.Medium })
                    Text(name, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LockWallpaperStrip(selected: Int, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WALLPAPER_NAMES.forEachIndexed { id, name ->
            val thumbnail by produceState<android.graphics.Bitmap?>(null, id) {
                value = withContext(Dispatchers.IO) { WallpaperStore(context).thumbnail(id) }
            }
            Column(Modifier.width(82.dp).semantics { contentDescription = name; this.selected = selected == id }
                .clickable { onSelect(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(82.dp, 112.dp).clip(RoundedCornerShape(16.dp))
                    .border(if (selected == id) 2.dp else 0.dp, Color.White, RoundedCornerShape(16.dp))) {
                    thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Text(name, fontSize = 10.sp, maxLines = 2, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}
