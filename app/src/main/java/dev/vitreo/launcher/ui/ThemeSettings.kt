package dev.vitreo.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.LauncherViewModel
import dev.vitreo.launcher.R
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.IconOverride
import dev.vitreo.launcher.core.THEME_ICON_PACK
import dev.vitreo.launcher.core.THEME_ICON_KINDS
import dev.vitreo.launcher.core.WALLPAPER_NAMES
import dev.vitreo.launcher.graphics.WallpaperStore
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ThemeSettings(ui: LauncherViewModel.UiState, onAppearance: (Appearance) -> Unit) {
    val a = ui.layout.appearance
    listOf(Triple("iOS 26", "Liquid Glass and soft, continuous corners", false),
        Triple("iOS 16", "Classic frosted surfaces, quiet shadows and original colorful icons", true),
        Triple("Cyberpunk", "Dark HUD, red frames and cyan line icons", false)).forEachIndexed { index, (name, subtitle, classic) ->
        val preset = if (index == 2) 1 else 0
        val chosen = a.themePreset == preset && (preset == 1 || a.iosClassic == classic)
        val glass = LocalGlass.current
        CompositionLocalProvider(LocalGlass provides glass.copy(appearance = glass.appearance.copy(themePreset = preset, iosClassic = classic))) {
            VitreoTheme(a.interfaceStyle, preset) {
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 14.dp).height(158.dp)
                    .testTag(if (classic) "theme_ios16" else "theme_$preset").selectable(chosen, role = Role.RadioButton,
                        onClick = { onAppearance(a.copy(themePreset = preset, iosClassic = if (preset == 1) a.iosClassic else classic)) }), material = GlassMaterial.Regular) {
                    Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            RadioButton(chosen, onClick = null)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            listOf(R.drawable.cyber_phone, R.drawable.cyber_messages, R.drawable.cyber_camera).forEachIndexed { index, icon ->
                                if (preset == 1) Box(Modifier.size(39.dp).background(CyberColors.Background, CyberShape).border(1.dp, CyberColors.Red, CyberShape)) {
                                    Image(painterResource(icon), null, Modifier.fillMaxSize().padding(4.dp), colorFilter = ColorFilter.tint(CyberColors.Cyan))
                                } else IosAppIcon(listOf("phone", "messages", "camera")[index], 0, Color.White, Modifier.size(39.dp))
                            }
                        }
                        Text(subtitle, fontSize = 13.sp, maxLines = 2)
                    }
                }
            }
        }
    }
    IosSectionLabel("Icon pack")
    IosGroup {
        PackChoice("Theme default", a.iconPack.isEmpty(), "pack_default") { onAppearance(a.copy(iconPack = "")) }
        ui.iconPacks.forEach { pack ->
            IosDivider()
            PackChoice(pack.label, a.iconPack == pack.packageName, "pack:${pack.packageName}") { onAppearance(a.copy(iconPack = pack.packageName)) }
        }
    }
    Text(if (ui.iconPacks.isEmpty()) "Install an ADW or Nova-compatible icon pack, then return here. Cyberpunk includes line icons for common apps."
        else "Packs apply across Home, folders and App Library. Apps missing from the pack keep their default artwork.",
        Modifier.padding(horizontal = 17.dp, vertical = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (a.iconPack.isNotEmpty() && ui.iconPacks.none { it.packageName == a.iconPack })
        Text("The selected pack is unavailable. Theme defaults are being used.", Modifier.padding(17.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
    if (a.themePreset == 1) {
        IosSectionLabel("Cyberpunk experience")
        IosGroup {
            PackChoice("Classic · Quiet HUD", !a.cyberImmersive, "cyber_classic") { onAppearance(a.copy(cyberImmersive = false)) }
            IosDivider()
            PackChoice("Immersive · Neon city", a.cyberImmersive, "cyber_immersive") { onAppearance(a.copy(cyberImmersive = true)) }
        }
        Text("Immersive adds rain, drifting light, a scanning HUD and animated panel edges. Classic keeps the original restrained appearance. Both keep your layout and wallpaper.",
            Modifier.padding(17.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IosSectionLabel("Status bar appearance")
        IosGroup {
            Column(Modifier.fillMaxWidth().padding(17.dp).testTag("cyber_status_controls")) {
                Box(Modifier.fillMaxWidth().height(72.dp).clip(CyberShape)
                    .background(Brush.linearGradient(listOf(Color(0xff56798d), CyberColors.Background)))
                    .testTag("cyber_status_preview"), contentAlignment = Alignment.Center) {
                    HomeStatusBar(LocalHomeClock.current, Modifier.height(statusBandHeight(a)), null, null)
                }
                Row(Modifier.padding(top = 16.dp)) {
                    Text("Rail thickness", Modifier.weight(1f)); Text("${a.cyberStatusHeight.roundToInt()} dp")
                }
                IosSlider(a.cyberStatusHeight, { onAppearance(a.copy(cyberStatusHeight = it)) },
                    Modifier.testTag("cyber_status_height"), valueRange = 16f..40f)
                Row {
                    Text("Opacity", Modifier.weight(1f)); Text("${(a.cyberStatusOpacity * 100).roundToInt()}%")
                }
                IosSlider(a.cyberStatusOpacity, { onAppearance(a.copy(cyberStatusOpacity = it)) },
                    Modifier.testTag("cyber_status_opacity"), valueRange = .2f..1f)
                TextButton(onClick = { onAppearance(a.copy(cyberStatusHeight = 22f, cyberStatusOpacity = .72f)) },
                    modifier = Modifier.testTag("cyber_status_reset").align(Alignment.End)) { Text("Reset status bar") }
            }
        }
        Text("Applies to Cyberpunk Home, Lock Screen and Notification Center. Camera / system insets remain reserved, even with a thin rail. Enable the Home bar separately in Status Bar & Notifications. iOS keeps its own appearance.",
            Modifier.padding(17.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IosSectionLabel("Cyberpunk wallpapers")
        val context = LocalContext.current
        val wallpapers by produceState<Map<Int, Bitmap>>(emptyMap(), context) {
            value = withContext(Dispatchers.Default) {
                val store = WallpaperStore(context)
                WALLPAPER_NAMES.indices.drop(4).associateWith(store::thumbnail)
            }
        }
        WALLPAPER_NAMES.indices.drop(4).chunked(2).forEach { ids ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ids.forEach { id ->
                    val title = WALLPAPER_NAMES[id]
                    Column(Modifier.weight(1f).testTag("cyber_wallpaper:${id - 4}").clickable { onAppearance(a.copy(palette = id, usePhoto = false)) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(.62f).clip(CyberShape).background(CyberColors.Background)) {
                            wallpapers[id]?.let { Image(it.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                        }
                        Text(title, Modifier.padding(vertical = 8.dp), fontSize = 14.sp)
                    }
                }
                if (ids.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        IosSectionLabel("HUD effects")
        IosGroup { IosToggleRow("Panel sweeps & glitch accents", a.cyberEffects) { onAppearance(a.copy(cyberEffects = it)) } }
        Text("Classic uses brief opening effects. Immersive adds ambient animation while Home is visible. Reduce Motion and Battery Saver turn effects off. Press and hold an app, then More > Change Icon to choose an alternate.",
            Modifier.padding(17.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PackChoice(label: String, checked: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).selectable(checked, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 52.dp).padding(horizontal = 17.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        RadioButton(checked, onClick = null)
    }
}

@Composable
fun IconPickerSheet(model: LauncherViewModel, ui: LauncherViewModel.UiState, appId: String, onClose: () -> Unit) {
    val packs = ui.iconPacks
    var selectedPack by rememberSaveable(appId) { mutableStateOf(ui.layout.appearance.iconOverrides[appId]?.pack
        ?: ui.layout.appearance.iconPack.takeIf { it.isNotEmpty() } ?: THEME_ICON_PACK) }
    var query by rememberSaveable(appId) { mutableStateOf("") }
    var choosingPack by remember { mutableStateOf(false) }
    val names by produceState<List<String>?>(null, selectedPack, ui.iconRevision) {
        value = null
        value = if (selectedPack == THEME_ICON_PACK) THEME_ICON_KINDS
            else withContext(Dispatchers.IO) { model.iconPacks.names(selectedPack) }
    }
    val results by produceState(emptyList<String>(), names, query) {
        value = withContext(Dispatchers.Default) { names.orEmpty().filter { it.contains(query.trim().replace(' ', '_'), ignoreCase = true) } }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().padding(horizontal = 20.dp).testTag("icon_picker")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { model.overrideIcon(appId, null); onClose() }, modifier = Modifier.testTag("reset_app_icon")) { Text("Use Default") }
        }
        Text("Change Icon", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(ui.apps.find { it.id == appId }?.label.orEmpty(), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        IosGroup { IosRow("Icon source", if (selectedPack == THEME_ICON_PACK) "Current theme" else packs.find { it.packageName == selectedPack }?.label ?: "Choose", onClick = { choosingPack = true }) }
        Text("Choose any built-in icon for this app. Theme icons follow iOS 26, iOS 16 or Cyberpunk when you switch themes.", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("icon_search"),
            label = { Text("Search icons") }, singleLine = true)
        when {
            selectedPack != THEME_ICON_PACK && packs.none { it.packageName == selectedPack } -> Text("This icon pack is unavailable. Choose Current theme or another pack.", Modifier.padding(17.dp))
            names == null -> CircularProgressIndicator(Modifier.padding(20.dp))
            results.isEmpty() -> Text("No icons found. Choose another pack or search term.", Modifier.padding(17.dp))
            else -> LazyVerticalGrid(GridCells.Adaptive(74.dp), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results, key = { it }) { name ->
                    Column(Modifier.testTag("pick_icon:$name").clickable { model.overrideIcon(appId, IconOverride(selectedPack, name)); onClose() }
                        .padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selectedPack == THEME_ICON_PACK) {
                            val cyber = LocalCyber.current
                            Box(Modifier.size(48.dp).clip(if (cyber) CyberShape else IconShape).then(if (cyber) Modifier.background(CyberColors.Panel).border(1.dp, CyberColors.Red, CyberShape) else Modifier)) {
                                if (cyber) cyberArtwork(name)?.let { Image(painterResource(it), null, Modifier.fillMaxSize().padding(4.dp), colorFilter = ColorFilter.tint(CyberColors.Cyan)) }
                                else IosAppIcon(name, ui.layout.appearance.iconStyle, Color(ui.layout.appearance.tintColor), Modifier.fillMaxSize())
                            }
                        } else PackArtwork(selectedPack, name, model, ui.iconRevision, Modifier.size(48.dp))
                        Text(name.replace('_', ' '), Modifier.padding(top = 5.dp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (choosingPack) AlertDialog(onDismissRequest = { choosingPack = false }, title = { Text("Icon packs") }, text = {
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            PackChoice("Current theme · Built-in icons", selectedPack == THEME_ICON_PACK, "picker_pack:theme") { selectedPack = THEME_ICON_PACK; choosingPack = false }
            packs.forEach { pack -> PackChoice(pack.label, selectedPack == pack.packageName, "picker_pack:${pack.packageName}") {
                selectedPack = pack.packageName; choosingPack = false
            } }
        }
    }, confirmButton = { TextButton(onClick = { choosingPack = false }) { Text("Done") } })
}

@Composable
private fun PackArtwork(pack: String, name: String, model: LauncherViewModel, revision: Int, modifier: Modifier) {
    val image by produceState<Bitmap?>(null, pack, name, revision) {
        value = null; value = withContext(Dispatchers.IO) { model.iconPacks.artwork(pack, name) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it.asImageBitmap(), name.replace('_', ' '), Modifier.fillMaxSize()) }
    }
}
