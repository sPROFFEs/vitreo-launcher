package dev.vitreo.launcher.ui

import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.data.AppRecord
import dev.vitreo.launcher.data.WidgetController
import java.util.Date

@Composable
fun TodayPage(now: Long, widgets: WidgetController, suggestions: List<AppRecord>, apps: Map<String, AppRecord>,
    onSearch: () -> Unit, onSettings: () -> Unit, onWidgets: () -> Unit, onClock: () -> Unit,
    onCalendar: () -> Unit, onLaunch: (String, androidx.compose.ui.geometry.Rect?) -> Unit, onEditWidget: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 21.dp).testTag("today_page")) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = LocalGlassContentColor.current)
            IconButton(onClick = onWidgets) { LauncherSymbol(Icons.Outlined.Add, "Add Widgets", tint = LocalGlassContentColor.current) }
            IconButton(onClick = onSettings, modifier = Modifier.testTag("settings_button")) { LauncherSymbol(Icons.Outlined.Settings, "Settings", tint = LocalGlassContentColor.current) }
        }
        GlassPanel(Modifier.fillMaxWidth().height(47.dp).clickable(onClick = onSearch), radius = 22f, tint = .13f) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                LauncherSymbol(Icons.Outlined.Search, null, Modifier.size(21.dp), tint = Color.White.copy(alpha = .8f))
                Text("Search", color = Color.White.copy(alpha = .7f), fontSize = 17.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(17.dp))
        HomeWidgets(now, Modifier.height(207.dp), onClock, onCalendar)
        GlassPanel(Modifier.fillMaxWidth(), radius = 28f, tint = .12f) {
            Column(Modifier.padding(14.dp)) {
                Text("Suggestions", color = Color.White.copy(alpha = .8f), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    suggestions.take(4).forEach { app ->
                        LauncherTile(HomeEntry.App(app.id), apps, 53.dp, true, Modifier.weight(1f), onClick = { onLaunch(app.id, it) }, onActions = {})
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        widgets.todayIds.forEach { id -> androidx.compose.runtime.key(id) { NativeWidget(widgets, id) { onEditWidget(id) }; Spacer(Modifier.height(10.dp)) } }
        TextButton(onClick = onWidgets, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 15.dp)) {
            Text("Add Widgets", color = Color.White, fontSize = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ControlTile(title: String, icon: ImageVector, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    GlassPanel(modifier.height(107.dp).clickable(onClick = onClick), radius = 28f, tint = .24f, interactive = true) {
        if (active) Box(Modifier.fillMaxSize().background((if (LocalCyber.current) CyberColors.Red else Color(0xff007aff)).copy(alpha = .7f)))
        Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
            LauncherSymbol(icon, null, Modifier.size(29.dp), tint = Color.White)
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ControlCenter(dark: Boolean, onClose: () -> Unit, onSystem: (String) -> Unit,
    onDark: (Boolean) -> Unit, onSettings: () -> Unit, onWidgets: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp).testTag("control_center")) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateFormat.getTimeFormat(context).format(Date()), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
                Text("Control Center", color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
            }
            IconButton(onClick = onClose) { LauncherSymbol(Icons.Outlined.Close, "Close", tint = Color.White) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ControlTile("Internet", Icons.Outlined.Wifi, Modifier.weight(1f)) { onSystem(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
            ControlTile("Bluetooth", Icons.Outlined.Bluetooth, Modifier.weight(1f)) { onSystem(Settings.ACTION_BLUETOOTH_SETTINGS) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ControlTile("Sound", Icons.Outlined.VolumeUp, Modifier.weight(1f)) { onSystem(Settings.Panel.ACTION_VOLUME) }
            ControlTile("Display", Icons.Outlined.BrightnessMedium, Modifier.weight(1f)) { onSystem(Settings.ACTION_DISPLAY_SETTINGS) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (LocalCyber.current) ControlTile("HUD Effects", Icons.Outlined.Tune, Modifier.weight(1f), active = LocalGlass.current.appearance.cyberEffects) { onDark(!dark) }
            else ControlTile("Dark Mode", Icons.Outlined.DarkMode, Modifier.weight(1f), active = dark) { onDark(!dark) }
            ControlTile("Widgets", Icons.Outlined.Widgets, Modifier.weight(1f), onClick = onWidgets)
        }
        Spacer(Modifier.height(14.dp))
        ControlTile("Vitreo Settings", Icons.Outlined.Settings, Modifier.fillMaxWidth(), onClick = onSettings)
        Spacer(Modifier.height(30.dp))
    }
}
