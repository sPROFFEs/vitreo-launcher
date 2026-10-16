@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.vitreo.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.AppRecord

@Composable
fun Spotlight(records: List<AppRecord>, apps: Map<String, AppRecord>, suggestions: List<AppRecord>,
    onClose: () -> Unit, onLaunch: (String, Rect?) -> Unit, onActions: (String, Rect?) -> Unit, alphabetical: Boolean = false) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, records, suggestions, alphabetical) {
        val key = searchKey(query)
        val source = if (alphabetical) records.sortedBy { searchKey(it.label) } else suggestions.take(8)
        if (key.isEmpty()) source
        else records.filter { key in searchKey(it.label) || key in searchKey(it.packageName) }.sortedBy { searchKey(it.label) }
    }
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)).safeDrawingPadding()
        .imePadding().padding(horizontal = 18.dp).testTag("library_title")) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassPanel(Modifier.weight(1f).height(49.dp), radius = 19f, tint = .32f) {
                Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(21.dp), tint = Color.White.copy(alpha = .8f))
                    BasicTextField(query, { query = it.take(100) }, Modifier.weight(1f).padding(start = 9.dp)
                        .focusRequester(focus).testTag("search_field"), singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp), cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { onLaunch(it.id, null) } }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search", color = Color.White.copy(alpha = .6f), fontSize = 18.sp)
                            inner()
                        })
                    if (query.isNotEmpty()) Icon(Icons.Outlined.Cancel, "Clear Search",
                        Modifier.size(20.dp).clickable { query = "" }, tint = Color.White.copy(alpha = .6f))
                }
            }
            Text("Cancel", color = Color.White, fontSize = 17.sp,
                modifier = Modifier.clickable(onClick = onClose).padding(start = 13.dp, top = 12.dp, bottom = 12.dp))
        }
        Text(if (query.isBlank() && !alphabetical) "Suggestions" else "Apps", color = Color.White.copy(alpha = .7f),
            fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 9.dp, bottom = 10.dp))
        if (results.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            Text("No Results", color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(top = 40.dp))
        } else if (alphabetical) AlphabeticalApps(results, apps, onLaunch, onActions)
        else GlassPanel(Modifier.fillMaxWidth().heightIn(max = 370.dp), radius = 27f, material = GlassMaterial.Regular) {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(results, key = { it.id }) { app ->
                    LauncherTile(HomeEntry.App(app.id), apps, 53.dp, true, Modifier.height(92.dp).testTag("search_entry:${app.id}"),
                        onClick = { onLaunch(app.id, it) }, onActions = { onActions(app.id, it) })
                }
            }
        }
        if (!alphabetical || results.isEmpty()) Spacer(Modifier.weight(1f))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun ColumnScope.AlphabeticalApps(records: List<AppRecord>, apps: Map<String, AppRecord>,
    onLaunch: (String, Rect?) -> Unit, onActions: (String, Rect?) -> Unit) {
    val groups = remember(records) { records.groupBy { searchKey(it.label).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#" }.toSortedMap() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    GlassPanel(Modifier.fillMaxWidth().weight(1f).testTag("alphabetical_apps"), radius = 27f, material = GlassMaterial.Regular) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(12.dp)) {
                groups.forEach { (letter, group) ->
                    item(key = "header:$letter") {
                        Text(letter, Modifier.padding(start = 10.dp, top = 12.dp, bottom = 7.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                    items(group, key = { it.id }) { app ->
                        var bounds by remember { mutableStateOf<Rect?>(null) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag("alphabetical_entry:${app.id}")
                            .combinedClickable(onClick = { onLaunch(app.id, bounds) }, onLongClick = { onActions(app.id, bounds) })
                            .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            EntryIcon(HomeEntry.App(app.id), apps, 43.dp, Modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
                            Text(app.label, Modifier.padding(start = 13.dp), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Column(Modifier.width(29.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                groups.keys.forEach { letter ->
                    Text(letter, Modifier.clickable {
                        val index = groups.entries.takeWhile { it.key != letter }.sumOf { it.value.size + 1 }
                        scope.launch { list.animateScrollToItem(index) }
                    }.padding(vertical = 2.dp, horizontal = 7.dp), color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AppLibrary(records: List<AppRecord>, apps: Map<String, AppRecord>, suggestions: List<AppRecord>,
    onSearch: () -> Unit, onLaunch: (String, Rect?) -> Unit, onActions: (String, Rect?) -> Unit,
    onCategory: (String, List<AppRecord>, Rect?) -> Unit) {
    val groups = remember(records, suggestions) {
        listOf("Suggestions" to suggestions.take(8)) + records.groupBy { appGroup(it.label, it.packageName, it.category) }
            .toSortedMap().toList()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp).testTag("app_library")) {
        GlassPanel(Modifier.fillMaxWidth().height(51.dp).clickable(onClick = onSearch), radius = 24f, tint = .2f) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = .8f))
                Text("App Library", color = Color.White.copy(alpha = .85f), fontSize = 18.sp, modifier = Modifier.padding(start = 9.dp))
            }
        }
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f).fillMaxWidth().padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(groups, key = { it.first }) { (title, group) ->
                var cardBounds by remember { mutableStateOf<Rect?>(null) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassPanel(Modifier.fillMaxWidth().aspectRatio(1f).onGloballyPositioned { cardBounds = it.boundsInRoot() }.testTag("library_category:$title")
                        .clickable { onCategory(title, group, cardBounds) }, radius = 29f, tint = .26f) {
                        Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                            repeat(2) { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    repeat(2) { column ->
                                        val index = row * 2 + column
                                        val app = group.getOrNull(index)
                                        if (app == null) Spacer(Modifier.size(53.dp))
                                        else if (index == 3 && group.size > 4) {
                                            Column(Modifier.size(53.dp).clickable { onCategory(title, group, cardBounds) }, verticalArrangement = Arrangement.SpaceEvenly) {
                                                repeat(2) { miniRow ->
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                        repeat(2) { miniColumn -> group.getOrNull(3 + miniRow * 2 + miniColumn)?.let {
                                                            EntryIcon(HomeEntry.App(it.id), apps, 22.dp)
                                                        } ?: Spacer(Modifier.size(22.dp)) }
                                                    }
                                                }
                                            }
                                        } else LauncherTile(HomeEntry.App(app.id), apps, 53.dp, false, Modifier.size(53.dp, 65.dp),
                                            onClick = { onLaunch(app.id, it) }, onActions = { onActions(app.id, it) })
                                    }
                                }
                            }
                        }
                    }
                    Text(title, color = LocalGlassContentColor.current, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
fun CategoryScreen(title: String, records: List<AppRecord>, apps: Map<String, AppRecord>,
    onClose: () -> Unit, onLaunch: (String, Rect?) -> Unit, onActions: (String, Rect?) -> Unit) {
    val columns = ((LocalConfiguration.current.screenWidthDp - 68) / 76).coerceAtLeast(1)
    val rows = (records.size + columns - 1) / columns
    val panelHeight = rows * (60f + 20f * LocalDensity.current.fontScale + 12f) + (rows - 1).coerceAtLeast(0) * 12f + 40f
    Column(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose).safeDrawingPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
        }
        Text("${records.size} apps", color = Color.White.copy(alpha = .8f), fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 20.dp))
        GlassPanel(Modifier.fillMaxWidth().heightIn(max = panelHeight.dp).weight(1f, fill = false).testTag("category_panel"),
            radius = 34f, material = GlassMaterial.Regular) {
            LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(records, key = { it.id }) { app ->
                    LauncherTile(HomeEntry.App(app.id), apps, 60.dp, true, Modifier.testTag("category_entry:${app.id}"),
                        onClick = { onLaunch(app.id, it) }, onActions = { onActions(app.id, it) })
                }
            }
        }
    }
}
