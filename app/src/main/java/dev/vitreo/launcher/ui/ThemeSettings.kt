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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ThemeSettings(ui: LauncherViewModel.UiState, onAppearance: (Appearance) -> Unit) {
    val a = ui.layout.appearance
    listOf("iOS" to "Liquid Glass and soft, continuous corners", "Cyberpunk" to "Dark HUD, red frames and cyan line icons").forEachIndexed { preset, (name, subtitle) ->
        val glass = LocalGlass.current
        CompositionLocalProvider(LocalGlass provides glass.copy(appearance = glass.appearance.copy(themePreset = preset))) {
            VitreoTheme(a.interfaceStyle, preset) {
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 14.dp).height(158.dp)
                    .testTag("theme_$preset").selectable(a.themePreset == preset, role = Role.RadioButton,
                        onClick = { onAppearance(a.copy(themePreset = preset)) }), material = GlassMaterial.Regular) {
                    Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            RadioButton(a.themePreset == preset, onClick = null)
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
        IosSectionLabel("HUD effects")
        IosGroup { IosToggleRow("Panel sweeps & glitch accents", a.cyberEffects) { onAppearance(a.copy(cyberEffects = it)) } }
        Text("Effects play briefly when panels open. Reduce Motion and Battery Saver turn them off. Press and hold an app, then More > Change Icon to choose an alternate.",
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
        ?: ui.layout.appearance.iconPack.takeIf { it.isNotEmpty() } ?: packs.firstOrNull()?.packageName.orEmpty()) }
    var query by rememberSaveable(appId) { mutableStateOf("") }
    var choosingPack by remember { mutableStateOf(false) }
    val names by produceState<List<String>?>(null, selectedPack, ui.iconRevision) {
        value = null; value = withContext(Dispatchers.IO) { model.iconPacks.names(selectedPack) }
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
        IosGroup { IosRow("Icon pack", packs.find { it.packageName == selectedPack }?.label ?: "Choose", onClick = { choosingPack = true }) }
        OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("icon_search"),
            label = { Text("Search icons") }, singleLine = true)
        when {
            packs.isEmpty() -> Text("Install an icon pack to choose alternate icons. Your current icon is kept.", Modifier.padding(17.dp))
            names == null -> CircularProgressIndicator(Modifier.padding(20.dp))
            results.isEmpty() -> Text("No icons found. Choose another pack or search term.", Modifier.padding(17.dp))
            else -> LazyVerticalGrid(GridCells.Adaptive(74.dp), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results, key = { it }) { name ->
                    Column(Modifier.testTag("pick_icon:$name").clickable { model.overrideIcon(appId, IconOverride(selectedPack, name)); onClose() }
                        .padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PackArtwork(selectedPack, name, model, ui.iconRevision, Modifier.size(48.dp))
                        Text(name.replace('_', ' '), Modifier.padding(top = 5.dp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (choosingPack) AlertDialog(onDismissRequest = { choosingPack = false }, title = { Text("Icon packs") }, text = {
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
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
