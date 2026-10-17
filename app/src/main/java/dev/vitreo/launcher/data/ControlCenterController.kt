package dev.vitreo.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.view.KeyEvent
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** Public Android controls. No camera session, network query or privileged toggles. */
class ControlCenterController(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val cameras = app.getSystemService(CameraManager::class.java)
    data class State(val volume: Float = 0f, val volumeFixed: Boolean = false, val battery: Int = -1,
        val torchSupported: Boolean = false, val torchAvailable: Boolean = false, val torchOn: Boolean = false,
        val systemBrightness: Float = .5f, val musicActive: Boolean = false,
        val airplane: Boolean? = null, val wifi: Boolean? = null, val bluetooth: Boolean? = null)
    var state by mutableStateOf(State())
        private set
    private var torchId: String? = null
    private var listening = false
    private var audioListening = false
    private var maximumVolume = 1
    private var observing = false
    private val radioSettings = listOf("airplane_mode_on", "wifi_on", "bluetooth_on")
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }
    private fun radio(key: String): Boolean? = runCatching {
        Settings.Global.getInt(app.contentResolver, key, -1).takeIf { it == 0 || it == 1 }?.let { it == 1 }
    }.getOrNull()
    private val playback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) { refresh() }
    }
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) state = state.copy(torchAvailable = true, torchOn = enabled)
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchId) state = state.copy(torchAvailable = false, torchOn = false)
        }
    }
    fun refresh() {
        val max = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(1).coerceAtLeast(1)
        maximumVolume = max
        state = state.copy(
            volume = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max }.getOrDefault(0f),
            volumeFixed = runCatching { audio.isVolumeFixed }.getOrDefault(true),
            battery = runCatching { app.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
                .getOrDefault(-1).takeIf { it in 0..100 } ?: -1,
            musicActive = runCatching { audio.isMusicActive }.getOrDefault(false),
            airplane = radio("airplane_mode_on"), wifi = radio("wifi_on"), bluetooth = radio("bluetooth_on"),
            systemBrightness = runCatching { Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f }
                .getOrDefault(.5f).coerceIn(.02f, 1f))
    }
    // Called off Main; camera-service discovery must not delay the panel's entry.
    fun discoverTorch(): String? = runCatching {
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) null
        else cameras.cameraIdList.firstOrNull { id -> cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    fun start(torchCamera: String?) {
        torchId = torchCamera
        state = state.copy(torchSupported = torchCamera != null)
        refresh()
        if (!observing) {
            observing = true
            runCatching { radioSettings.forEach { app.contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, settingsObserver) } }
                .onFailure { app.contentResolver.unregisterContentObserver(settingsObserver); observing = false }
        }
        if (!audioListening) runCatching {
            audio.registerAudioPlaybackCallback(playback, Handler(Looper.getMainLooper())); audioListening = true
        }
        if (!listening && torchCamera != null) runCatching {
            cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper())); listening = true
        }
    }
    fun stop() {
        if (listening) runCatching { cameras.unregisterTorchCallback(callback) }
        listening = false
        if (audioListening) runCatching { audio.unregisterAudioPlaybackCallback(playback) }
        audioListening = false
        if (observing) runCatching { app.contentResolver.unregisterContentObserver(settingsObserver) }
        observing = false
    }
    fun volume(value: Float): Boolean = runCatching {
        if (audio.isVolumeFixed) return false
        val max = maximumVolume
        val target = (value.coerceIn(0f, 1f) * max).roundToInt()
        if (target == (state.volume * max).roundToInt()) return true
        if (target != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        refresh()
        (state.volume * max).roundToInt() == target
    }.getOrDefault(false)
    fun mediaKey(code: Int): Boolean = runCatching {
        if (code !in setOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_NEXT)) return false
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        true
    }.getOrDefault(false)
    fun toggleTorch(): Boolean = runCatching {
        val id = torchId ?: return false
        if (!state.torchAvailable) return false
        // TorchCallback confirms the resulting state; never show a fake enabled toggle.
        cameras.setTorchMode(id, !state.torchOn)
        true
    }.getOrDefault(false)
}
