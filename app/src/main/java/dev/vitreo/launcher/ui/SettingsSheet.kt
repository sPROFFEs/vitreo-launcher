package dev.vitreo.launcher.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.BuildConfig
import dev.vitreo.launcher.LauncherViewModel
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.HomeEntry
import kotlin.math.roundToInt

@Composable
fun SettingsSheet(ui: LauncherViewModel.UiState, defaultHome: Boolean, onClose: () -> Unit,
    onAppearance: (Appearance) -> Unit, onSetHome: () -> Unit, onPhoto: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onDiagnostics: () -> Unit, onRefresh: () -> Unit,
    onReset: () -> Unit, onLicenses: () -> Unit, onWidgets: () -> Unit) {
    var page by remember { mutableStateOf("Ajustes") }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val a = ui.layout.appearance
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (page == "Ajustes") onClose() else page = "Ajustes" }) {
                Icon(Icons.Outlined.ChevronLeft, null, Modifier.size(22.dp))
                Text(if (page == "Ajustes") "Inicio" else "Ajustes")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("OK", fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(page, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 25.dp).testTag("settings_title"))
            when (page) {
                "Ajustes" -> {
                    IosGroup {
                        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(58.dp).clip(IconShape).background(Brush.linearGradient(listOf(Color(0xff6cb9ff), Color(0xff2862df)))), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Layers, null, Modifier.size(34.dp), tint = Color.White)
                            }
                            Column(Modifier.padding(start = 15.dp)) {
                                Text("Vitreo", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                Text("Tu inicio, a tu manera", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    IosSectionLabel("Personalización")
                    IosGroup {
                        IosRow("Pantalla de inicio", "Iconos y aspecto") { page = "Pantalla de inicio" }; IosDivider()
                        IosRow("Fondo de pantalla") { page = "Fondo de pantalla" }; IosDivider()
                        IosRow("Widgets", onClick = onWidgets)
                    }
                    IosSectionLabel("General")
                    IosGroup {
                        IosRow("App de inicio", if (defaultHome) "Vitreo" else "Elegir", enabled = !defaultHome, onClick = onSetHome); IosDivider()
                        IosRow("Copias y distribución") { page = "Copias y distribución" }; IosDivider()
                        IosRow("Actualizar aplicaciones", enabled = ui.ready, onClick = onRefresh)
                    }
                    IosSectionLabel("Información")
                    IosGroup {
                        IosRow("Acerca de Vitreo", BuildConfig.VERSION_NAME) { page = "Acerca de Vitreo" }; IosDivider()
                        IosRow("Licencias", onClick = onLicenses)
                    }
                    Text("Hecho para que todo encuentre su sitio.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, modifier = Modifier.padding(start = 17.dp, top = 15.dp, bottom = 25.dp))
                }
                "Pantalla de inicio" -> {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 24f, tint = .16f) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val apps = ui.apps.associateBy { it.id }
                            ui.apps.take(4).forEach { app -> EntryIcon(HomeEntry.App(app.id), apps, 55.dp) }
                        }
                    }
                    IosSectionLabel("Iconos")
                    IosGroup {
                        IosSegmented(listOf("Original", "Oscuro", "Tintado", "Cristal"), a.iconStyle, Modifier.padding(12.dp)) { onAppearance(a.copy(iconStyle = it)) }
                        if (a.iconStyle == 2) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(0xff8fbfff, 0xffe6b4f3, 0xffe9c68e, 0xffa5dfbb, 0xfff4b4bb).forEach { color ->
                                Box(Modifier.size(32.dp).background(Color(color), CircleShape).clickable { onAppearance(a.copy(tintColor = color.toInt())) }, contentAlignment = Alignment.Center) {
                                    if (a.tintColor == color.toInt()) Icon(Icons.Outlined.Check, null, Modifier.size(19.dp), tint = Color(0xff2d3440))
                                }
                            }
                        }
                        IosDivider()
                        IosToggleRow("Mostrar nombres", a.showLabels, modifier = Modifier.testTag("labels_switch")) { onAppearance(a.copy(showLabels = it)) }
                        IosDivider()
                        Column(Modifier.padding(horizontal = 17.dp, vertical = 12.dp)) {
                            Row { Text("Tamaño", Modifier.weight(1f)); Text("${a.iconSize.roundToInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Slider(a.iconSize, { onAppearance(a.copy(iconSize = it)) }, valueRange = 40f..68f, steps = 13)
                        }
                    }
                    IosSectionLabel("Apariencia")
                    IosGroup {
                        IosSegmented(listOf("Automático", "Claro", "Oscuro"), a.interfaceStyle, Modifier.padding(12.dp)) { onAppearance(a.copy(interfaceStyle = it)) }
                        IosDivider()
                        IosToggleRow("Reloj y calendario", a.showWidgets) { onAppearance(a.copy(showWidgets = it)) }
                    }
                    IosSectionLabel("Liquid Glass")
                    IosGroup {
                        Column(Modifier.padding(horizontal = 17.dp, vertical = 12.dp)) {
                            Text("Transparencia")
                            Slider(1f - a.frost, { onAppearance(a.copy(frost = 1f - it)) }, enabled = !a.opaquePanels)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Esmerilado", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Transparente", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IosDivider()
                        IosToggleRow("Reducir movimiento", a.reducedEffects) { onAppearance(a.copy(reducedEffects = it)) }
                        IosDivider()
                        IosToggleRow("Reducir transparencia", a.opaquePanels) { onAppearance(a.copy(opaquePanels = it)) }
                    }
                }
                "Fondo de pantalla" -> {
                    Text("Elige un nuevo fondo", fontSize = 17.sp, modifier = Modifier.padding(bottom = 20.dp))
                    val palettes = listOf(listOf(0xff163890, 0xff6cbded), listOf(0xff524095, 0xffd69ebc),
                        listOf(0xff9f455f, 0xfff8ba8f), listOf(0xff080e1c, 0xff4b6884))
                    listOf("Azul", "Orquídea", "Aurora", "Medianoche").chunked(2).forEachIndexed { row, names ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            names.forEachIndexed { column, title ->
                                val index = row * 2 + column
                                Box(Modifier.weight(1f).aspectRatio(.7f).clip(RoundedCornerShape(24.dp))
                                    .background(Brush.linearGradient(palettes[index].map(::Color)))
                                    .clickable { onAppearance(a.copy(palette = index, usePhoto = false)) }) {
                                    Text(title, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                                    if (!a.usePhoto && a.palette == index) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(12.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                    IosGroup { IosRow("Elegir una foto…", onClick = onPhoto) }
                }
                "Copias y distribución" -> {
                    IosGroup {
                        IosRow("Exportar escritorio", onClick = onExport); IosDivider()
                        IosRow("Importar escritorio") { confirmation = "import" }; IosDivider()
                        IosRow("Restablecer distribución", destructive = true) { confirmation = "reset" }
                    }
                    Text("La copia conserva los iconos, carpetas y apariencia. Las fotos y widgets de otras apps se añaden de nuevo en cada dispositivo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(17.dp))
                }
                else -> {
                    IosGroup { IosRow("Versión", BuildConfig.VERSION_NAME, onClick = {}); IosDivider(); IosRow("Guardar diagnóstico", onClick = onDiagnostics) }
                    Text("Un inicio inspirado en iOS y Folio, con tus aplicaciones Android. Sin cuentas, anuncios ni telemetría.",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(17.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(if (action == "reset") "¿Restablecer Inicio?" else "¿Importar escritorio?") },
            text = { Text("Se sustituirá la distribución actual. Puedes exportarla primero para conservar una copia.") },
            confirmButton = { TextButton(onClick = { confirmation = null; if (action == "reset") onReset() else onImport() }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } })
    }
}
