package dev.vitreo.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.HomePanel
import dev.vitreo.launcher.core.homePanelAt
import dev.vitreo.launcher.data.SystemBridge

@Composable
internal fun SystemIntegrationSettings(a: Appearance, onAppearance: (Appearance) -> Unit) {
    val context = LocalContext.current
    val bridge by SystemBridge.state.collectAsStateWithLifecycle()
    IosSectionLabel("Home integration · optional")
    IosGroup {
        IosToggleRow("Vitreo status bar on Home", a.homeStatusBar, modifier = Modifier.testTag("home_status_switch")) {
            onAppearance(a.copy(homeStatusBar = it))
        }
        IosDivider()
        IosToggleRow("Notification Center", a.notificationCenter, modifier = Modifier.testTag("notification_center_switch")) {
            onAppearance(a.copy(notificationCenter = it))
        }
        IosDivider()
        IosToggleRow("Connect media player", a.mediaIntegration, modifier = Modifier.testTag("media_integration_switch")) {
            onAppearance(a.copy(mediaIntegration = it))
        }
    }
    IntegrationFootnote("The status bar replaces Android's bar inside Home only. Battery and network connection reflect your device; cellular signal strength is not requested. Other apps, the lock screen and Recents keep Android's interface. Swipe from the very top to reveal Android's bar temporarily.")
    if (a.notificationCenter || a.mediaIntegration) {
        IosSectionLabel("Notification access")
        IosGroup {
            IosRow("Android notification access", if (bridge.access) "Allowed" else "Not allowed") {
                runCatching { context.startActivity(SystemBridge.accessIntent(context)) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
            }
        }
        IntegrationFootnote("Android asks you to authorize access to notification content. Vitreo displays alerts only when Notification Center is enabled, and reads active media sessions only when Connect media player is enabled. Content stays in memory on this device; it is never saved, exported or sent anywhere. Turn either feature off to clear its data; revoke access here to disconnect entirely. Some protected notifications and work profiles are restricted by Android.")
    }
    IosSectionLabel("Gesture area")
    IosGroup {
        Column(Modifier.padding(17.dp)) {
            Text("Top area · ${a.panelGestureHeight.toInt()} pt", fontSize = 15.sp)
            IosSlider(a.panelGestureHeight, { onAppearance(a.copy(panelGestureHeight = it)) }, valueRange = 64f..180f,
                modifier = Modifier.testTag("panel_gesture_height"))
            GestureAreaPreview(a)
        }
    }
    IntegrationFootnote("Start below Android's status bar, or just inside Home when using Vitreo's bar. Right 38%: Control Center. Left 62%: Notification Center. Swipe down at least 60 pt, or flick, to open; reverse a short pull to cancel. Swipe up from a panel's bottom handle to close. Elsewhere on Home, swipe down for Search. Drag inside the diagram to test the zones.")
}

@Composable
private fun IntegrationFootnote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp))
}

@Composable
private fun GestureAreaPreview(a: Appearance) {
    var result by remember { mutableStateOf("Try a downward drag here") }
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).testTag("panel_gesture_preview")
        .pointerInput(a.controlCenter, a.notificationCenter) {
            var panel: HomePanel? = null
            detectDragGestures(onDragStart = {
                panel = homePanelAt(it.x / size.width, it.y / density, 0f, 124f, a.controlCenter, a.notificationCenter)
            }, onDrag = { change, amount ->
                change.consume()
                if (amount.y > 0) result = when (panel) { HomePanel.Controls -> "Control Center"; HomePanel.Notifications -> "Notification Center"; else -> "This panel is off" }
            })
        }) {
        Row(Modifier.fillMaxWidth().height(65.dp)) {
            Box(Modifier.weight(.62f).fillMaxHeight().background(Color(0xff6484e5).copy(alpha = if (a.notificationCenter) .3f else .08f)), contentAlignment = Alignment.Center) {
                Text("Notifications", fontSize = 12.sp)
            }
            Box(Modifier.weight(.38f).fillMaxHeight().background(Color(0xff46b6d5).copy(alpha = if (a.controlCenter) .3f else .08f)), contentAlignment = Alignment.Center) {
                Text("Controls", fontSize = 12.sp)
            }
        }
        Text(result, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp), fontSize = 12.sp)
    }
}
