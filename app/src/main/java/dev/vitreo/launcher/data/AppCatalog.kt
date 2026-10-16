package dev.vitreo.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

data class AppRecord(val id: String, val label: String, val packageName: String, val icon: Bitmap?,
    val category: Int = -1, val glyph: Bitmap? = null)

/** Launchable activities in this Android user. Adaptive artwork is rendered before applying our mask. */
class AppCatalog(private val context: Context) {
    private data class Artwork(val updated: Long, val iconResource: Int, val labelResource: Int,
        val label: String, val record: AppRecord)
    private val artwork = mutableMapOf<String, Artwork>()
    private var configuration: Configuration? = null

    @Synchronized
    fun invalidate(packageName: String? = null) {
        if (packageName == null) artwork.clear()
        else artwork.entries.removeAll { it.value.record.packageName == packageName }
    }

    @Synchronized
    @Suppress("DEPRECATION")
    fun load(): List<AppRecord> {
        val pm = context.packageManager
        val currentConfiguration = context.resources.configuration
        if (configuration != currentConfiguration) {
            artwork.clear(); configuration = Configuration(currentConfiguration)
        }
        val collator = Collator.getInstance(Locale.ENGLISH)
        val packageUpdates = mutableMapOf<String, Long>()
        val live = mutableSetOf<String>()
        val records = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).asSequence()
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName != context.packageName }
            .map { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                val id = component.flattenToString()
                live.add(id)
                val updated = packageUpdates.getOrPut(component.packageName) {
                    runCatching { pm.getPackageInfo(component.packageName, 0).lastUpdateTime }.getOrDefault(-1L)
                }
                val label = info.loadLabel(pm).toString()
                artwork[id]?.takeIf { it.updated == updated && it.iconResource == info.iconResource &&
                    it.labelResource == info.labelRes && it.label == label }?.let { return@map it.record }
                val images = runCatching {
                    val drawable = info.loadIcon(pm)
                    if (drawable is AdaptiveIconDrawable) {
                        val icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                        drawable.background?.apply { setBounds(0, 0, 128, 128); draw(Canvas(icon)) }
                        val glyph = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                        drawable.foreground?.apply { setBounds(-32, -32, 160, 160); draw(Canvas(glyph)) }
                        Canvas(icon).drawBitmap(glyph, 0f, 0f, null)
                        icon to glyph
                    } else drawable.toBitmap(128, 128, Bitmap.Config.ARGB_8888) to null
                }.getOrNull()
                AppRecord(id, label, component.packageName,
                    images?.first, info.activityInfo.applicationInfo.category, images?.second).also {
                    artwork[id] = Artwork(updated, info.iconResource, info.labelRes, label, it)
                }
            }.distinctBy { it.id }.toList()
            .sortedWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
        artwork.keys.retainAll(live)
        return records
    }

    @Suppress("DEPRECATION")
    fun preferredDock(apps: List<AppRecord>): List<String> {
        val intents = listOf(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC))
        val hints = listOf(listOf("dialer", "phone"), listOf("browser", "chrome", "firefox"),
            listOf("messaging", "messages", "mms"), listOf("music", "audio", "spotify"))
        val result = mutableListOf<String>()
        intents.forEachIndexed { index, intent ->
            val preferred = context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
            val app = apps.firstOrNull { it.packageName == preferred && it.id !in result }
                ?: apps.firstOrNull { it.id !in result && hints[index].any(it.packageName::contains) }
            app?.let { result.add(it.id) }
        }
        return (result + apps.map { it.id }).distinct().take(4)
    }
}
