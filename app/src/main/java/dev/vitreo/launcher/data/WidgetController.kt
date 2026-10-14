package dev.vitreo.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf

/** IDs belong to this installation and are deliberately separate from portable layout backups. */
class WidgetController(context: Context) {
    private val app = context.applicationContext
    val host = AppWidgetHost(app, 193847)
    val manager = AppWidgetManager.getInstance(app)
    private val preferences = app.getSharedPreferences("today-widgets", Context.MODE_PRIVATE)
    val ids = mutableStateListOf<Int>()
    var pendingId by mutableIntStateOf(-1)
        private set
    init {
        val owned = host.appWidgetIds.toSet()
        ids.addAll(preferences.getString("ids", "").orEmpty().split(',').mapNotNull(String::toIntOrNull)
            .distinct().filter { it in owned && manager.getAppWidgetInfo(it) != null }.take(8))
        owned.filter { it !in ids }.forEach(host::deleteAppWidgetId)
        save()
    }
    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders
        .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
            it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0 }
        .sortedBy { it.loadLabel(app.packageManager).lowercase() }
    fun beginAdd(): Int? {
        if (pendingId >= 0 || ids.size >= 8) return null
        return host.allocateAppWidgetId().also { pendingId = it }
    }
    fun finishAdd(id: Int, accepted: Boolean): Boolean {
        // A configuration result can arrive after HOME/cancellation or after a
        // different request has started. It must never complete that request.
        if (id < 0 || id != pendingId) return false
        pendingId = -1
        if (accepted) return add(id)
        remove(id)
        return false
    }
    fun cancelAdd() {
        val id = pendingId
        pendingId = -1
        if (id >= 0) remove(id)
    }
    fun add(id: Int): Boolean {
        if (id !in ids && manager.getAppWidgetInfo(id) != null && ids.size < 8) ids.add(id)
        else if (id !in ids) host.deleteAppWidgetId(id)
        save()
        return id in ids
    }
    fun remove(id: Int) { ids.remove(id); host.deleteAppWidgetId(id); save() }
    fun refresh() {
        if (ids.removeAll { manager.getAppWidgetInfo(it) == null }) save()
    }
    private fun save() { preferences.edit().putString("ids", ids.joinToString(",")).apply() }
}
