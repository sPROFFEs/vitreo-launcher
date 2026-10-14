package dev.vitreo.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SheetHeader(title: String, onClose: () -> Unit, tag: String = "sheet_title") {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).testTag(tag), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar") }
    }
}

@Composable
fun SheetAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

@Composable
fun LibrarySheet(records: List<AppRecord>, apps: Map<String, AppRecord>, onClose: () -> Unit,
                 onLaunch: (String) -> Unit, onActions: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val filtered = remember(records, query) {
        val key = searchKey(query)
        records.filter { key.isEmpty() || key in searchKey(it.label) || key in searchKey(it.packageName) }
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(18.dp)) {
        SheetHeader("Aplicaciones", onClose, "library_title")
        OutlinedTextField(query, { query = it.take(100) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search_field"),
            label = { Text("Nombre o paquete") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { filtered.firstOrNull()?.let { onLaunch(it.id) } }))
        Text("${filtered.size} apps \u00b7 B\u00fasqueda local", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
        if (filtered.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Sin resultados") }
        else LazyVerticalGrid(GridCells.Fixed(4), modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { app ->
                LauncherTile(HomeEntry.App(app.id), apps, 54.dp, true, modifier = Modifier.heightIn(min = 91.dp),
                    onClick = { onLaunch(app.id) }, onActions = { onActions(app.id) })
            }
        }
    }
    // Search is ready for a tap, but does not automatically shrink the library with the IME.
}

@Composable
fun FolderSheet(folder: HomeEntry.Folder, apps: Map<String, AppRecord>, onClose: () -> Unit,
                onLaunch: (String) -> Unit, onActions: (String) -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(18.dp)) {
        SheetHeader(folder.title, onClose)
        TextButton(onClick = onEdit) { Text("Editar carpeta") }
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().heightIn(max = 370.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folder.apps, key = { it }) { id ->
                LauncherTile(HomeEntry.App(id), apps, 54.dp, true, Modifier.heightIn(min = 92.dp),
                    onClick = { onLaunch(id) }, onActions = { onActions(id) })
            }
        }
    }
}

@Composable
fun ActionsSheet(
    entry: HomeEntry, state: LauncherState, apps: Map<String, AppRecord>, onClose: () -> Unit,
    onHome: (String) -> Unit, onDock: (String) -> Unit, onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit, onInfo: (String) -> Unit, onPair: (String) -> Unit,
    onChooseFolder: (String) -> Unit, onRename: (String, String) -> Unit, onDissolve: (String) -> Unit,
) {
    val index = state.home.indexOfFirst { it.id == entry.id }
    Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(18.dp)) {
        SheetHeader(entryLabel(entry, apps), onClose)
        if (index >= 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onMove(entry.id, -1) }, enabled = index > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ChevronLeft, null); Text("Anterior")
                }
                OutlinedButton(onClick = { onMove(entry.id, 1) }, enabled = index < state.home.lastIndex, modifier = Modifier.weight(1f)) {
                    Text("Siguiente"); Icon(Icons.Outlined.ChevronRight, null)
                }
            }
            Text("Tambi\u00e9n mueve elementos entre p\u00e1ginas.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (entry) {
            is HomeEntry.App -> {
                if (state.home.none { it is HomeEntry.App && it.id == entry.id }) SheetAction("Mover al escritorio") { onHome(entry.id) }
                if (entry.id !in state.dock) SheetAction("Fijar en dock (${state.dock.size}/4)", state.dock.size < DOCK_SLOTS) { onDock(entry.id) }
                if (entry.id in state.placedApps()) SheetAction("Quitar acceso directo (no desinstala)") { onRemove(entry.id) }
                if (index >= 0 && state.home.filterIsInstance<HomeEntry.App>().size >= 2) SheetAction("Crear carpeta con otra app") { onPair(entry.id) }
                if (state.home.filterIsInstance<HomeEntry.Folder>().any { entry.id !in it.apps && it.apps.size < MAX_FOLDER_APPS }) SheetAction("Mover a carpeta existente") { onChooseFolder(entry.id) }
                SheetAction("Informaci\u00f3n de la aplicaci\u00f3n", entry.id in apps) { onInfo(entry.id) }
            }
            is HomeEntry.Folder -> {
                var title by remember(entry.id) { mutableStateOf(entry.title) }
                OutlinedTextField(title, { title = it.take(48) }, label = { Text("Nombre de carpeta") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
                SheetAction("Guardar nombre") { onRename(entry.id, title) }
                SheetAction("Deshacer carpeta y conservar sus apps") { onDissolve(entry.id) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ChoiceSheet(title: String, choices: List<Pair<String, String>>, onClose: () -> Unit, onChoose: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 550.dp).verticalScroll(rememberScrollState()).padding(18.dp)) {
        SheetHeader(title, onClose)
        if (choices.isEmpty()) Text("No hay opciones disponibles.", Modifier.padding(16.dp))
        choices.forEach { (id, label) -> key(id) { SheetAction(label) { onChoose(id) } } }
    }
}

@Composable
fun LicensesSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("Cargando licencias...") {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() } }
                .getOrDefault("Licencias incluidas en LICENSE y THIRD_PARTY_NOTICES.md del repositorio.")
        }
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(18.dp)) {
        SheetHeader("Licencias y procedencia", onClose)
        Text(text, Modifier.weight(1f).verticalScroll(rememberScrollState()), fontSize = 12.sp)
    }
}
