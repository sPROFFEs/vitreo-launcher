package dev.vitreo.launcher.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.data.StateCodec
import dev.vitreo.launcher.ui.LockScreenSurface

/** Occludes the real keyguard; only Android can authenticate and dismiss it. Never exported. */
class LockScreenActivity : ComponentActivity() {
    internal var preview = false
        private set
    private var authenticating = false
    private var pendingNotice: String? = null
    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleReturn = Runnable { if (!preview && !authenticating && !isFinishing && keyguard?.isDeviceLocked == true) finishAndRemoveTask() }
    private fun resetIdle() {
        idleHandler.removeCallbacks(idleReturn)
        if (!preview && !authenticating && !isFinishing && hasWindowFocus() &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            getSystemService(android.os.PowerManager::class.java).isInteractive) {
            idleHandler.postDelayed(idleReturn, LockScreenRuntime.appearance.value.lockScreen.sanitized().idleTimeoutSeconds * 1000L)
        }
    }
    private fun completeUnlock() {
        if (preview || isFinishing || keyguard?.isDeviceLocked != false) return
        pendingNotice?.let { key ->
            val notice = dev.vitreo.launcher.data.SystemBridge.state.value.notices.firstOrNull { it.key == key }
            dev.vitreo.launcher.data.launchNotification(this, notice?.intent)
        }
        pendingNotice = null
        finishAndRemoveTask()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); resetIdle() }
    override fun onUserInteraction() { super.onUserInteraction(); resetIdle() }
    override fun onPause() { idleHandler.removeCallbacks(idleReturn); super.onPause() }

    private var presentReceiver: BroadcastReceiver? = null
    private val keyguard get() = getSystemService(KeyguardManager::class.java)
    private var failure by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent.getBooleanExtra(PREVIEW, false)
        if (!preview && (!LockScreenRuntime.canPresent(this) ||
                keyguard?.isDeviceLocked != true || !keyguard.isKeyguardLocked)) {
            finishAndRemoveTask(); return
        }
        enableEdgeToEdge()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.keepScreenOn = false
        setShowWhenLocked(!preview)
        if (!preview) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (!preview) {
            LockScreenRuntime.prepared(this)
            presentReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_USER_PRESENT -> completeUnlock()
                        Intent.ACTION_SCREEN_OFF -> { pendingNotice = null; idleHandler.removeCallbacks(idleReturn) }
                        Intent.ACTION_SCREEN_ON -> resetIdle()
                    }
                }
            }.also { ContextCompat.registerReceiver(this, it, IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }, ContextCompat.RECEIVER_NOT_EXPORTED) }
        }
        if (!preview) lifecycleScope.launch {
            // Disabling must remove the surface even while keyguard pauses Compose's frame clock.
            LockScreenRuntime.enabled.collect { enabled -> if (!enabled) finishAndRemoveTask() }
        }
        val previewAppearance = if (preview) runCatching {
            StateCodec.decode(intent.getStringExtra(STYLE).orEmpty()).appearance
        }.getOrDefault(Appearance()) else null
        setContent {
            val current by LockScreenRuntime.appearance.collectAsState()
            BackHandler { finishAndRemoveTask() }
            var previewHint by rememberSaveable { mutableStateOf(false) }
            LockScreenSurface(previewAppearance ?: current, preview, busy,
                failure ?: if (preview && previewHint) "Preview only. Android handles real authentication." else null,
                onUnlock = { if (preview) previewHint = true else unlock() },
                onSystemLock = { finishAndRemoveTask() },
                onSystemUnlock = { if (preview) previewHint = true else finishAndRemoveTask() },
                onCamera = { if (preview) previewHint = true else openSecureCamera() },
                onEmergency = { if (preview) previewHint = true else openEmergency() },
                onNotification = { key -> if (!preview) { pendingNotice = key; unlock() } })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val nextPreview = intent.getBooleanExtra(PREVIEW, false)
        setIntent(intent)
        // Power-off from an open preview must prepare a real secured surface, not reuse preview mode.
        if (preview || nextPreview) recreate()
    }

    override fun onResume() {
        super.onResume()
        if (!preview && !isFinishing) {
            if (keyguard?.isDeviceLocked == false) completeUnlock()
            else if (!LockScreenRuntime.enabled.value || LockScreenRuntime.unavailableReason(this) != null) finishAndRemoveTask()
            else resetIdle()
        }
    }

    private fun unlock() {
        if (authenticating || isFinishing) return
        if (keyguard?.isDeviceLocked == false) { completeUnlock(); return }
        idleHandler.removeCallbacks(idleReturn)
        authenticating = true; busy = true; failure = null
        keyguard?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                authenticating = false; busy = false
                completeUnlock()
            }
            override fun onDismissCancelled() { authenticating = false; busy = false; pendingNotice = null; resetIdle() }
            override fun onDismissError() {
                authenticating = false; busy = false; pendingNotice = null; resetIdle()
                failure = "Use the system lock screen to unlock this device."
            }
        })
    }

    private fun openSecureCamera() {
        runCatching { startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)) }
            .onFailure { failure = "Secure camera is unavailable. Use the system lock screen." }
    }

    private fun openEmergency() {
        // Android exposes no public API for replacing the keyguard emergency dialer.
        // Hand back to the native screen, which owns emergency calls and medical information.
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(idleReturn)
        pendingNotice = null
        LockScreenRuntime.removed(this)
        presentReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    companion object {
        private const val PREVIEW = "dev.vitreo.launcher.lock.PREVIEW"
        private const val STYLE = "dev.vitreo.launcher.lock.STYLE"
        fun preview(context: Context, appearance: Appearance) {
            context.startActivity(Intent(context, LockScreenActivity::class.java)
                .addFlags(if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(PREVIEW, true)
                .putExtra(STYLE, StateCodec.encode(dev.vitreo.launcher.core.LauncherState(appearance = appearance.copy(iconOverrides = emptyMap(), iconPack = "")))))
        }
    }
}
