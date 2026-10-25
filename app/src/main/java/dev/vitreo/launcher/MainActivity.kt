package dev.vitreo.launcher

import android.app.role.RoleManager
import android.app.ActivityOptions
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.ui.LauncherScreen
import dev.vitreo.launcher.ui.VitreoTheme
import dev.vitreo.launcher.core.WidgetConfigurationRequests

class MainActivity : ComponentActivity() {
    private val model by viewModels<LauncherViewModel>()
    private var homeSerial by mutableIntStateOf(0)
    private var appReturnSerial by mutableIntStateOf(0)
    private var leftForApp = false
    private var defaultHome by mutableStateOf(false)
    private var pendingHomeReveal = false
    private var homeFocusedOnce = false
    private var homeRevealSerial by mutableIntStateOf(0)
    private val overviewReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            // Quickstep on Android 12 can keep HOME resumed underneath Overview.
            // Focus alone cannot distinguish it from Aoede, keyboards or dialogs.
            if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS &&
                intent.getStringExtra("reason") == "recentapps") pendingHomeReveal = true
        }
    }
    private val widgetConfigurations = WidgetConfigurationRequests()
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultHome = isDefaultHome()
        model.homeRole(defaultHome)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeRevealSerial = savedInstanceState?.getInt("homeRevealSerial") ?: 0
        pendingHomeReveal = savedInstanceState != null
        androidx.core.content.ContextCompat.registerReceiver(this, overviewReceiver,
            android.content.IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            VitreoTheme(ui.layout.appearance.interfaceStyle, ui.layout.appearance.themePreset) {
                LauncherScreen(model, homeSerial, appReturnSerial, defaultHome, ::chooseHome, ::launchApp, ::launchShortcut, ::appInfo, ::configureWidget, homeRevealSerial)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        dev.vitreo.launcher.update.AppUpdates.onForeground()
        dev.vitreo.launcher.island.NotificationIsland.refresh()
        if (leftForApp) { leftForApp = false; appReturnSerial++ }
        revealHomeWhenFocused()
        defaultHome = isDefaultHome()
        model.homeRole(defaultHome)
        model.refresh()
    }
    override fun onStop() { pendingHomeReveal = true; super.onStop() }
    override fun onDestroy() { unregisterReceiver(overviewReceiver); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("homeRevealSerial", homeRevealSerial)
        super.onSaveInstanceState(outState)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            revealHomeWhenFocused()
            homeFocusedOnce = true
        }
        if (hasFocus && defaultHome && model.systemBackgroundVisible.value) runCatching {
            android.app.WallpaperManager.getInstance(this).setWallpaperOffsets(window.decorView.windowToken, .5f, .5f)
        }
    }
    private fun revealHomeWhenFocused() {
        // RESUMED can precede the visible window while Android still animates
        // its Recents card. Start the icon arrival with actual input focus.
        if (pendingHomeReveal && hasWindowFocus() && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            pendingHomeReveal = false
            homeRevealSerial++
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (homeFocusedOnce && !hasWindowFocus() && intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)) pendingHomeReveal = true
        // The first HOME after an app restores the place it was opened from.
        // A further HOME closes that folder/search and returns to the first page.
        if (!leftForApp) homeSerial++
    }
    private fun isDefaultHome(): Boolean = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    private fun chooseHome() {
        try {
            val role = getSystemService(RoleManager::class.java)
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) roleRequest.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) { model.message("Open Android Settings > Default apps > Home app.") }
    }
    private fun launchApp(id: String, source: Rect?): Boolean {
        // Only an activity from the live launchable-app catalog can be launched.
        if (model.ui.value.apps.none { it.id == id }) {
            model.message("This app is unavailable in this profile. You can remove its shortcut."); return false
        }
        val component = ComponentName.unflattenFromString(id) ?: return false
        return try {
            val appearance = model.ui.value.layout.appearance
            val options = when {
                appearance.reducedEffects || model.ui.value.powerSaver -> ActivityOptions.makeCustomAnimation(this, 0, 0)
                source != null -> ActivityOptions.makeScaleUpAnimation(window.decorView, source.left.toInt(), source.top.toInt(),
                    source.width.toInt().coerceAtLeast(1), source.height.toInt().coerceAtLeast(1))
                else -> null
            }
            val location = IntArray(2)
            window.decorView.getLocationOnScreen(location)
            val screenBounds = source?.let { android.graphics.Rect(it.left.toInt() + location[0], it.top.toInt() + location[1],
                it.right.toInt() + location[0], it.bottom.toInt() + location[1]) }
            // Use Android's launcher contract, with BOTH animation and screen-space source bounds.
            getSystemService(LauncherApps::class.java).startMainActivity(component, Process.myUserHandle(), screenBounds, options?.toBundle())
            leftForApp = true
            model.recordLaunch(id)
            true
        }
        catch (e: Exception) { leftForApp = false; model.message("Android could not open this app."); false }
    }
    private fun launchShortcut(shortcut: ShortcutInfo, source: Rect?): Boolean {
        if (model.ui.value.apps.none { it.packageName == shortcut.`package` }) return false
        return try {
            val appearance = model.ui.value.layout.appearance
            val options = if (appearance.reducedEffects || model.ui.value.powerSaver) ActivityOptions.makeCustomAnimation(this, 0, 0)
                else source?.let { ActivityOptions.makeScaleUpAnimation(window.decorView, it.left.toInt(), it.top.toInt(),
                    it.width.toInt().coerceAtLeast(1), it.height.toInt().coerceAtLeast(1)) }
            getSystemService(LauncherApps::class.java).startShortcut(shortcut, null, options?.toBundle())
            leftForApp = true
            model.ui.value.apps.firstOrNull { it.packageName == shortcut.`package` }?.let { model.recordLaunch(it.id) }
            true
        } catch (_: Exception) { model.message("This shortcut is no longer available."); false }
    }
    private fun appInfo(id: String) {
        val app = model.ui.value.apps.find { it.id == id } ?: return
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))) }
        catch (e: Exception) { model.message("Could not open App Info.") }
    }
    private fun configureWidget(host: AppWidgetHost, id: Int, result: (Boolean) -> Unit) {
        val request = try { widgetConfigurations.register(result) }
        catch (_: Exception) { result(false); return }
        try { host.startAppWidgetConfigureActivityForResult(this, id, 0, request, null) }
        catch (_: Exception) { widgetConfigurations.complete(request, false) }
    }
    @Deprecated("Android widget configuration uses the host's activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgetConfigurations.complete(requestCode, resultCode == RESULT_OK))
            super.onActivityResult(requestCode, resultCode, data)
    }
}
