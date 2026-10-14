package dev.vitreo.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/** IDs belong to this installation and are deliberately separate from portable layout backups. */
class WidgetController(context: Context) {
    private val app = context.applicationContext
    val host = AppWidgetHost(app, 193847)
    val manager = AppWidgetManager.getInstance(app)
    private val preferences = app.getSharedPreferences("today-widgets", Context.MODE_PRIVATE)
    val ids = mutableStateListOf<Int>()
    init {
        val owned = host.appWidgetIds.toSet()
        ids.addAll(preferences.getString("ids", "").orEmpty().split(',').mapNotNull(String::toIntOrNull)
            .distinct().filter { it in owned && manager.getAppWidgetInfo(it) != null }.take(8))
        owned.filter { it !in ids }.forEach(host::deleteAppWidgetId)
        save()
    }
    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders
        .sortedBy { it.loadLabel(app.packageManager).lowercase() }
    fun add(id: Int) {
        if (id !in ids && manager.getAppWidgetInfo(id) != null && ids.size < 8) ids.add(id)
        else if (id !in ids) host.deleteAppWidgetId(id)
        save()
    }
    fun remove(id: Int) { ids.remove(id); host.deleteAppWidgetId(id); save() }
    fun refresh() {
        if (ids.removeAll { manager.getAppWidgetInfo(it) == null }) save()
    }
    private fun save() { preferences.edit().putString("ids", ids.joinToString(",")).apply() }
}
