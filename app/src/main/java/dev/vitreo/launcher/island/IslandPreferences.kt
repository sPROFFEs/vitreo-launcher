package dev.vitreo.launcher.island

import android.content.Context
import dev.vitreo.launcher.core.IslandLayout
import org.json.JSONObject

/** Device-local like Aoede: neither overlay activation nor placement comes from a layout backup. */
internal object IslandPreferences {
    private fun prefs(c:Context)=c.getSharedPreferences("notification-island-local",Context.MODE_PRIVATE)
    fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
    fun enabled(c:Context,v:Boolean) { prefs(c).edit().putBoolean("enabled",v).apply() }
    fun read(c:Context):IslandLayout = runCatching { decode(JSONObject(prefs(c).getString("layout","{}").orEmpty())) }.getOrDefault(IslandLayout())
    fun write(c:Context,v:IslandLayout) { prefs(c).edit().putString("layout",encode(v).toString()).apply() }
    internal fun encode(value:IslandLayout):JSONObject { val a=value.sanitized(); return JSONObject().apply {
        put("width",a.width);put("height",a.height);put("top",a.top);put("horizontal",a.horizontal)
        put("handleWidth",a.handleWidth);put("handleHeight",a.handleHeight);put("handleTop",a.handleTop);put("handleHorizontal",a.handleHorizontal)
        put("touchWidth",a.touchWidth);put("touchHeight",a.touchHeight);put("touchTop",a.touchTop);put("touchHorizontal",a.touchHorizontal)
        put("coexistence",a.coexistence);put("reserveAlways",a.reserveAlways);put("reservedBottom",a.reservedBottom);put("duration",a.duration)
    } }
    internal fun decode(o:JSONObject)=IslandLayout(o.optInt("width",156),o.optInt("height",48),o.optInt("top",8),o.optInt("horizontal",0),
        o.optInt("handleWidth",32),o.optInt("handleHeight",12),o.optInt("handleTop",-1),o.optInt("handleHorizontal",0),
        o.optInt("touchWidth",156),o.optInt("touchHeight",48),o.optInt("touchTop",-1),o.optInt("touchHorizontal",0),
        o.optInt("coexistence",0),o.optBoolean("reserveAlways",false),o.optInt("reservedBottom",112),o.optInt("duration",6)).sanitized()
}
