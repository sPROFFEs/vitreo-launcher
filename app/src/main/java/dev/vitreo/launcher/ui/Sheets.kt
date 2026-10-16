package dev.vitreo.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
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
    onLaunch: (String, androidx.compose.ui.geometry.Rect?) -> Unit, onActions: (String, androidx.compose.ui.geometry.Rect?) -> Unit, onRename: (String) -> Unit) {
    val count = pageCount(folder.apps.size, 9)
    val pager = rememberPagerState(pageCount = { count })
    var name by remember(folder.id) { mutableStateOf(folder.title) }
    Box(Modifier.fillMaxSize().clickable(onClick = onClose).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 370.dp).fillMaxWidth().padding(horizontal = 25.dp).clickable {}, horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(name, { name = it.take(48); onRename(name) }, singleLine = true,
                cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(color = Color.White, fontSize = 30.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("folder_name"))
            GlassPanel(Modifier.fillMaxWidth().aspectRatio(.95f).testTag("folder_panel"), radius = 40f,
                material = GlassMaterial.Regular) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        repeat(3) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                repeat(3) { column ->
                                    val id = folder.apps.getOrNull(page * 9 + row * 3 + column)
                                    if (id == null) Spacer(Modifier.weight(1f).height(86.dp))
                                    else LauncherTile(HomeEntry.App(id), apps, 58.dp, true, Modifier.weight(1f),
                                        onClick = { onLaunch(id, it) }, onActions = { onActions(id, it) })
                                }
                            }
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
