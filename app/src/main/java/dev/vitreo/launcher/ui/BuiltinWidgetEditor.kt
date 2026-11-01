package dev.vitreo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.data.AppRecord

@Composable
internal fun BuiltinWidgetEditor(
    kind: String,
    appearance: Appearance,
    apps: Map<String, AppRecord>,
    onAppearance: (Appearance) -> Unit,
    onPickApp: (String) -> Unit,
    onClose: () -> Unit,
) {
    val isClock = kind == "clock"
    val title = if (isClock) "Clock Widget" else "Calendar Widget"
    val now = LocalHomeClock.current
    val currentAppId = if (isClock) appearance.clockApp else appearance.calendarApp
    val currentAppName = apps[currentAppId]?.label ?: currentAppId.takeIf { it.isNotEmpty() } ?: "Default / Auto"
    val isShownOnHome = if (isClock) appearance.showClockWidget && appearance.showWidgets else appearance.showCalendarWidget && appearance.showWidgets

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("builtin_widget_editor")) {
        SheetHeader(title, onClose, "builtin_widget_title")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Live Widget Preview
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(165.dp, 165.dp)) {
                    if (isClock) {
                        ClockWidgetCard(now = now, modifier = Modifier.fillMaxSize(), onClick = {})
                    } else {
                        CalendarWidgetCard(now = now, modifier = Modifier.fillMaxSize(), onClick = {})
                    }
                }
            }

            IosSectionLabel("Linked Application")
            IosGroup {
                IosRow("Open when tapped", currentAppName) { onPickApp(kind) }
                if (currentAppId.isNotEmpty()) {
                    IosDivider()
                    IosRow("Reset to Default App", destructive = true) {
                        if (isClock) onAppearance(appearance.copy(clockApp = ""))
                        else onAppearance(appearance.copy(calendarApp = ""))
                    }
                }
            }
            Text(
                "Tapping this widget on the Home screen or Today will open this app. By default, Vitreo automatically detects your installed clock or calendar app.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            IosSectionLabel("Home Screen")
            IosGroup {
                IosToggleRow("Show on Home Screen", isShownOnHome) { enabled ->
                    if (isClock) {
                        val newShowWidgets = enabled || appearance.showCalendarWidget
                        onAppearance(appearance.copy(showClockWidget = enabled, showWidgets = newShowWidgets))
                    } else {
                        val newShowWidgets = enabled || appearance.showClockWidget
                        onAppearance(appearance.copy(showCalendarWidget = enabled, showWidgets = newShowWidgets))
                    }
                }
            }

            IosSectionLabel("Widget Actions")
            IosGroup {
                IosRow("Remove from Home", destructive = true) {
                    if (isClock) {
                        val newShowWidgets = appearance.showCalendarWidget
                        onAppearance(appearance.copy(showClockWidget = false, showWidgets = newShowWidgets))
                    } else {
                        val newShowWidgets = appearance.showClockWidget
                        onAppearance(appearance.copy(showCalendarWidget = false, showWidgets = newShowWidgets))
                    }
                    onClose()
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun AppPickerForWidgetSheet(
    kind: String,
    allApps: List<AppRecord>,
    appsMap: Map<String, AppRecord>,
    currentAppId: String,
    onSelectApp: (String) -> Unit,
    onClose: () -> Unit,
) {
    val isClock = kind == "clock"
    val title = if (isClock) "Clock App" else "Calendar App"
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(allApps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allApps.sortedBy { it.label.lowercase() }
        else allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            .sortedBy { it.label.lowercase() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("widget_app_picker")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Back") }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(60.dp))
        }

        // Search Bar
        IosGroup(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                LauncherSymbol(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    modifier = Modifier.weight(1f).padding(start = 10.dp).testTag("app_search_query"),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search installed apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                )
            }
        }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                IosSectionLabel("System Default")
                IosGroup {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onSelectApp(""); onClose() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic / System Default", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Detects installed ${if (isClock) "clock or alarms" else "calendar"} app automatically",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (currentAppId.isEmpty()) {
                            LauncherSymbol(Icons.Outlined.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                IosSectionLabel("Installed Applications")
            }

            items(filtered, key = { it.id }) { app ->
                val isSelected = currentAppId == app.id || currentAppId == app.packageName
                IosGroup {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onSelectApp(app.id); onClose() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EntryIcon(entry = HomeEntry.App(app.id), apps = appsMap, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isSelected) {
                            LauncherSymbol(Icons.Outlined.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
