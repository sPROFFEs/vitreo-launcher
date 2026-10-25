package dev.vitreo.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SheetHeader(title: String, onClose: () -> Unit, tag: String = "") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).testTag(tag), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onClose) { Text("Done", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun SheetAction(text: String, enabled: Boolean = true, onClick: () -> Unit) = IosRow(text, enabled = enabled, onClick = onClick)

@Composable
fun FolderSheet(folder: HomeEntry.Folder, apps: Map<String, AppRecord>, onClose: () -> Unit,
    onLaunch: (String, androidx.compose.ui.geometry.Rect?) -> Unit, onActions: (String, androidx.compose.ui.geometry.Rect?) -> Unit, onRename: (String) -> Unit, iconSize: Float = LocalGlass.current.appearance.iconSize) {
    val available = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat(), 430f) - 80f
    val columns = appGridColumns(available, iconSize)
    val pageSize = columns * 3
    val count = pageCount(folder.apps.size, pageSize)
    val pager = rememberPagerState(pageCount = { count })
    var name by remember(folder.id) { mutableStateOf(folder.title) }
    Box(Modifier.fillMaxSize().clickable(onClick = onClose).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 430.dp).fillMaxWidth().padding(horizontal = 25.dp).clickable {}, horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(name, { name = it.take(48); onRename(name) }, singleLine = true,
                cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(color = Color.White, fontSize = 32.sp, textAlign = TextAlign.Center, fontWeight = if (LocalCyber.current) FontWeight.Medium else FontWeight.Normal),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("folder_name"))
            GlassPanel(Modifier.fillMaxWidth().heightIn(max = (3 * (iconSize + 42f) + 30f).dp).weight(1f, fill = false).testTag("folder_panel"), radius = 40f,
                material = GlassMaterial.Regular) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 5.dp)) {
                        items(folder.apps.drop(page * pageSize).take(pageSize), key = { it }) { id ->
                            LauncherTile(HomeEntry.App(id), apps, iconSize.dp, true,
                                onClick = { onLaunch(id, it) }, onActions = { onActions(id, it) })
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 15.dp)) { if (count > 1) repeat(count) { page ->
                Box(Modifier.padding(4.dp).size(6.dp).background(Color.White.copy(alpha = if (page == pager.currentPage) 1f else .35f), CircleShape))
            } }
        }
    }
}

@Composable
fun ChoiceSheet(title: String, choices: List<Pair<String, String>>, onClose: () -> Unit, onChoose: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        SheetHeader(title, onClose)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            IosGroup { choices.forEach { (id, label) -> key(id) { IosRow(label) { onChoose(id) }; IosDivider() } } }
        }
    }
}

@Composable
fun LicensesSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("") {
        value = withContext(Dispatchers.IO) { context.assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() } }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        SheetHeader("Licenses", onClose)
        Text(text, Modifier.padding(20.dp).verticalScroll(rememberScrollState()), fontSize = 13.sp)
    }
}
