package dev.vitreo.launcher.lock

import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.os.SystemClock
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.LockScreenWakeWindow
import java.lang.ref.WeakReference
import dev.vitreo.launcher.data.StateStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No persistent service, overlay, device admin, boot receiver or authentication of our own.
 * Prepare immediately at screen-off; a single immediate wake recovery covers quick power cycles.
 * The real keyguard must already be secure and locked. Native keyguard remains the fallback. */
object LockScreenRuntime {
    private const val PREFS = "lock-screen-local"
    private val handler = Handler(Looper.getMainLooper())
    private val active = MutableStateFlow(false)
    val enabled = active.asStateFlow()
    private val style = MutableStateFlow(Appearance())
    val appearance = style.asStateFlow()
    private var receiver: BroadcastReceiver? = null
    private var cycle = 0
    private val wakeWindow = LockScreenWakeWindow()
    private var surface: WeakReference<LockScreenActivity>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun initialize(context: Context) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        val token = cycle
        scope.launch {
            val options = withContext(Dispatchers.IO) { StateStore(context).load().state.appearance }
            if (cycle != token || !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)) return@launch
            configure(options)
            if (unavailableReason(context) == null) register(context.applicationContext)
            // Keep the device-local preference so a temporary role change never silently loses it.
        }
    }

    fun configure(appearance: Appearance) { style.value = appearance.sanitized() }
    private fun configureNotifications(context: Context) {
        val a = style.value
        dev.vitreo.launcher.data.SystemBridge.configure(context, a.notificationCenter || active.value && (a.lockScreen.showNotifications || a.lockScreen.notificationCount), a.mediaIntegration)
    }

    fun unavailableReason(context: Context): String? = when {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) != true ->
            "Set Vitreo as your default launcher first."
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true ->
            "Set a PIN, password or pattern in Android Security settings first."
        else -> null
    }

    /** Called only by this device's settings or an isolated emulator test, never by backup import. */
    fun setEnabled(context: Context, value: Boolean): String? {
        if (value) unavailableReason(context)?.let { return it }
        cycle++ // Invalidate any pending initialization or screen-off preparation.
        wakeWindow.cancel()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("enabled", value) }
        if (value) register(context.applicationContext) else {
            receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
            receiver = null
            active.value = false
            configureNotifications(context)
        }
        return null
    }

    private fun register(context: Context) {
        active.value = true
        configureNotifications(context)
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        wakeWindow.screenOff(SystemClock.elapsedRealtime())
                        prepare(context, ++cycle, 0)
                    }
                    Intent.ACTION_SCREEN_ON -> if (wakeWindow.consumeWake(SystemClock.elapsedRealtime()))
                        prepare(context, cycle, 0, waking = true)
                    Intent.ACTION_USER_PRESENT -> { cycle++; wakeWindow.cancel() }
                }
            }
        }.also { ContextCompat.registerReceiver(context, it, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    internal fun prepared(activity: LockScreenActivity) { surface = WeakReference(activity) }
    internal fun removed(activity: LockScreenActivity) { if (surface?.get() === activity) surface = null }
    internal fun canPresent(context: Context): Boolean = active.value && unavailableReason(context) == null &&
        context.getSystemService(UserManager::class.java)?.isUserForeground == true &&
        context.getSystemService(AudioManager::class.java)?.mode == AudioManager.MODE_NORMAL

    private fun prepare(context: Context, token: Int, attempt: Int, waking: Boolean = false) {
        if (cycle != token || !canPresent(context)) return
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive != false
        if (interactive && !waking) return
        // Never cover an existing custom surface, authentication session or secure camera return.
        if (surface?.get()?.let { !it.isFinishing && !it.isDestroyed } == true) return
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true && keyguard.isDeviceLocked) {
            runCatching { context.startActivity(Intent(context, LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NO_USER_ACTION)) }
        } else if (!waking && !interactive && attempt < 24) {
            // Frequent checks only during the first frames; then back off while the display is off.
            handler.postDelayed({ prepare(context, token, attempt + 1) }, if (attempt < 8) 16L else 100L)
        }
    }
}
