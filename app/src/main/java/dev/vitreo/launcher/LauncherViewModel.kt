package dev.vitreo.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.*
import dev.vitreo.launcher.graphics.WallpaperFrame
import dev.vitreo.launcher.graphics.WallpaperStore
import dev.vitreo.launcher.graphics.HomeBackground
import dev.vitreo.launcher.graphics.SystemHomeWallpaper
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
        val iconPacks: List<IconPackInfo> = emptyList(),
        val iconRevision: Int = 0,
    )
    private val app = application
    private val recentPreferences = app.getSharedPreferences("recent-launches", Context.MODE_PRIVATE)
    private val store = StateStore(app)
    private val catalog = AppCatalog(app)
    val iconPacks = IconPacks(app)
    private val wallpaperStore = WallpaperStore(app)
    private val systemWallpaper = SystemHomeWallpaper(app)
    private var homeRole = false
    private var backgroundJob: Job? = null
    private var syncJob: Job? = null
    private val _background = MutableStateFlow<android.graphics.Bitmap?>(null)
    val background: StateFlow<android.graphics.Bitmap?> = _background.asStateFlow()
    private val _systemBackgroundVisible = MutableStateFlow(false)
    val systemBackgroundVisible: StateFlow<Boolean> = _systemBackgroundVisible.asStateFlow()
    private val _ui = MutableStateFlow(UiState(recentApps = recentPreferences.getString("apps", "")
        .orEmpty().split('|').filter(::isAppId).distinct().take(16)))
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _wallpaper = MutableStateFlow<WallpaperFrame?>(null)
    val wallpaper: StateFlow<WallpaperFrame?> = _wallpaper.asStateFlow()
    data class PhotoDraft(val frame: WallpaperFrame, val transform: PhotoTransform)
    private val _photoDraft = MutableStateFlow<PhotoDraft?>(null)
    val photoDraft: StateFlow<PhotoDraft?> = _photoDraft.asStateFlow()
    private val _photoBusy = MutableStateFlow(false)
    val photoBusy: StateFlow<Boolean> = _photoBusy.asStateFlow()
    private var photoJob: Job? = null
    private var wallpaperWidth = 720
    private var wallpaperHeight = 1600
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val saves = Channel<LauncherState>(Channel.CONFLATED)
    // Close-and-drain in onCleared: cancelling viewModelScope must not drop queued writes.
    // Process termination can still interrupt any asynchronous save; AtomicFile protects integrity.
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var catalogJob: Job? = null
    private var wallpaperJob: Job? = null
    private var loaded = false
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val removedPackages = mutableSetOf<String>()
    private val unavailablePackages = mutableSetOf<String>()
    private val appChanges = object : LauncherApps.Callback() {
        private fun changed(packages: Array<out String>, user: UserHandle) {
            if (user != android.os.Process.myUserHandle()) return
            packages.forEach(catalog::invalidate)
            refresh()
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            if (user == android.os.Process.myUserHandle()) { removedPackages.remove(packageName); unavailablePackages.remove(packageName) }
            changed(arrayOf(packageName), user)
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            // LauncherApps explicitly distinguishes removal from an update.
            if (user == android.os.Process.myUserHandle()) removedPackages.add(packageName)
            changed(arrayOf(packageName), user)
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(arrayOf(packageName), user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            if (user == android.os.Process.myUserHandle()) unavailablePackages.removeAll(packageNames.toSet())
            changed(packageNames, user)
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            if (user == android.os.Process.myUserHandle()) unavailablePackages.addAll(packageNames)
            changed(packageNames, user)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.data?.schemeSpecificPart?.let { name ->
                when (intent.action) {
                    Intent.ACTION_PACKAGE_REMOVED -> if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) unavailablePackages.add(name)
                        else removedPackages.add(name)
                    Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> { removedPackages.remove(name); unavailablePackages.remove(name) }
                }
                catalog.invalidate(name)
            }
            refresh()
        }
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
                    catch (e: Exception) { _messages.tryEmit("Changes could not be saved: ${e.javaClass.simpleName}.") }
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
        launcherApps.registerCallback(appChanges, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(app, powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun refresh() {
        if (!loaded) return
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            try {
                val explicitRemovals = removedPackages.toSet()
                val unavailable = unavailablePackages.toSet()
                val known = _ui.value.layout.knownApps
                val (apps, replacements, removed) = withContext(Dispatchers.IO) {
                    val records = catalog.load()
                    val removed = explicitRemovals + catalog.confirmedRemovedPackages(known, records, unavailable)
                    val remaining = records.filterNot { it.packageName in removed }
                    Triple(remaining, catalog.aliasReplacements(known, remaining), removed)
                }
                val availablePacks = withContext(Dispatchers.IO) { iconPacks.refresh() }
                ensureActive()
                val installed = if (_ui.value.layout.initialized) apps.map { it.id }
                    else catalog.preferredDock(apps) + apps.map { it.id }
                val merged = LayoutOps.reconcile(LayoutOps.remapApps(LayoutOps.removePackages(_ui.value.layout, removed), replacements), installed.distinct())
                val saving = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                val before = _ui.value
                val recent = before.recentApps.map { replacements[it] ?: it }.filterNot { it.substringBefore('/') in removed }.distinct()
                _ui.value = before.copy(layout = merged, apps = apps, ready = true, powerSaver = saving, recentApps = recent,
                    iconPacks = availablePacks, iconRevision = iconPacks.revision)
                if (recent != before.recentApps) recentPreferences.edit().putString("apps", recent.joinToString("|")).apply()
                if (merged != before.layout) saves.trySend(merged)
                removedPackages.removeAll(removed)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _ui.value = _ui.value.copy(ready = true)
                _messages.emit("Could not refresh your apps. Try again in Settings.")
            }
        }
    }

    private fun commit(next: LauncherState, preparedWallpaper: WallpaperFrame? = null) {
        if (!_ui.value.ready) return
        val old = _ui.value.layout
        val state = next.sanitized()
        _ui.value = _ui.value.copy(layout = state)
        saves.trySend(state)
        if (preparedWallpaper != null) {
            wallpaperJob?.cancel()
            _wallpaper.value = preparedWallpaper
            prepareHomeBackground()
        } else if (old.appearance.palette != state.appearance.palette || old.appearance.usePhoto != state.appearance.usePhoto ||
            old.appearance.photoTransform != state.appearance.photoTransform) reloadWallpaper()
        else if (old.appearance.themePreset != state.appearance.themePreset || old.appearance.wallpaperDim != state.appearance.wallpaperDim ||
            old.appearance.syncWallpaper != state.appearance.syncWallpaper)
            prepareHomeBackground(debounce = old.appearance.wallpaperDim != state.appearance.wallpaperDim)
    }

    fun appearance(value: Appearance) = commit(_ui.value.layout.copy(appearance = value))
    fun overrideIcon(id: String, value: IconOverride?) {
        if (_ui.value.apps.none { it.id == id }) return
        val current = _ui.value.layout.appearance
        appearance(current.copy(iconOverrides = if (value == null) current.iconOverrides - id else current.iconOverrides + (id to value)))
    }
    fun addHome(appId: String) = commit(LayoutOps.addToHome(_ui.value.layout, appId))
    fun resetLayout() = commit(LayoutOps.reconcile(LauncherState(appearance = _ui.value.layout.appearance),
        (catalog.preferredDock(_ui.value.apps) + _ui.value.apps.map { it.id }).distinct()))
    fun removeApp(appId: String) = commit(LayoutOps.removeApp(_ui.value.layout, appId))
    fun pinDock(appId: String) {
        if (_ui.value.layout.dock.size >= DOCK_SLOTS && appId !in _ui.value.layout.dock) {
            message("The dock holds four apps. Move one to Home first."); return
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
                val frame = withContext(Dispatchers.IO) { wallpaperStore.load(appearance.usePhoto, appearance.palette,
                    appearance.photoTransform, wallpaperWidth, wallpaperHeight) }
                ensureActive(); _wallpaper.value = frame; prepareHomeBackground()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Could not prepare the wallpaper. Try another image.") }
        }
    }

    fun wallpaperViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width == wallpaperWidth && height == wallpaperHeight) return
        wallpaperWidth = width; wallpaperHeight = height
        if (_ui.value.layout.appearance.usePhoto) reloadWallpaper() else prepareHomeBackground()
    }

    fun homeRole(active: Boolean) {
        homeRole = active
        if (!active) { syncJob?.cancel(); _systemBackgroundVisible.value = false }
        else syncHomeBackground()
    }

    private fun prepareHomeBackground(debounce: Boolean = false) {
        val frame = _wallpaper.value ?: return
        backgroundJob?.cancel(); syncJob?.cancel(); _systemBackgroundVisible.value = false
        val appearance = _ui.value.layout.appearance
        val width = wallpaperWidth; val height = wallpaperHeight
        backgroundJob = viewModelScope.launch {
            try {
                if (debounce) delay(140)
                val bitmap = withContext(Dispatchers.Default) { HomeBackground.render(frame, appearance, width, height, app.resources.displayMetrics.density) }
                ensureActive(); _background.value = bitmap; syncHomeBackground()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message("Could not prepare the Home background.") }
        }
    }

    private fun syncHomeBackground() {
        syncJob?.cancel()
        if (!homeRole || !_ui.value.layout.appearance.syncWallpaper) { _systemBackgroundVisible.value = false; return }
        val bitmap = _background.value ?: return
        syncJob = viewModelScope.launch {
            // Coalesce slider/photo/viewport changes; wallpaper IO never runs on Main.
            delay(350)
            try {
                val applied = withContext(Dispatchers.IO) { systemWallpaper.apply(bitmap) }
                ensureActive()
                if (homeRole && _background.value === bitmap && _ui.value.layout.appearance.syncWallpaper)
                    _systemBackgroundVisible.value = applied
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _systemBackgroundVisible.value = false; message("Android could not synchronize the Home wallpaper. The local background is still available.") }
        }
    }

    fun preparePhoto(uri: Uri? = null) {
        if (_photoBusy.value) return
        photoJob?.cancel()
        photoJob = viewModelScope.launch {
            _photoBusy.value = true
            try {
                val source = withContext(Dispatchers.IO) {
                    val bitmap = if (uri != null) wallpaperStore.decodePhoto(uri) else wallpaperStore.readPhoto()
                    wallpaperStore.frame(checkNotNull(bitmap))
                }
                ensureActive()
                _photoDraft.value = PhotoDraft(source, if (uri == null) _ui.value.layout.appearance.photoTransform else PhotoTransform())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message("Could not open this photo. Your wallpaper has not changed.") }
            finally { _photoBusy.value = false }
        }
    }

    fun discardPhoto() {
        if (_photoDraft.value == null) photoJob?.cancel()
        _photoDraft.value = null
    }

    fun savePhoto(transform: PhotoTransform) {
        val draft = _photoDraft.value ?: return
        if (_photoBusy.value) return
        _photoBusy.value = true
        viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    wallpaperStore.savePhoto(draft.frame.sharp)
                    wallpaperStore.load(true, _ui.value.layout.appearance.palette, transform, wallpaperWidth, wallpaperHeight)
                }
                commit(_ui.value.layout.copy(appearance = _ui.value.layout.appearance.copy(
                    usePhoto = true, photoTransform = transform)), prepared)
                _photoDraft.value = null
                message("Wallpaper saved.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message("Could not save this photo. Please try again.") }
            finally { _photoBusy.value = false }
        }
    }

    fun exportLayout(uri: Uri) {
        val state = _ui.value.layout
        writeDocument(uri, "Backup saved.") {
            // A portable layout is not permission to reopen a URI on another device.
            StateCodec.encode(state.copy(appearance = state.appearance.copy(usePhoto = false)))
        }
    }
    fun importLayout(uri: Uri) {
        viewModelScope.launch {
            try {
                val state = withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri) ?: error("Could not open this backup.")
                    stream.use { StateCodec.decode(StateStore.readLimited(it)) }
                }
                commit(LayoutOps.reconcile(state.copy(initialized = true,
                    appearance = state.appearance.copy(usePhoto = false)), _ui.value.apps.map { it.id }))
                message("Layout imported. Photos are not included in backups.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Backup rejected. Your current layout has not changed.") }
        }
    }
    fun exportDiagnostics(uri: Uri) = writeDocument(uri, "Report saved. Review it before sharing.") {
        JSONObject().apply {
            put("app", "Vitreo"); put("version", BuildConfig.VERSION_NAME)
            put("applicationId", BuildConfig.APPLICATION_ID); put("buildType", BuildConfig.BUILD_TYPE)
            put("androidApi", Build.VERSION.SDK_INT); put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL); put("powerSaver", _ui.value.powerSaver)
            put("agslPlatformAvailable", Build.VERSION.SDK_INT >= 33)
            put("agslFallbackReason", dev.vitreo.launcher.graphics.GlassDiagnostics.lastFailure)
            put("agslRenderedThisProcess", dev.vitreo.launcher.graphics.GlassDiagnostics.hardwareRefractionDrawn)
            put("reducedMotion", _ui.value.layout.appearance.reducedEffects)
            put("reducedTransparency", _ui.value.layout.appearance.opaquePanels)
            put("theme", if (_ui.value.layout.appearance.themePreset == 1) "Cyberpunk" else "iOS")
            put("externalIconPackSelected", _ui.value.layout.appearance.iconPack.isNotEmpty())
            put("refraction", _ui.value.layout.appearance.refraction.toDouble())
            put("frost", _ui.value.layout.appearance.frost.toDouble())
            put("catalogSource", "LauncherApps + MAIN/LAUNCHER; current Android user")
            put("launcherQueryActivityCount", catalog.queryStats.launcherActivities)
            put("intentQueryActivityCount", catalog.queryStats.intentActivities)
            put("catalogQueryFailure", catalog.queryStats.queryFailure)
            put("launchableActivityCount", _ui.value.apps.size)
            put("lastCrash", File(app.filesDir, "last-crash.txt").takeIf(File::exists)?.readText()?.take(32768) ?: "none")
            put("note", "No app inventory, images, tokens or layout included. Crash stack may contain technical context.")
        }.toString(2)
    }

    private fun writeDocument(uri: Uri, success: String, contents: () -> String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = contents().toByteArray(Charsets.UTF_8)
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error("Could not write this document.")
                    stream.use { it.write(bytes) }
                }
                message(success)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Could not save this document.") }
        }
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(receiver) }
        runCatching { launcherApps.unregisterCallback(appChanges) }
        runCatching { app.unregisterReceiver(powerReceiver) }
        saves.close()
        super.onCleared()
    }
}
