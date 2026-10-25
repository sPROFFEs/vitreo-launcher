package dev.vitreo.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord

@Composable
fun NewFolderSheet(apps: List<AppRecord>, onClose: () -> Unit, onCreate: (List<String>, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val records = remember(apps) { apps.associateBy { it.id } }
    val visible = remember(apps, query) { apps.filter { searchKey(query) in searchKey(it.label) }.sortedBy { searchKey(it.label) } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().testTag("new_folder")) {
        SheetHeader("Create Folder", onClose)
        OutlinedTextField(name, { name = it.take(48) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Folder name") }, singleLine = true)
        OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), label = { Text("Search apps") }, singleLine = true)
        Text("Choose at least two apps. You can also hold and drag an app over another on Home.", Modifier.padding(horizontal = 22.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp)) {
            items(visible, key = { it.id }) { app ->
                Row(Modifier.fillMaxWidth().testTag("folder_choice:${app.id}").clickable {
                    selected = if (app.id in selected) selected - app.id else if (selected.size < MAX_FOLDER_APPS) selected + app.id else selected
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EntryIcon(HomeEntry.App(app.id), records, 42.dp)
                    Text(app.label, Modifier.weight(1f).padding(horizontal = 14.dp))
                    Checkbox(app.id in selected, onCheckedChange = null)
                }
            }
        }
        Button(onClick = { onCreate(selected, name.trim().ifBlank { "Folder" }) }, enabled = selected.size >= 2,
            modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("confirm_create_folder")) { Text("Create Folder (${selected.size})") }
    }
}
