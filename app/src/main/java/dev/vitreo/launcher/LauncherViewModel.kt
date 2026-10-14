package dev.vitreo.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.*
import dev.vitreo.launcher.graphics.WallpaperFrame
import dev.vitreo.launcher.graphics.WallpaperStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    data class UiState(
        val layout: LauncherState = LauncherState(),
        val apps: List<AppRecord> = emptyList(),
        val ready: Boolean = false,
        val powerSaver: Boolean = false,
        val recentApps: List<String> = emptyList(),
    )
    private val app = application
    private val recentPreferences = app.getSharedPreferences("recent-launches", Context.MODE_PRIVATE)
    private val store = StateStore(app)
    private val catalog = AppCatalog(app)
    private val wallpaperStore = WallpaperStore(app)
    private val _ui = MutableStateFlow(UiState(recentApps = recentPreferences.getString("apps", "")
        .orEmpty().split('|').filter(::isAppId).distinct().take(16)))
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _wallpaper = MutableStateFlow<WallpaperFrame?>(null)
    val wallpaper: StateFlow<WallpaperFrame?> = _wallpaper.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val saves = Channel<LauncherState>(Channel.CONFLATED)
    // Close-and-drain in onCleared: cancelling viewModelScope must not drop queued writes.
    // Process termination can still interrupt any asynchronous save; AtomicFile protects integrity.
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var catalogJob: Job? = null
    private var wallpaperJob: Job? = null
    private var loaded = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val saving = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            _ui.value = _ui.value.copy(powerSaver = saving)
        }
    }

    init {
        persistenceScope.launch {
            try {
                for (state in saves) {
                    try { store.save(state) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { _messages.tryEmit("No se guardaron los cambios: ${e.javaClass.simpleName}.") }
                }
            } finally { persistenceScope.cancel() }
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { store.load() }
            _ui.value = _ui.value.copy(layout = result.state)
            loaded = true
            result.warning?.let { _messages.emit(it) }
            reloadWallpaper()
            refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(app, powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun refresh() {
        if (!loaded) return
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { catalog.load() }
                ensureActive()
                val installed = if (_ui.value.layout.initialized) apps.map { it.id }
                    else catalog.preferredDock(apps) + apps.map { it.id }
                val merged = LayoutOps.reconcile(_ui.value.layout, installed.distinct())
                val saving = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                _ui.value = _ui.value.copy(layout = merged, apps = apps, ready = true, powerSaver = saving)
                saves.trySend(merged)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _ui.value = _ui.value.copy(ready = true)
                _messages.emit("No se pudo actualizar la lista de aplicaciones. Puedes reintentar en Ajustes.")
            }
        }
    }

    private fun commit(next: LauncherState) {
        if (!_ui.value.ready) return
        val old = _ui.value.layout
        val state = next.sanitized()
        _ui.value = _ui.value.copy(layout = state)
        saves.trySend(state)
        if (old.appearance.palette != state.appearance.palette || old.appearance.usePhoto != state.appearance.usePhoto) reloadWallpaper()
    }

    fun appearance(value: Appearance) = commit(_ui.value.layout.copy(appearance = value))
    fun addHome(appId: String) = commit(LayoutOps.addToHome(_ui.value.layout, appId))
    fun resetLayout() = commit(LayoutOps.reconcile(LauncherState(appearance = _ui.value.layout.appearance),
        (catalog.preferredDock(_ui.value.apps) + _ui.value.apps.map { it.id }).distinct()))
    fun removeApp(appId: String) = commit(LayoutOps.removeApp(_ui.value.layout, appId))
    fun pinDock(appId: String) {
        if (_ui.value.layout.dock.size >= DOCK_SLOTS && appId !in _ui.value.layout.dock) {
            message("El dock tiene cuatro plazas. Mueve una app al escritorio primero."); return
        }
        commit(LayoutOps.pinDock(_ui.value.layout, appId))
    }
    fun move(id: String, delta: Int) = commit(LayoutOps.move(_ui.value.layout, id, delta))
    fun moveToSlot(id: String, target: String) = commit(LayoutOps.moveToSlot(_ui.value.layout, id, target))
    fun dockDrop(id: String, slot: Int) = commit(LayoutOps.placeInDock(_ui.value.layout, id, slot))
    fun folder(first: String, second: String) {
        val s = if (first in _ui.value.layout.dock) LayoutOps.addToHome(_ui.value.layout, first) else _ui.value.layout
        commit(LayoutOps.folder(s, first, second, "folder:${UUID.randomUUID()}"))
    }
    fun recordLaunch(id: String) {
        if (_ui.value.apps.none { it.id == id }) return
        val recent = (listOf(id) + _ui.value.recentApps).distinct().take(16)
        _ui.value = _ui.value.copy(recentApps = recent)
        recentPreferences.edit().putString("apps", recent.joinToString("|")).apply()
    }
    fun addToFolder(appId: String, folder: String) = commit(LayoutOps.addToFolder(_ui.value.layout, appId, folder))
    fun dissolve(id: String) = commit(LayoutOps.dissolve(_ui.value.layout, id))
    fun renameFolder(id: String, name: String) = commit(LayoutOps.renameFolder(_ui.value.layout, id, name))
    fun message(text: String) { _messages.tryEmit(text) }

    private fun reloadWallpaper() {
        wallpaperJob?.cancel()
        val appearance = _ui.value.layout.appearance
        wallpaperJob = viewModelScope.launch {
            try {
                val frame = withContext(Dispatchers.IO) { wallpaperStore.load(appearance.usePhoto, appearance.palette) }
                ensureActive(); _wallpaper.value = frame
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("No se pudo preparar el fondo. Prueba otra imagen.") }
        }
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { wallpaperStore.importPhoto(uri) }
                commit(_ui.value.layout.copy(appearance = _ui.value.layout.appearance.copy(usePhoto = true)))
                reloadWallpaper() // Also handles replacing an already active photo.
                message("Fondo guardado dentro de Vitreo.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("No se pudo importar la imagen. Se conserva el fondo anterior.") }
        }
    }

    fun exportLayout(uri: Uri) {
        val state = _ui.value.layout
        writeDocument(uri, "Copia guardada.") {
            // A portable layout is not permission to reopen a URI on another device.
            StateCodec.encode(state.copy(appearance = state.appearance.copy(usePhoto = false)))
        }
    }
    fun importLayout(uri: Uri) {
        viewModelScope.launch {
            try {
                val state = withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri) ?: error("No se pudo abrir la copia.")
                    stream.use { StateCodec.decode(StateStore.readLimited(it)) }
                }
                commit(LayoutOps.reconcile(state.copy(initialized = true,
                    appearance = state.appearance.copy(usePhoto = false)), _ui.value.apps.map { it.id }))
                message("Copia importada. El fondo de foto no forma parte de la copia.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Copia rechazada. El escritorio anterior no se ha modificado.") }
        }
    }
    fun exportDiagnostics(uri: Uri) = writeDocument(uri, "Informe guardado. Rev\u00edsalo antes de compartirlo.") {
        JSONObject().apply {
            put("app", "Vitreo"); put("version", BuildConfig.VERSION_NAME)
            put("applicationId", BuildConfig.APPLICATION_ID); put("buildType", BuildConfig.BUILD_TYPE)
            put("androidApi", Build.VERSION.SDK_INT); put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL); put("powerSaver", _ui.value.powerSaver)
            put("agslPlatformAvailable", Build.VERSION.SDK_INT >= 33)
            put("agslFallbackReason", dev.vitreo.launcher.graphics.GlassDiagnostics.lastFailure)
            put("lastCrash", File(app.filesDir, "last-crash.txt").takeIf(File::exists)?.readText()?.take(32768) ?: "none")
            put("note", "No app inventory, images, tokens or layout included. Crash stack may contain technical context.")
        }.toString(2)
    }

    private fun writeDocument(uri: Uri, success: String, contents: () -> String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = contents().toByteArray(Charsets.UTF_8)
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error("No se pudo escribir el documento.")
                    stream.use { it.write(bytes) }
                }
                message(success)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("No se pudo guardar el documento.") }
        }
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(receiver) }
        runCatching { app.unregisterReceiver(powerReceiver) }
        saves.close()
        super.onCleared()
    }
}
