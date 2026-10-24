package dev.vitreo.launcher.data

import dev.vitreo.launcher.core.LockScreenSettings
import org.json.JSONObject

object LockScreenCodec {
    fun encode(value: LockScreenSettings): JSONObject = value.sanitized().let { a -> JSONObject().apply {
        put("theme", a.theme); put("clockSize", a.clockSize.toDouble()); put("clockStyle", a.clockStyle)
        put("clockHeight", a.clockHeight.toDouble())
        put("clockColor", a.clockColor); put("glassClock", a.glassClock); put("showDate", a.showDate)
        put("showBattery", a.showBattery); put("showAlarm", a.showAlarm)
        put("showNotifications", a.showNotifications); put("idleTimeoutSeconds", a.idleTimeoutSeconds)
        put("notificationCount", a.notificationCount); put("wallpaper", a.wallpaper)
        put("dim", a.dim.toDouble()); put("immersive", a.immersive)
        put("showSystemUnlock", a.showSystemUnlock)
    } }
    fun decode(a: JSONObject?): LockScreenSettings {
        if (a == null) return LockScreenSettings()
        return LockScreenSettings(theme = a.optInt("theme", 0), clockSize = a.optDouble("clockSize", 104.0).toFloat(),
            clockStyle = a.optInt("clockStyle", 0), clockColor = a.optInt("clockColor", 0xfff5f8ff.toInt()),
            clockHeight = a.optDouble("clockHeight", 1.0).toFloat(),
            glassClock = a.optBoolean("glassClock", true), showDate = a.optBoolean("showDate", true),
            showBattery = a.optBoolean("showBattery", true), showAlarm = a.optBoolean("showAlarm", true),
            showNotifications = a.optBoolean("showNotifications", true), idleTimeoutSeconds = a.optInt("idleTimeoutSeconds", 10),
            notificationCount = a.optBoolean("notificationCount", false), wallpaper = a.optInt("wallpaper", -1),
            dim = a.optDouble("dim", .18).toFloat(), immersive = a.optBoolean("immersive", false),
            showSystemUnlock = a.optBoolean("showSystemUnlock", false)).sanitized()
    }
}
