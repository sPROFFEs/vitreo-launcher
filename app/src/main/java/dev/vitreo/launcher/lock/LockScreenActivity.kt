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
                    if (intent.action == Intent.ACTION_USER_PRESENT && keyguard?.isDeviceLocked == false) finishAndRemoveTask()
                }
            }.also { ContextCompat.registerReceiver(this, it, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED) }
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
                onCamera = { if (preview) previewHint = true else openSecureCamera() },
                onEmergency = { if (preview) previewHint = true else openEmergency() })
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
            if (keyguard?.isDeviceLocked == false || !LockScreenRuntime.enabled.value ||
                LockScreenRuntime.unavailableReason(this) != null) finishAndRemoveTask()
        }
    }

    private fun unlock() {
        if (authenticating || isFinishing) return
        if (keyguard?.isDeviceLocked == false) { finishAndRemoveTask(); return }
        authenticating = true; busy = true; failure = null
        keyguard?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                authenticating = false; busy = false
                if (keyguard?.isDeviceLocked == false) finishAndRemoveTask()
            }
            override fun onDismissCancelled() { authenticating = false; busy = false }
            override fun onDismissError() {
                authenticating = false; busy = false
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
