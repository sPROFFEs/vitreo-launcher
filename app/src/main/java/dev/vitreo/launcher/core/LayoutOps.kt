package dev.vitreo.launcher.core

object LayoutOps {
    /** Reject additions at capacity before normalization could evict an existing app. */
    private fun canPlace(state: LauncherState, app: String): Boolean {
        val placed = state.placedApps()
        return app in placed || placed.size < MAX_ITEMS
    }

    fun reconcile(state: LauncherState, installed: List<String>): LauncherState {
        val valid = installed.filter(::isAppId).distinct().take(MAX_ITEMS)
        val s = state.sanitized()
        if (!s.initialized) return s.copy(initialized = true, dock = valid.take(DOCK_SLOTS),
            home = valid.drop(DOCK_SLOTS).map(HomeEntry::App), knownApps = valid.toSet()).sanitized()
        val placed = s.placedApps().toSet()
        val added = valid.filter { it !in s.knownApps && it !in placed }
        return s.copy(home = s.home + added.map(HomeEntry::App), knownApps = s.knownApps + valid).sanitized()
    }

    private fun withoutApp(state: LauncherState, app: String): LauncherState = state.copy(
        dock = state.dock.filterNot { it == app },
        home = state.home.mapNotNull {
            when (it) {
                is HomeEntry.App -> it.takeUnless { it.id == app }
                is HomeEntry.Folder -> it.copy(apps = it.apps.filterNot { id -> id == app })
            }
        },
    )

    fun addToHome(state: LauncherState, app: String): LauncherState {
        if (!isAppId(app) || !canPlace(state, app)) return state
        val s = withoutApp(state, app)
        return s.copy(home = s.home + HomeEntry.App(app)).sanitized()
    }

    fun removeApp(state: LauncherState, app: String) = withoutApp(state, app).sanitized()

    fun pinDock(state: LauncherState, app: String): LauncherState {
        if (!isAppId(app) || !canPlace(state, app) || app in state.dock || state.dock.size >= DOCK_SLOTS) return state
        return withoutApp(state, app).let { it.copy(dock = it.dock + app).sanitized() }
    }

    fun move(state: LauncherState, id: String, delta: Int): LauncherState {
        val index = state.home.indexOfFirst { it.id == id }
        if (index < 0 || state.home.isEmpty()) return state
        val target = (index.toLong() + delta).coerceIn(0, state.home.lastIndex.toLong()).toInt()
        val result = state.home.toMutableList()
        result.add(target, result.removeAt(index))
        return state.copy(home = result)
    }

    fun moveToSlot(state: LauncherState, id: String, target: String): LauncherState {
        val sourceIndex = state.home.indexOfFirst { it.id == id }
        val targetIndex = state.home.indexOfFirst { it.id == target }
        if (sourceIndex < 0 || targetIndex < 0) return state
        return move(state, id, targetIndex - sourceIndex)
    }

    fun moveBefore(state: LauncherState, id: String, target: String): LauncherState {
        if (id == target) return state
        val entry = state.home.firstOrNull { it.id == id } ?: return state
        if (state.home.none { it.id == target }) return state
        val result = state.home.filterNot { it.id == id }.toMutableList()
        result.add(result.indexOfFirst { it.id == target }, entry)
        return state.copy(home = result)
    }

    fun folder(state: LauncherState, first: String, second: String, folderId: String): LauncherState {
        if (first == second || !isFolderId(folderId) || state.home.any { it.id == folderId }) return state
        if (state.home.none { it is HomeEntry.App && it.id == first } ||
            state.home.none { it is HomeEntry.App && it.id == second }) return state
        val result = state.home.toMutableList()
        val at = minOf(result.indexOfFirst { it.id == first }, result.indexOfFirst { it.id == second })
        result.removeAll { it.id == first || it.id == second }
        result.add(at, HomeEntry.Folder(folderId, "Carpeta", listOf(first, second)))
        return state.copy(home = result).sanitized()
    }

    fun addToFolder(state: LauncherState, app: String, folderId: String): LauncherState {
        val folder = state.home.filterIsInstance<HomeEntry.Folder>().find { it.id == folderId } ?: return state
        if (!isAppId(app) || !canPlace(state, app) || app in folder.apps || folder.apps.size >= MAX_FOLDER_APPS) return state
        // Do not sanitize until the target folder has been updated: a one-item intermediate folder may collapse.
        val s = withoutApp(state, app)
        return s.copy(home = s.home.map {
            if (it is HomeEntry.Folder && it.id == folderId) it.copy(apps = it.apps + app) else it
        }).sanitized()
    }

    fun dissolve(state: LauncherState, folderId: String): LauncherState = state.copy(home = state.home.flatMap {
        if (it is HomeEntry.Folder && it.id == folderId) it.apps.map(HomeEntry::App) else listOf(it)
    }).sanitized()

    fun renameFolder(state: LauncherState, id: String, title: String): LauncherState = state.copy(home = state.home.map {
        if (it is HomeEntry.Folder && it.id == id) it.copy(title = title) else it
    }).sanitized()
}
