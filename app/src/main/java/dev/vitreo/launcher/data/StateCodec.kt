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
                put("photoZoom", s.appearance.photoTransform.zoom.toDouble())
                put("photoX", s.appearance.photoTransform.x.toDouble())
                put("photoY", s.appearance.photoTransform.y.toDouble())
                put("wallpaperDim", s.appearance.wallpaperDim.toDouble())
                put("themePreset", s.appearance.themePreset)
                put("cyberEffects", s.appearance.cyberEffects)
                put("cyberImmersive", s.appearance.cyberImmersive)
                put("controlWide", JSONArray(s.appearance.controlWide.toList()))
                put("syncWallpaper", s.appearance.syncWallpaper)
                put("controlCenter", s.appearance.controlCenter)
                put("homeStatusBar", s.appearance.homeStatusBar)
                put("notificationCenter", s.appearance.notificationCenter)
                put("mediaIntegration", s.appearance.mediaIntegration)
                put("panelGestureHeight", s.appearance.panelGestureHeight.toDouble())
                put("controlShortcuts", JSONArray(s.appearance.controlShortcuts))
                put("homeBrightness", s.appearance.homeBrightness.toDouble())
                put("todaySections", JSONArray(s.appearance.todaySections))
                put("hiddenTodaySections", JSONArray(s.appearance.hiddenTodaySections.toList()))
                put("iconPack", s.appearance.iconPack)
                put("iconOverrides", JSONObject().apply {
                    s.appearance.iconOverrides.forEach { (id, icon) ->
                        put(id, JSONObject().put("pack", icon.pack).put("drawable", icon.drawable))
                    }
                })
            })
        }.toString(2)
    }

    fun decode(text: String): LauncherState {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds 1 MiB." }
        val root = JSONObject(text)
        require(root.getInt("schema") == STATE_SCHEMA) { "Unsupported backup format." }
        val entries = root.getJSONArray("home")
        require(entries.length() <= MAX_ITEMS) { "Too many items." }
        val home = (0 until entries.length()).map { index ->
            val obj = entries.getJSONObject(index)
            when (obj.getString("type")) {
                "app" -> HomeEntry.App(obj.getString("id"))
                "folder" -> HomeEntry.Folder(obj.getString("id"), obj.getString("title"),
                    strings(obj.getJSONArray("apps"), MAX_FOLDER_APPS))
                else -> error("Unsupported item type.")
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
                photoTransform = PhotoTransform(a.optDouble("photoZoom", 1.0).toFloat(),
                    a.optDouble("photoX", 0.0).toFloat(), a.optDouble("photoY", 0.0).toFloat()),
                wallpaperDim = a.optDouble("wallpaperDim", .12).toFloat(),
                themePreset = a.optInt("themePreset", 0),
                cyberEffects = a.optBoolean("cyberEffects", true),
                cyberImmersive = a.optBoolean("cyberImmersive", false),
                controlWide = a.optJSONArray("controlWide")?.let { strings(it, 32).toSet() } ?: emptySet(),
                syncWallpaper = a.optBoolean("syncWallpaper", true),
                controlCenter = a.optBoolean("controlCenter", true),
                homeStatusBar = a.optBoolean("homeStatusBar", false),
                notificationCenter = a.optBoolean("notificationCenter", false),
                mediaIntegration = a.optBoolean("mediaIntegration", false),
                panelGestureHeight = a.optDouble("panelGestureHeight", 112.0).toFloat(),
                controlShortcuts = a.optJSONArray("controlShortcuts")?.let { strings(it, 32) } ?: DEFAULT_CONTROL_SHORTCUTS,
                homeBrightness = a.optDouble("homeBrightness", -1.0).toFloat(),
                todaySections = a.optJSONArray("todaySections")?.let { strings(it, 32) } ?: DEFAULT_TODAY_SECTIONS,
                hiddenTodaySections = a.optJSONArray("hiddenTodaySections")?.let { strings(it, 32).toSet() }.orEmpty(),
                iconPack = a.optString("iconPack", ""),
                iconOverrides = a.optJSONObject("iconOverrides")?.let { overrides ->
                    require(overrides.length() <= MAX_ITEMS) { "Too many icon overrides." }
                    overrides.keys().asSequence().mapNotNull { id -> overrides.optJSONObject(id)?.let {
                        id to IconOverride(it.optString("pack"), it.optString("drawable"))
                    } }.toMap()
                }.orEmpty(),
            ),
        ).sanitized()
    }

    private fun strings(array: JSONArray, maximum: Int): List<String> {
        require(array.length() <= maximum) { "List is too large." }
        return (0 until array.length()).map { array.getString(it) }
    }
}
