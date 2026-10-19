package dev.vitreo.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.BuildConfig
import dev.vitreo.launcher.LauncherViewModel
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.core.MIN_ICON_SIZE
import dev.vitreo.launcher.core.MAX_ICON_SIZE
import dev.vitreo.launcher.graphics.WallpaperStore
import dev.vitreo.launcher.graphics.GlassDiagnostics
import dev.vitreo.launcher.graphics.Viewport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    Root("Settings"), Home("Home Screen"), Wallpaper("Wallpaper"), Glass("Motion & Glass"),
    Backup("Layout & Backup"), About("About Vitreo"), Themes("Themes & Icons"), Controls("Control Center")
}

@Composable
private fun SettingsFootnote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp))
}

@Composable
fun SettingsSheet(ui: LauncherViewModel.UiState, defaultHome: Boolean, onClose: () -> Unit,
    onAppearance: (Appearance) -> Unit, onSetHome: () -> Unit, onPhoto: () -> Unit, onEditPhoto: () -> Unit,
    photoBusy: Boolean, onExport: () -> Unit, onImport: () -> Unit, onDiagnostics: () -> Unit, onRefresh: () -> Unit,
    onReset: () -> Unit, onLicenses: () -> Unit, onWidgets: () -> Unit, onControls: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Root) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val a = ui.layout.appearance
    val reduced = LocalGlass.current.appearance.reducedEffects
    BackHandler(page != SettingsPage.Root) { page = SettingsPage.Root }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (page == SettingsPage.Root) onClose() else page = SettingsPage.Root }) {
                LauncherSymbol(Icons.Outlined.ChevronLeft, null, Modifier.size(22.dp))
                Text(if (page == SettingsPage.Root) "Home" else "Settings")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", fontWeight = FontWeight.SemiBold) }
        }
        AnimatedContent(page, modifier = Modifier.weight(1f), transitionSpec = {
            val direction = if (targetState == SettingsPage.Root) -1 else 1
            if (reduced) EnterTransition.None togetherWith ExitTransition.None
            else (slideInHorizontally(spring(dampingRatio = 1f, stiffness = 480f)) { it * direction / 5 } + fadeIn(tween(180)))
                .togetherWith(slideOutHorizontally(tween(180)) { -it * direction / 8 } + fadeOut(tween(130)))
        }, label = "settings navigation") { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(current.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 25.dp).testTag("settings_title"))
                when (current) {
                    SettingsPage.Root -> {
                        IosGroup {
                            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(58.dp).clip(if (LocalCyber.current) CyberShape else IconShape)
                                    .background(Brush.linearGradient(if (LocalCyber.current) listOf(CyberColors.Red, CyberColors.Panel)
                                        else listOf(Color(0xff6cb9ff), Color(0xff2862df)))), contentAlignment = Alignment.Center) {
                                    LauncherSymbol(Icons.Outlined.Layers, null, Modifier.size(34.dp), tint = Color.White)
                                }
                                Column(Modifier.padding(start = 15.dp)) {
                                    Text("Vitreo", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                    Text("Make yourself at home", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        IosSectionLabel("Personalization")
                        IosGroup {
                            IosRow("Themes & Icons", if (a.themePreset == 1) "Cyberpunk" else "iOS") { page = SettingsPage.Themes }; IosDivider()
                            IosRow("Home Screen", "Icons & appearance") { page = SettingsPage.Home }; IosDivider()
                            IosRow("Wallpaper", if (a.usePhoto) "Photo" else "Collection") { page = SettingsPage.Wallpaper }; IosDivider()
                            IosRow("Motion & Glass") { page = SettingsPage.Glass }; IosDivider()
                            IosRow("Control Center", if (a.controlCenter) "On" else "Off") { page = SettingsPage.Controls }; IosDivider()
                            IosRow("Status Bar & Notifications") { page = SettingsPage.Controls }; IosDivider()
                            IosRow("Widgets", onClick = onWidgets)
                        }
                        IosSectionLabel("General")
                        IosGroup {
                            IosRow("Default launcher", if (defaultHome) "Vitreo" else "Choose", enabled = !defaultHome, onClick = onSetHome); IosDivider()
                            IosRow("Layout & Backup") { page = SettingsPage.Backup }; IosDivider()
                            IosRow("Refresh apps", "${ui.apps.size} apps", enabled = ui.ready, onClick = onRefresh)
                        }
                        IosSectionLabel("Information")
                        IosGroup {
                            IosRow("About Vitreo", BuildConfig.VERSION_NAME) { page = SettingsPage.About }; IosDivider()
                            IosRow("Open source licenses", onClick = onLicenses)
                        }
                        SettingsFootnote("Swipe down on Home for Search. Control Center opens with a short downward swipe from the upper-right area below Android's status bar.")
                    }
                    SettingsPage.Home -> {
                        HomeAppearancePreview(ui)
                        IosSectionLabel("App icons")
                        IosGroup {
                            if (a.themePreset == 0) {
                            IosSegmented(listOf("Light", "Dark", "Tinted", "Clear"), a.iconStyle, Modifier.padding(12.dp)) { onAppearance(a.copy(iconStyle = it)) }
                            IosToggleRow("iOS-style icons", a.iosIcons) { onAppearance(a.copy(iosIcons = it)) }
                            if (a.iconStyle == 2) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(0xff8fbfff, 0xffe6b4f3, 0xffe9c68e, 0xffa5dfbb, 0xfff4b4bb).forEachIndexed { index, color ->
                                    Box(Modifier.size(40.dp).background(Color(color), CircleShape)
                                        .semantics { contentDescription = listOf("Blue", "Purple", "Gold", "Green", "Pink")[index] + " tint"; selected = a.tintColor == color.toInt() }
                                        .clickable { onAppearance(a.copy(tintColor = color.toInt())) }, contentAlignment = Alignment.Center) {
                                        if (a.tintColor == color.toInt()) LauncherSymbol(Icons.Outlined.Check, "Selected tint ${index + 1}", Modifier.size(21.dp), tint = Color(0xff2d3440))
                                    }
                                }
                            }
                            IosDivider()
                            }
                            IosToggleRow("Show app names", a.showLabels, modifier = Modifier.testTag("labels_switch")) { onAppearance(a.copy(showLabels = it)) }
                            IosDivider()
                            SettingsSlider("Icon size", a.iconSize, "${a.iconSize.roundToInt()} pt", MIN_ICON_SIZE..MAX_ICON_SIZE) { onAppearance(a.copy(iconSize = it)) }
                        }
                        SettingsFootnote(if (a.themePreset == 1) "Cyberpunk uses Arcticons line artwork and HUD frames. Choose an installed icon pack in Themes & Icons. Credits are in Open source licenses."
                            else "iOS app artwork from SVG Stack, with live Clock and Calendar. Other apps keep their own icons. Credits are in Open source licenses.")
                        IosSectionLabel("Interface")
                        IosGroup {
                            IosSegmented(listOf("System", "Light", "Dark"), a.interfaceStyle, Modifier.padding(12.dp)) { onAppearance(a.copy(interfaceStyle = it)) }
                            IosDivider()
                            IosToggleRow("Clock & Calendar on Home", a.showWidgets) { onAppearance(a.copy(showWidgets = it)) }
                        }
                        SettingsFootnote("Press and hold an app to rearrange Home, create folders or move apps to the dock.")
                    }
                    SettingsPage.Controls -> {
                        IosSectionLabel("Access")
                        IosGroup {
                            IosToggleRow("Enable Control Center", a.controlCenter, modifier = Modifier.testTag("control_center_switch")) {
                                onAppearance(a.copy(controlCenter = it))
                            }
                            IosDivider()
                            IosRow("Open Control Center", enabled = a.controlCenter, onClick = onControls)
                        }
                        SettingsFootnote("Available on Home, Today and App Library. Swipe down from the upper-right area below the status bar, or tap the controls button in Today. Swipe up from the panel's bottom handle to close it.")
                        IosSectionLabel("Controls")
                        SettingsFootnote("Media volume and flashlight use Android's public controls. Network, Focus, rotation and device shortcuts open Android settings. Connect media player below to show real track metadata and supported playback actions. Without access, media keys are available while Android reports active playback.")
                        SettingsFootnote("Brightness applies to Vitreo only. Use Display settings for system brightness. No camera recording or overlay service is used.")
                        IosGroup {
                            IosRow("Use system brightness", if (a.homeBrightness < 0) "Selected" else "Reset") { onAppearance(a.copy(homeBrightness = -1f)) }
                        }
                        SettingsFootnote("Turn Control Center off to remove its gesture and Today button. Android's own Quick Settings remain available from the status bar.")
                        SystemIntegrationSettings(a, onAppearance)
                    }
                    SettingsPage.Glass -> {
                        if (a.themePreset == 1) {
                            SettingsFootnote("Cyberpunk uses dark HUD panels. Liquid Glass controls apply to the iOS theme.")
                        } else {
                        GlassPreview()
                        SettingsFootnote(when {
                            a.opaquePanels -> "Solid panels are on. Turn off Reduce Transparency to see glass."
                            ui.powerSaver -> "Frosted glass is active while Battery Saver is on."
                            reduced -> "Frosted glass is active while Reduce Motion is on."
                            android.os.Build.VERSION.SDK_INT < 33 -> "Frosted glass is active. Live edge refraction needs Android 13 or later."
                            GlassDiagnostics.lastFailure != "none" -> "A graphics error was recorded. If glass looks flat, export diagnostics from About Vitreo."
                            else -> "Live edge refraction is enabled. Details in the wallpaper make the curved edges easier to see."
                        })
                        IosSectionLabel("Material")
                        IosGroup {
                            SettingsSlider("Frost", a.frost, "${(a.frost * 100).roundToInt()}%", enabled = !a.opaquePanels) { onAppearance(a.copy(frost = it)) }
                            IosDivider()
                            SettingsSlider("Refraction", a.refraction, "${(a.refraction * 100).roundToInt()}%",
                                enabled = !a.opaquePanels && !reduced && android.os.Build.VERSION.SDK_INT >= 33) { onAppearance(a.copy(refraction = it)) }
                        }
                        SettingsFootnote("Controls catch light at their curved edges. Folders and app lists use a denser material to keep names easy to read.")
                        }
                        IosSectionLabel("Accessibility")
                        IosGroup {
                            IosToggleRow("Reduce Motion", a.reducedEffects) { onAppearance(a.copy(reducedEffects = it)) }; IosDivider()
                            IosToggleRow("Reduce Transparency", a.opaquePanels) { onAppearance(a.copy(opaquePanels = it)) }
                        }
                        SettingsFootnote(if (ui.powerSaver) "Battery Saver is on. Motion and refraction are reduced automatically." else "Reduce Motion removes zooming, spring motion and icon jiggle. Battery Saver also reduces effects automatically.")
                        IosGroup { IosRow("Reset effects") { onAppearance(a.copy(refraction = .65f, frost = .38f, reducedEffects = false, opaquePanels = false)) } }
                    }
                    SettingsPage.Wallpaper -> {
                        IosGroup {
                            IosRow(if (photoBusy) "Opening photo…" else "Choose a Photo…", enabled = !photoBusy, onClick = onPhoto)
                            if (a.usePhoto) { IosDivider(); IosRow("Adjust Current Photo", enabled = !photoBusy, onClick = onEditPhoto) }
                        }
                        SettingsFootnote("Pinch to zoom, drag to position, or choose Fit to show the whole photo. Changes are applied only when you save.")
                        IosSectionLabel("Collection")
                        WallpaperCollection(a, onAppearance)
                        IosSectionLabel("Readability")
                        IosGroup {
                            SettingsSlider("Dim wallpaper", a.wallpaperDim, "${(a.wallpaperDim * 100).roundToInt()}%", 0f.. .6f) { onAppearance(a.copy(wallpaperDim = it)) }
                        }
                        SettingsFootnote("A little dimming helps white Home Screen labels stand out on bright photos.")
                        IosSectionLabel("System integration")
                        IosGroup {
                            IosToggleRow("Sync with Android Home", a.syncWallpaper, modifier = Modifier.testTag("wallpaper_sync")) {
                                onAppearance(a.copy(syncWallpaper = it))
                            }
                        }
                        SettingsFootnote("When Vitreo is your default launcher, use the same wallpaper on Android Home and behind Recents. Includes your photo position, dimming and Cyberpunk background.")
                    }
                    SettingsPage.Backup -> {
                        IosGroup {
                            IosRow("Export Layout", onClick = onExport); IosDivider()
                            IosRow("Import Layout") { confirmation = "import" }; IosDivider()
                            IosRow("Reset Home Screen Layout", destructive = true) { confirmation = "reset" }
                        }
                        SettingsFootnote("Backups include apps, folders and appearance. Photos stay on this device; add third-party widgets again on each device.")
                    }
                    SettingsPage.About -> {
                        IosGroup { IosRow("Version", BuildConfig.VERSION_NAME); IosDivider(); IosRow("Language", "English"); IosDivider(); IosRow("Export Diagnostics", onClick = onDiagnostics) }
                        SettingsFootnote("An iOS-inspired home for your Android apps, with original artwork and a native glass renderer. No accounts, ads or telemetry. Interface language: English.")
                    }
                    SettingsPage.Themes -> ThemeSettings(ui, onAppearance)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(if (action == "reset") "Reset Home Screen?" else "Import Layout?") },
            text = { Text("This replaces your current layout. Export a backup first if you want to keep it.") },
            confirmButton = { TextButton(onClick = { confirmation = null; if (action == "reset") onReset() else onImport() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}

@Composable
private fun GlassPreview() {
    val glass = LocalGlass.current
    var viewport by remember { mutableStateOf(Viewport()) }
    Box(Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(28.dp)).onGloballyPositioned {
        val position = it.positionOnScreen()
        viewport = Viewport(it.size.width.toFloat(), it.size.height.toFloat(), position.x, position.y)
    }, contentAlignment = Alignment.Center) {
        glass.frame?.let { Image(it.sharp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        CompositionLocalProvider(LocalGlass provides glass.copy(viewport = viewport)) {
            GlassPanel(Modifier.width(210.dp).height(104.dp), radius = 30f, interactive = true) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    LauncherSymbol(Icons.Outlined.BlurOn, null, Modifier.size(28.dp), tint = Color.White)
                    Text("Liquid Glass", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Text("Touch to catch the light", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(title: String, value: Float, detail: String, range: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 17.dp, vertical = 12.dp)) {
        Row { Text(title, Modifier.weight(1f)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        IosSlider(value, onChange, valueRange = range, enabled = enabled)
    }
}

@Composable
private fun HomeAppearancePreview(ui: LauncherViewModel.UiState) {
    val apps = remember(ui.apps) { ui.apps.associateBy { it.id } }
    val frame = LocalGlass.current.frame
    Box(Modifier.fillMaxWidth().height(125.dp).clip(RoundedCornerShape(26.dp))) {
        frame?.let { Image(it.sharp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f)))
        if (LocalCyber.current) CyberBackdrop(ui.layout.appearance.usePhoto, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ui.apps.take(4).forEach { app ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    EntryIcon(HomeEntry.App(app.id), apps, 50.dp)
                    if (ui.layout.appearance.showLabels) Text(app.label, color = LocalGlassContentColor.current, fontSize = 12.sp,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun WallpaperCollection(a: Appearance, onAppearance: (Appearance) -> Unit) {
    val context = LocalContext.current
    val previews by produceState<List<android.graphics.Bitmap>>(emptyList()) {
        value = withContext(Dispatchers.Default) { val store = WallpaperStore(context); (0..5).map { store.procedural(it) } }
    }
    listOf("Blue", "Orchid", "Aurora", "Midnight", "Neon Rider", "Digital Dusk").chunked(2).forEachIndexed { row, names ->
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            names.forEachIndexed { column, title ->
                val index = row * 2 + column
                Box(Modifier.weight(1f).aspectRatio(.7f).clip(RoundedCornerShape(24.dp))
                    .background(Color(0xff1c315e)).clickable { onAppearance(a.copy(palette = index, usePhoto = false)) }) {
                    previews.getOrNull(index)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .5f)))))
                    Text(title, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                    if (!a.usePhoto && a.palette == index) LauncherSymbol(Icons.Outlined.CheckCircle, "Selected", Modifier.align(Alignment.TopEnd).padding(12.dp), tint = Color.White)
                }
            }
        }
    }
}
