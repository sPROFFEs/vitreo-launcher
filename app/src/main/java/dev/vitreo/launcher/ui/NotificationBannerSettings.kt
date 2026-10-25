package dev.vitreo.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.data.NotificationSettingsNavigation
import dev.vitreo.launcher.data.SystemBridge

@Composable
internal fun NotificationBannerSettings() {
    val context=LocalContext.current
    val bridge by SystemBridge.state.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    val categories=remember(bridge.notices) { bridge.notices.filter { it.packageName!=context.packageName && it.channelId.isNotEmpty() }
        .distinctBy { it.packageName to it.channelId }.take(40) }
    BannerFootnote("Keep notifications enabled and use Vitreo's island for the floating alert. Android controls its own banners separately for each notification category.")
    IosSectionLabel("On Pixel & GrapheneOS")
    IosGroup {
        Text("1. Open an app's notification category.\n\n2. Leave the app and category enabled.\n\n3. Turn off ‘Pop on screen’ (sometimes ‘Show as pop-up’).\n\n4. Keep Alerting selected if you want sound and vibration.",
            Modifier.padding(17.dp),fontSize=15.sp)
    }
    BannerFootnote("The notification stays in Android's panel and can still reach the island. Choosing Silent also mutes it; turning the category off stops it reaching Vitreo. Android does not give launchers a general switch to disable other apps' banners.")
    IosGroup {
        IosRow("All app notification settings",onClick={failed=!NotificationSettingsNavigation.open(context)})
    }
    if(failed) BannerFootnote("Open Android Settings → Notifications → App notifications, choose an app, then its notification category.")
    IosSectionLabel("Recent notification categories")
    if(categories.isEmpty()) BannerFootnote(if(bridge.access)
        "New notifications will make their categories available here while the island or Notification Center is enabled. You can always find every app in Android settings above."
        else "Grant Android notification access and enable the island to show shortcuts for recent categories. Android settings above is available without that access.")
    else IosGroup {
        categories.forEachIndexed { index,notice ->
            Box(Modifier.testTag("banner_category:${notice.packageName}:${notice.channelId}")) {
                IosRow(notice.app,notice.channelName.ifBlank { "Notification category" }) {
                    failed=!NotificationSettingsNavigation.open(context,notice.packageName,notice.channelId)
                }
            }
            if(index<categories.lastIndex) IosDivider()
        }
    }
    BannerFootnote("Different categories, and sometimes individual conversations, have separate pop-up settings. Configure only the categories you want to route through the island. Other apps' custom in-app banners are controlled by those apps.")
    IosGroup { IosRow("All apps via ADB",if(advanced) "Hide guide" else "Advanced setup") { advanced=!advanced } }
    if(advanced) {
        BannerFootnote("Pixel / GrapheneOS also support a system-wide heads-up setting. From a computer authorized for USB debugging, save the current value first, then disable banners. This is a manual Android change; Vitreo cannot apply it.")
        SelectionContainer {
            Text("adb shell settings get global heads_up_notifications_enabled\n\nadb shell settings put global heads_up_notifications_enabled 0",
                fontFamily=FontFamily.Monospace,fontSize=12.sp,modifier=Modifier.padding(17.dp))
        }
        BannerFootnote("To re-enable banners, restore the saved value (normally 1). If it was ‘null’, delete the setting instead. This affects all apps, stays changed if you disable or uninstall Vitreo, and does not disable sounds, notification categories, bubbles or full-screen call/alarm screens. Device versions may differ.")
        SelectionContainer {
            Text("adb shell settings put global heads_up_notifications_enabled 1\n\n# If the original value was null:\nadb shell settings delete global heads_up_notifications_enabled",
                fontFamily=FontFamily.Monospace,fontSize=12.sp,modifier=Modifier.padding(17.dp))
        }
    }
}

@Composable
private fun BannerFootnote(value:String) {
    Text(value,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp,modifier=Modifier.padding(horizontal=17.dp,vertical=12.dp))
}
