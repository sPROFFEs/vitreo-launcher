package dev.vitreo.launcher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vitreo.launcher.BuildConfig
import dev.vitreo.launcher.update.AppUpdates

@Composable
fun UpdatesSettings() {
    val context=LocalContext.current
    val state by AppUpdates.state.collectAsState()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) allowed=context.packageManager.canRequestPackageInstalls() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    IosSectionLabel("Vitreo ${BuildConfig.VERSION_NAME}")
    if (!AppUpdates.supported) {
        UpdateFootnote("Automatic updates are available in the signed Vitreo release. Debug and Preview are separate apps and never install production releases.")
        return
    }
    IosGroup {
        IosToggleRow("Automatic updates",state.automatic) { AppUpdates.automatic(it) }; IosDivider()
        IosToggleRow("Download on Wi-Fi only",state.wifiOnly) { AppUpdates.wifiOnly(it) }
    }
    UpdateFootnote("Checks daily for stable releases on GitHub. If the API is unavailable or rate limited, verified release files are fetched directly over HTTPS. Downloads use an unmetered connection by default. The APK checksum, app identity, version and signing certificate are verified before installation.")
    IosGroup {
        IosRow("Allow installing updates",if(allowed) "Allowed" else "Open Android settings") {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))) }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            IosDivider(); IosRow("Allow update notifications") { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
    UpdateFootnote("Android may ask you to confirm an installation. Installation waits until the device is unlocked. Allow update notifications to be notified when your confirmation is needed.")
    Text(state.status,Modifier.padding(16.dp),style=MaterialTheme.typography.bodyLarge)
    if (state.busy) {
        if(state.progress >= 0) LinearProgressIndicator(progress={state.progress/100f},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=16.dp))
        TextButton(onClick=AppUpdates::cancel) { Text("Cancel") }
    } else IosGroup {
        if (state.needsConsent) { IosRow("Confirm installation") { AppUpdates.confirm(context) }; IosDivider() }
        else if (state.release!=null) { IosRow("Install Vitreo ${state.release!!.version}") { AppUpdates.installNow() }; IosDivider() }
        IosRow("Check for updates",state.checkedAt.takeIf { it>0 }?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(it)) }.orEmpty()) { AppUpdates.checkNow() }
    }
    state.release?.let { release ->
        IosSectionLabel("What's new in ${release.version}")
        Text(release.notes,Modifier.padding(16.dp),fontSize=14.sp)
        TextButton(onClick={ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(release.page))) } }) { Text("View GitHub release") }
    }
    Spacer(Modifier.height(24.dp))
}
@Composable
private fun UpdateFootnote(text: String) { Text(text,Modifier.padding(horizontal=16.dp,vertical=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
