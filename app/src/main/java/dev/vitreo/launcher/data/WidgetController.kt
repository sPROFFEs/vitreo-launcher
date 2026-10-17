package dev.vitreo.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import dev.vitreo.launcher.core.HomeWidgetPlacement
import org.json.JSONArray
import org.json.JSONObject

/** IDs belong to this installation and are deliberately separate from portable layout backups. */
class WidgetController(context: Context) {
    private val app = context.applicationContext
    val host = AppWidgetHost(app, 193847)
    val manager = AppWidgetManager.getInstance(app)
    private val preferences = app.getSharedPreferences("today-widgets", Context.MODE_PRIVATE)
    val ids = mutableStateListOf<Int>()
    val homePlacements = mutableStateListOf<HomeWidgetPlacement>()
    val todayIds: List<Int> get() = ids.filter { id -> homePlacements.none { it.id == id } }
    var pendingId by mutableIntStateOf(-1)
        private set
    var lastAddedId by mutableIntStateOf(-1)
        private set
    private var pendingPlacement: HomeWidgetPlacement? = null
    init {
        val owned = host.appWidgetIds.toSet()
        ids.addAll(preferences.getString("ids", "").orEmpty().split(',').mapNotNull(String::toIntOrNull)
            .distinct().filter { it in owned && manager.getAppWidgetInfo(it) != null }.take(8))
        runCatching {
            val array = JSONArray(preferences.getString("home", "[]").orEmpty().take(8192))
            repeat(minOf(array.length(), 8)) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val id = item.optInt("id", -1)
                if (id in ids && homePlacements.none { it.id == id }) homePlacements.add(HomeWidgetPlacement(id,
                    item.optInt("page"), item.optInt("column"), item.optInt("row"),
                    item.optInt("columns", 2), item.optInt("rows", 2)).sanitized())
            }
        }
        owned.filter { it !in ids }.forEach(host::deleteAppWidgetId)
        save()
    }
    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders
        .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
            it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0 }
        .sortedBy { it.loadLabel(app.packageManager).lowercase() }
    fun beginAdd(homePage: Int? = null, columns: Int = 2, rows: Int = 2): Int? {
        if (pendingId >= 0 || ids.size >= 8) return null
        return host.allocateAppWidgetId().also {
            pendingId = it
            pendingPlacement = homePage?.let { page -> HomeWidgetPlacement(it, page, columns = columns, rows = rows).sanitized() }
        }
    }
    fun finishAdd(id: Int, accepted: Boolean): Boolean {
        // A configuration result can arrive after HOME/cancellation or after a
        // different request has started. It must never complete that request.
        if (id < 0 || id != pendingId) return false
        val placement = pendingPlacement
        pendingId = -1
        pendingPlacement = null
        if (accepted && add(id)) {
            if (placement != null) placeOnHome(placement)
            lastAddedId = id
            return true
        }
        remove(id)
        return false
    }
    fun cancelAdd() {
        val id = pendingId
        pendingId = -1
        pendingPlacement = null
        if (id >= 0) remove(id)
    }
    fun add(id: Int): Boolean {
        if (id !in ids && manager.getAppWidgetInfo(id) != null && ids.size < 8) ids.add(id)
        else if (id !in ids) host.deleteAppWidgetId(id)
        save()
        return id in ids
    }
    fun placeOnHome(placement: HomeWidgetPlacement) {
        if (placement.id !in ids) return
        homePlacements.removeAll { it.id == placement.id }
        homePlacements.add(placement.sanitized())
        save()
    }
    fun moveToToday(id: Int) { homePlacements.removeAll { it.id == id }; save() }
    fun moveInToday(id: Int, delta: Int) {
        val today = todayIds
        val index = today.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in today.indices) return
        val destination = ids.indexOf(today[target])
        ids.remove(id); ids.add(destination, id)
        save()
    }
    fun remove(id: Int) { ids.remove(id); homePlacements.removeAll { it.id == id }; host.deleteAppWidgetId(id); save() }
    fun refresh() {
        if (ids.removeAll { manager.getAppWidgetInfo(it) == null }) {
            homePlacements.removeAll { it.id !in ids }; save()
        }
    }
    private fun save() {
        val home = JSONArray()
        homePlacements.forEach { placement -> home.put(JSONObject().apply {
            put("id", placement.id); put("page", placement.page); put("column", placement.column); put("row", placement.row)
            put("columns", placement.columns); put("rows", placement.rows)
        }) }
        preferences.edit().putString("ids", ids.joinToString(",")).putString("home", home.toString()).apply()
    }
}
