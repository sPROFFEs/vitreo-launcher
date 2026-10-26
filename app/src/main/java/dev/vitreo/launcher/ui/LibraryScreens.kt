@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.vitreo.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
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
    query: String, onQueryChange: (String) -> Unit,
    onClose: () -> Unit, onLaunch: (String, Rect?) -> Unit, onActions: (String, Rect?) -> Unit,
    alphabetical: Boolean = false, active: Boolean = true) {
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
            GlassPanel(Modifier.weight(1f).height(49.dp), radius = if (LocalGlass.current.appearance.iosClassic) 12f else 25f, tint = .24f, contentColor = Color.White) {
                Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    LauncherSymbol(Icons.Outlined.Search, null, Modifier.size(21.dp), tint = Color.White.copy(alpha = .8f))
                    BasicTextField(query, { onQueryChange(it.take(100)) }, Modifier.weight(1f).padding(start = 9.dp)
                        .focusRequester(focus).testTag("search_field"), singleLine = true, enabled = active,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp), cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { onLaunch(it.id, null) } }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search", color = Color.White.copy(alpha = .6f), fontSize = 18.sp)
                            inner()
                        })
                    if (query.isNotEmpty()) LauncherSymbol(Icons.Outlined.Cancel, "Clear Search",
                        Modifier.size(20.dp).clickable { onQueryChange("") }, tint = Color.White.copy(alpha = .6f))
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
        else GlassPanel(Modifier.fillMaxWidth().heightIn(max = 470.dp), radius = 27f, material = GlassMaterial.Regular) {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(results, key = { it.id }) { app ->
                    val icon = libraryIconSize(LocalGlass.current.appearance, LocalConfiguration.current.screenWidthDp.toFloat()).dp
                    LauncherTile(HomeEntry.App(app.id), apps, minOf(icon, ((LocalConfiguration.current.screenWidthDp - 64) / 4f - 8).dp), true, Modifier.height(icon + 40.dp).testTag("search_entry:${app.id}"),
                        onClick = { onLaunch(app.id, it) }, onActions = { onActions(app.id, it) })
                }
            }
        }
        if (!alphabetical || results.isEmpty()) Spacer(Modifier.weight(1f))
    }
    LaunchedEffect(active) { if (active) focus.requestFocus() }
    // LauncherScreen owns IME cleanup. An outgoing field can be disposed after a
    // new search/editor has opened; hiding the window-wide IME here closes that session.
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
                            EntryIcon(HomeEntry.App(app.id), apps, libraryIconSize(LocalGlass.current.appearance, LocalConfiguration.current.screenWidthDp.toFloat()).dp, Modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
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
    val ink = LocalGlassContentColor.current
    val cyber = LocalCyber.current
    val groups = remember(records, suggestions) {
        listOf("Suggestions" to suggestions.take(8)) + records.groupBy { appGroup(it.label, it.packageName, it.category) }
            .toSortedMap().toList()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = (if (cyber) 18f else IosDesign.HomeInset).dp, vertical = 12.dp).testTag("app_library")) {
        GlassPanel(Modifier.fillMaxWidth().height(IosDesign.SearchHeight.dp).clickable(onClick = onSearch),
            radius = if (LocalGlass.current.appearance.iosClassic) 12f else 25f, tint = .16f, interactive = true) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                LauncherSymbol(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = ink.copy(alpha = .8f))
                Text("App Library", color = ink.copy(alpha = .85f), fontSize = 18.sp, modifier = Modifier.padding(start = 9.dp))
            }
        }
        val categoryColumns = if (LocalConfiguration.current.screenWidthDp < 300) 1 else 2
        LazyVerticalGrid(GridCells.Fixed(categoryColumns), Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(groups, key = { it.first }) { (title, group) ->
                var cardBounds by remember { mutableStateOf<Rect?>(null) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassPanel(Modifier.fillMaxWidth().aspectRatio(1f).onGloballyPositioned { cardBounds = it.boundsInRoot() }.testTag("library_category:$title")
                        .clickable { onCategory(title, group, cardBounds) }, radius = if (cyber) 29f else IosDesign.CardRadius, tint = .18f, interactive = true) {
                        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
                            val cell = minOf(maxWidth / 2, maxHeight / 2)
                            val desired = libraryIconSize(LocalGlass.current.appearance, LocalConfiguration.current.screenWidthDp.toFloat())
                            val base = (cell - 14.dp).coerceIn(24.dp, 56.dp)
                            val icon = (base * (desired / 60f)).coerceIn(24.dp, (cell - 4.dp).coerceAtLeast(24.dp))
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                                repeat(2) { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        repeat(2) { column ->
                                            val index = row * 2 + column
                                            val app = group.getOrNull(index)
                                            if (app == null) Spacer(Modifier.size(cell))
                                            else if (index == 3 && group.size > 4) Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                                Column(Modifier.size(icon).clickable { onCategory(title, group, cardBounds) }, verticalArrangement = Arrangement.SpaceEvenly) {
                                                    repeat(2) { miniRow ->
                                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                            repeat(2) { miniColumn ->
                                                                val mini = group.getOrNull(3 + miniRow * 2 + miniColumn)
                                                                val size = (icon - 6.dp) / 2
                                                                if (mini == null) Spacer(Modifier.size(size))
                                                                else EntryIcon(HomeEntry.App(mini.id), apps, size)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else LauncherTile(HomeEntry.App(app.id), apps, icon, false, Modifier.size(cell),
                                                onClick = { onLaunch(app.id, it) }, onActions = { onActions(app.id, it) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(title, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(span = { GridItemSpan(categoryColumns) }) { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
fun CategoryScreen(title: String, records: List<AppRecord>, apps: Map<String, AppRecord>,
    onClose: () -> Unit, onLaunch: (String, Rect?) -> Unit, onActions: (String, Rect?) -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val icon = libraryIconSize(LocalGlass.current.appearance, screenWidth.toFloat())
    val columns = appGridColumns(screenWidth - 68f, icon, if (screenWidth < 600) 3 else 6)
    val rows = (records.size + columns - 1) / columns
    val panelHeight = rows * (icon + 20f * LocalDensity.current.fontScale + 12f) + (rows - 1).coerceAtLeast(0) * 12f + 40f
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
            Text(title, Modifier.padding(horizontal = 44.dp), color = Color.White, fontSize = 30.sp,
                fontWeight = if (LocalCyber.current) FontWeight.Bold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) { LauncherSymbol(Icons.Outlined.Close, "Close", tint = Color.White) }
        }
        Text("${records.size} apps", color = Color.White.copy(alpha = .7f), fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 24.dp))
        GlassPanel(Modifier.fillMaxWidth().heightIn(max = panelHeight.dp).weight(1f, fill = false).testTag("category_panel"),
            radius = 34f, material = GlassMaterial.Regular) {
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(records, key = { it.id }) { app ->
                    LauncherTile(HomeEntry.App(app.id), apps, icon.dp, true, Modifier.testTag("category_entry:${app.id}"),
                        onClick = { onLaunch(app.id, it) }, onActions = { onActions(app.id, it) })
                }
            }
        }
    }
}
