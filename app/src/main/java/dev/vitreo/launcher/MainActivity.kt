package dev.vitreo.launcher

import android.app.role.RoleManager
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
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
    private var visible = false
    private val keyguard get() = getSystemService(KeyguardManager::class.java)
    private val screenPower get() = getSystemService(PowerManager::class.java)
    private val revealBeforeDraw = ViewTreeObserver.OnPreDrawListener {
        revealAfterUnlock(beforeDraw = true)
        true
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Some devices queue SCREEN_OFF until the display is already back.
                    // Preparing at that point would move an already visible workspace.
                    if (visible && defaultHome && !leftForApp && !screenPower.isInteractive)
                        model.homeUnlock.sleep(keyguard.isKeyguardLocked)
                }
                Intent.ACTION_SCREEN_ON -> model.homeUnlock.wake(keyguard.isKeyguardLocked)
                Intent.ACTION_USER_PRESENT -> {
                    model.homeUnlock.userPresent(SystemClock.uptimeMillis())
                    revealAfterUnlock()
                }
            }
        }
    }
    private val widgetConfigurations = WidgetConfigurationRequests()
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultHome = isDefaultHome()
        model.homeRole(defaultHome)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        window.decorView.viewTreeObserver.addOnPreDrawListener(revealBeforeDraw)
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            VitreoTheme(ui.layout.appearance.interfaceStyle, ui.layout.appearance.themePreset, ui.layout.appearance) {
                LauncherScreen(model, homeSerial, appReturnSerial, defaultHome, ::chooseHome, ::launchApp, ::launchShortcut, ::appInfo, ::configureWidget)
            }
        }
    }
    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onResume() {
        super.onResume()
        dev.vitreo.launcher.update.AppUpdates.onForeground()
        dev.vitreo.launcher.island.NotificationIsland.refresh()
        if (leftForApp) { leftForApp = false; appReturnSerial++ }
        defaultHome = isDefaultHome()
        revealAfterUnlock()
        model.homeRole(defaultHome)
        model.refreshIfStale()
    }
    override fun onPause() {
        if (defaultHome && !leftForApp && !screenPower.isInteractive)
            model.homeUnlock.sleep(keyguard.isKeyguardLocked)
        super.onPause()
    }
    override fun onStop() {
        // Keyguard can stop Home before the queued SCREEN_OFF broadcast arrives.
        if (defaultHome && !leftForApp && (!screenPower.isInteractive || keyguard.isKeyguardLocked)) {
            model.homeUnlock.sleep(keyguard.isKeyguardLocked, stopped = true)
        } else if (!isChangingConfigurations) model.homeUnlock.leaveHome()
        visible = false
        super.onStop()
    }
    override fun onDestroy() {
        window.decorView.viewTreeObserver.removeOnPreDrawListener(revealBeforeDraw)
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) revealAfterUnlock()
        if (hasFocus && defaultHome && model.systemBackgroundVisible.value) runCatching {
            android.app.WallpaperManager.getInstance(this).setWallpaperOffsets(window.decorView.windowToken, .5f, .5f)
        }
    }
    private fun revealAfterUnlock(beforeDraw: Boolean = false) {
        val motion = model.homeUnlock
        if (!motion.pending || !defaultHome || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !screenPower.isInteractive) return
        // Android can resume and draw the stopped Home behind its outgoing lock
        // surface well before keyguard reports hidden. Home is not showWhenLocked.
        val drawingResumedHome = beforeDraw && visible && motion.stoppedForSleep
        if (drawingResumedHome || !keyguard.isKeyguardLocked)
            motion.showUnlockedHome(SystemClock.uptimeMillis())
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
            model.homeUnlock.leaveHome()
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
            model.homeUnlock.leaveHome()
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
