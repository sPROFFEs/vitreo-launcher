package dev.vitreo.launcher.data

import dev.vitreo.launcher.core.*
import org.json.JSONArray
import org.json.JSONObject

object StateCodec {
    const val MAX_BYTES = 1_048_576

    fun encode(state: LauncherState): String {
        val s = state.sanitized()
        return JSONObject().apply {
            put("schema", STATE_SCHEMA)
            put("initialized", s.initialized)
            put("home", JSONArray().apply {
                s.home.forEach { entry -> put(JSONObject().apply {
                    put("id", entry.id)
                    when (entry) {
                        is HomeEntry.App -> put("type", "app")
                        is HomeEntry.Folder -> { put("type", "folder"); put("title", entry.title); put("apps", JSONArray(entry.apps)) }
                    }
                }) }
            })
            put("dock", JSONArray(s.dock))
            put("knownApps", JSONArray(s.knownApps.sorted()))
            put("appearance", JSONObject().apply {
                put("iconSize", s.appearance.iconSize.toDouble())
                put("showLabels", s.appearance.showLabels)
                put("refraction", s.appearance.refraction.toDouble())
                put("frost", s.appearance.frost.toDouble())
                put("reducedEffects", s.appearance.reducedEffects)
                put("opaquePanels", s.appearance.opaquePanels)
                put("palette", s.appearance.palette)
                put("usePhoto", s.appearance.usePhoto)
                put("iconStyle", s.appearance.iconStyle)
                put("tintColor", s.appearance.tintColor)
                put("interfaceStyle", s.appearance.interfaceStyle)
                put("showWidgets", s.appearance.showWidgets)
                put("iosIcons", s.appearance.iosIcons)
            })
        }.toString(2)
    }

    fun decode(text: String): LauncherState {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "La copia supera 1 MiB." }
        val root = JSONObject(text)
        require(root.getInt("schema") == STATE_SCHEMA) { "Formato de copia no compatible." }
        val entries = root.getJSONArray("home")
        require(entries.length() <= MAX_ITEMS) { "Demasiados elementos." }
        val home = (0 until entries.length()).map { index ->
            val obj = entries.getJSONObject(index)
            when (obj.getString("type")) {
                "app" -> HomeEntry.App(obj.getString("id"))
                "folder" -> HomeEntry.Folder(obj.getString("id"), obj.getString("title"),
                    strings(obj.getJSONArray("apps"), MAX_FOLDER_APPS))
                else -> error("Tipo de elemento no compatible.")
            }
        }
        val a = root.optJSONObject("appearance") ?: JSONObject()
        return LauncherState(
            initialized = root.optBoolean("initialized", true),
            home = home,
            dock = strings(root.getJSONArray("dock"), DOCK_SLOTS),
            knownApps = strings(root.optJSONArray("knownApps") ?: JSONArray(), MAX_ITEMS * 4).toSet(),
            appearance = Appearance(
                iconSize = a.optDouble("iconSize", 60.0).toFloat(),
                showLabels = a.optBoolean("showLabels", true),
                refraction = a.optDouble("refraction", .65).toFloat(),
                frost = a.optDouble("frost", .38).toFloat(),
                reducedEffects = a.optBoolean("reducedEffects", false),
                opaquePanels = a.optBoolean("opaquePanels", false),
                palette = a.optInt("palette", 0),
                usePhoto = a.optBoolean("usePhoto", false),
                iconStyle = a.optInt("iconStyle", 0),
                tintColor = a.optInt("tintColor", 0xff8fbfff.toInt()),
                interfaceStyle = a.optInt("interfaceStyle", 0),
                showWidgets = a.optBoolean("showWidgets", true),
                iosIcons = a.optBoolean("iosIcons", true),
            ),
        ).sanitized()
    }

    private fun strings(array: JSONArray, maximum: Int): List<String> {
        require(array.length() <= maximum) { "Lista demasiado grande." }
        return (0 until array.length()).map { array.getString(it) }
    }
}
