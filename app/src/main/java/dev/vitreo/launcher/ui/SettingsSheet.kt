package dev.vitreo.launcher.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.BuildConfig
import dev.vitreo.launcher.LauncherViewModel
import dev.vitreo.launcher.core.Appearance
import kotlin.math.roundToInt

@Composable
private fun ToggleRow(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp), fontSize = 15.sp)
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 22.dp, bottom = 9.dp), color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
fun SettingsSheet(
    ui: LauncherViewModel.UiState,
    defaultHome: Boolean,
    onClose: () -> Unit,
    onAppearance: (Appearance) -> Unit,
    onSetHome: () -> Unit,
    onPhoto: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDiagnostics: () -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onLicenses: () -> Unit,
) {
    val a = ui.layout.appearance
    var confirmation by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).verticalScroll(rememberScrollState()).padding(20.dp)) {
        SheetHeader("Ajustes", onClose, "settings_title")
        Text("Vitreo ${BuildConfig.VERSION_NAME}", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Launcher local \u00b7 versi\u00f3n experimental", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (BuildConfig.BUILD_TYPE == "preview") {
            Text("PREVIEW: esta APK usa una firma temporal de pruebas. Una APK de otro build puede exigir desinstalar esta versi\u00f3n. Exporta tu escritorio antes.",
                color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp)
        }
        SectionTitle("INICIO")
        SheetAction(if (defaultHome) "Vitreo ya es tu app de inicio" else "Elegir Vitreo como app de inicio", !defaultHome, onSetHome)
        SheetAction("Actualizar lista de aplicaciones", ui.ready, onRefresh)
        SectionTitle("CRISTAL")
        Text(if (Build.VERSION.SDK_INT >= 33) "Refracci\u00f3n AGSL sobre el fondo del launcher." else "Android 12: cristal esmerilado sin refracci\u00f3n AGSL.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.powerSaver) Text("El ahorro de bater\u00eda est\u00e1 activo: se usa el modo reducido.", fontSize = 13.sp)
        ToggleRow("Reducir efectos", a.reducedEffects, ui.ready) { onAppearance(a.copy(reducedEffects = it)) }
        ToggleRow("Paneles opacos (m\u00e1ximo contraste)", a.opaquePanels, ui.ready) { onAppearance(a.copy(opaquePanels = it)) }
        Text("Refracci\u00f3n: ${(a.refraction * 100).roundToInt()}%", fontSize = 14.sp)
        Slider(a.refraction, { onAppearance(a.copy(refraction = it)) },
            enabled = ui.ready && !a.reducedEffects && !a.opaquePanels && !ui.powerSaver && Build.VERSION.SDK_INT >= 33)
        Text("Esmerilado: ${(a.frost * 100).roundToInt()}%", fontSize = 14.sp)
        Slider(a.frost, { onAppearance(a.copy(frost = it)) }, enabled = ui.ready && !a.opaquePanels && !a.reducedEffects && !ui.powerSaver && Build.VERSION.SDK_INT >= 33)
        SectionTitle("ICONOS")
        ToggleRow("Mostrar nombres", a.showLabels, ui.ready) { onAppearance(a.copy(showLabels = it)) }
        Text("Tama\u00f1o solicitado: ${a.iconSize.roundToInt()} dp", fontSize = 14.sp)
        Slider(a.iconSize, { onAppearance(a.copy(iconSize = it)) }, valueRange = 40f..68f, steps = 13, enabled = ui.ready)
        Text("Cuatro columnas. El tama\u00f1o real se ajusta al ancho y al texto del sistema.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("FONDO")
        listOf("Oc\u00e9ano", "Atardecer", "Bosque", "Grafito").chunked(2).forEachIndexed { row, names ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                names.forEachIndexed { column, name ->
                    val index = row * 2 + column
                    OutlinedButton(onClick = { onAppearance(a.copy(palette = index, usePhoto = false)) }, enabled = ui.ready, modifier = Modifier.weight(1f)) {
                        Text((if (!a.usePhoto && a.palette == index) "\u2022 " else "") + name)
                    }
                }
            }
        }
        SheetAction("Elegir imagen local", ui.ready, onClick = onPhoto)
        Text("Se copia una imagen reducida al almacenamiento privado de Vitreo. No cambia el fondo de bloqueo ni lee toda tu galer\u00eda.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("COPIAS Y DIAGN\u00d3STICO")
        SheetAction("Exportar escritorio y ajustes", ui.ready, onExport)
        SheetAction("Importar una copia", ui.ready) { confirmation = "import" }
        SheetAction("Guardar informe de diagn\u00f3stico", onClick = onDiagnostics)
        SheetAction("Restablecer distribuci\u00f3n", ui.ready) { confirmation = "reset" }
        Text("La copia no contiene el fondo. El informe se guarda donde elijas y no se env\u00eda autom\u00e1ticamente. Rev\u00edsalo antes de compartirlo.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("ALCANCE 0.1.0")
        Text("Perfil Android actual. Sin widgets nativos, paquetes de iconos, espacio privado, perfiles de trabajo ni sustituci\u00f3n de Recientes. No modifiques el sistema para probarlo.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SheetAction("Licencias y procedencia", onClick = onLicenses)
        Spacer(Modifier.height(24.dp))
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(if (action == "reset") "\u00bfRestablecer escritorio?" else "\u00bfImportar una copia?") },
            text = { Text("Se sustituir\u00e1 la distribuci\u00f3n actual. Exporta una copia primero para poder volver atr\u00e1s. Las aplicaciones no se desinstalan.") },
            confirmButton = { TextButton(onClick = { confirmation = null; if (action == "reset") onReset() else onImport() }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } })
    }
}
