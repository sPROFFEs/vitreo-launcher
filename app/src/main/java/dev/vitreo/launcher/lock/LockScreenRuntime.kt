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
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.data.StateStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No persistent service, overlay, device admin, boot receiver or authentication of our own.
 * HOME's background-start exemption is used only while the display is already off and the real
 * keyguard is locked. If Android stops this process, its native lock screen is the fallback. */
object LockScreenRuntime {
    private const val PREFS = "lock-screen-local"
    private val handler = Handler(Looper.getMainLooper())
    private val active = MutableStateFlow(false)
    val enabled = active.asStateFlow()
    private val style = MutableStateFlow(Appearance())
    val appearance = style.asStateFlow()
    private var receiver: BroadcastReceiver? = null
    private var cycle = 0
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("enabled", value) }
        if (value) register(context.applicationContext) else {
            receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
            receiver = null
            active.value = false
        }
        return null
    }

    private fun register(context: Context) {
        active.value = true
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> prepare(context, ++cycle, 0)
                    Intent.ACTION_USER_PRESENT -> cycle++
                }
            }
        }.also { ContextCompat.registerReceiver(context, it, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    private fun prepare(context: Context, token: Int, attempt: Int) {
        handler.postDelayed({
            if (!active.value || cycle != token ||
                context.getSystemService(PowerManager::class.java)?.isInteractive != false) return@postDelayed
            if (unavailableReason(context) != null ||
                context.getSystemService(UserManager::class.java)?.isUserForeground != true ||
                context.getSystemService(AudioManager::class.java)?.mode != AudioManager.MODE_NORMAL) return@postDelayed
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardLocked == true && keyguard.isDeviceLocked) {
                runCatching { context.startActivity(Intent(context, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
            } else if (attempt < 12) prepare(context, token, attempt + 1)
        }, if (attempt == 0) 120L else 140L)
    }
}
