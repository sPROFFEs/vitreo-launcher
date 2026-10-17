package dev.vitreo.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.util.Xml
import dev.vitreo.launcher.core.IconOverride
import dev.vitreo.launcher.core.isAppId
import dev.vitreo.launcher.core.isPackageName
import org.xmlpull.v1.XmlPullParser
import java.io.FilterInputStream
import java.io.InputStream

data class IconPackInfo(val packageName: String, val label: String)
data class IconPackIndex(val mappings: Map<String, String>, val drawables: List<String>)

/** Resource-only ADW/Nova reader. Called on IO; no package code or activities are loaded. */
class IconPacks(private val context: Context) {
    private data class Loaded(val stamp: Long, val configuration: String, val resources: Resources, val index: IconPackIndex)
    private val packs = linkedMapOf<String, Loaded>()
    private var inventory = ""
    @Volatile var revision: Int = 0
        private set
    private val bitmaps = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun discover(): List<IconPackInfo> = ACTIONS.flatMap { action ->
        runCatching { context.packageManager.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
    }.distinctBy { it.activityInfo.packageName }.mapNotNull {
        runCatching { IconPackInfo(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString().take(100)) }.getOrNull()
    }.sortedBy { it.label.lowercase() }

    @Synchronized
    fun refresh(): List<IconPackInfo> {
        val available = discover()
        val signature = context.resources.configuration.toString() + available.joinToString { pack ->
            pack.packageName + ":" + runCatching { context.packageManager.getPackageInfo(pack.packageName, 0).lastUpdateTime }.getOrDefault(-1L)
        }
        if (signature != inventory) { invalidate(); inventory = signature; revision++ }
        return available
    }

    @Synchronized
    private fun load(pack: String): Loaded? = runCatching {
        if (!isPackageName(pack)) return null
        val pm = context.packageManager
        val stamp = pm.getPackageInfo(pack, 0).lastUpdateTime
        val configuration = context.resources.configuration.toString()
        packs[pack]?.takeIf { it.stamp == stamp && it.configuration == configuration }?.let { return it }
        val resources = pm.getResourcesForApplication(pack)
        fun read(name: String): IconPackIndex? {
            val xml = resources.getIdentifier(name, "xml", pack)
            if (xml != 0) return resources.getXml(xml).use { parse(it) }
            val raw = resources.getIdentifier(name, "raw", pack)
            if (raw != 0) return resources.openRawResource(raw).use { parse(it) }
            return runCatching { resources.assets.open("$name.xml").use { parse(it) } }.getOrNull()
        }
        val mappings = read("appfilter") ?: return null
        val picker = runCatching { read("drawable") }.getOrNull()
        val index = mappings.copy(drawables = (mappings.drawables + picker?.drawables.orEmpty()).distinct().sorted())
        Loaded(stamp, configuration, resources, index).also {
            if (packs.size >= 3) packs.remove(packs.keys.first())
            packs[pack] = it
        }
    }.getOrNull()

    @Synchronized
    fun names(pack: String): List<String> = load(pack)?.index?.drawables.orEmpty()

    @Synchronized
    fun resolve(id: String, pack: String, override: IconOverride?): Bitmap? {
        if (override != null) artwork(override.pack, override.drawable)?.let { return it }
        val loaded = load(pack) ?: return null
        val component = ComponentName.unflattenFromString(id)?.flattenToString() ?: return null
        val drawable = loaded.index.mappings[component] ?: return null
        return artwork(pack, drawable)
    }

    @Synchronized
    fun artwork(pack: String, name: String): Bitmap? = runCatching {
        if (!IconOverride(pack, name).valid()) return null
        val loaded = load(pack) ?: return null
        val key = "$pack:${loaded.stamp}:${loaded.configuration}:$name"
        bitmaps.get(key)?.let { return it }
        val id = loaded.resources.getIdentifier(name, "drawable", pack)
        if (id == 0) return null
        val drawable = loaded.resources.getDrawable(id, null).mutate()
        Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, it.width, it.height); drawable.draw(Canvas(it)); bitmaps.put(key, it)
        }
    }.getOrNull()

    /** Drop parsed resources as well as raster art after package/configuration changes. IO only. */
    @Synchronized
    fun invalidate() { packs.clear(); bitmaps.evictAll() }

    companion object {
        val ACTIONS = listOf("org.adw.ActivityStarter.THEMES", "com.novalauncher.THEME")
        const val MAX_XML_BYTES = 16 * 1024 * 1024
        const val MAX_ENTRIES = 100_000
        private val drawableName = Regex("[a-z0-9_]{1,128}")

        fun parse(input: InputStream): IconPackIndex {
            val bounded = object : FilterInputStream(input) {
                var remaining = MAX_XML_BYTES
                override fun read(): Int { val v = super.read(); if (v >= 0) { remaining--; require(remaining >= 0) }; return v }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    val n = `in`.read(bytes, offset, minOf(length, remaining + 1))
                    if (n > 0) { remaining -= n; require(remaining >= 0) { "Icon pack XML is too large." } }; return n
                }
            }
            val parser = Xml.newPullParser().apply { setInput(bounded, "UTF-8") }
            return parse(parser)
        }

        fun parse(parser: XmlPullParser): IconPackIndex {
            val mappings = linkedMapOf<String, String>(); val names = linkedSetOf<String>()
            var events = 0; var entries = 0
            var type = parser.eventType
            while (type != XmlPullParser.END_DOCUMENT) {
                require(++events <= 1_000_000) { "Icon pack XML is too complex." }
                require(type != XmlPullParser.DOCDECL && type != XmlPullParser.ENTITY_REF) { "Entities are not supported." }
                if (type == XmlPullParser.START_TAG && parser.name == "item") {
                    require(++entries <= MAX_ENTRIES) { "Too many icon pack entries." }
                    val name = parser.getAttributeValue(null, "drawable").orEmpty()
                    if (drawableName.matches(name)) {
                        names.add(name)
                        val raw = parser.getAttributeValue(null, "component").orEmpty().removePrefix("ComponentInfo{").removeSuffix("}")
                        val component = ComponentName.unflattenFromString(raw)?.flattenToString()
                        if (component != null && isAppId(component)) mappings.putIfAbsent(component, name)
                    }
                }
                type = parser.nextToken()
            }
            return IconPackIndex(mappings, names.toList())
        }
    }
}
