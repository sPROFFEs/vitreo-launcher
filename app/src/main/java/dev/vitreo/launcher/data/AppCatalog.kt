package dev.vitreo.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

data class AppRecord(val id: String, val label: String, val packageName: String, val icon: Bitmap?)

/** Only launchable activities in the current Android user; never an inventory upload. */
class AppCatalog(private val context: Context) {
    @Suppress("DEPRECATION")
    fun load(): List<AppRecord> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())
        return pm.queryIntentActivities(intent, 0).asSequence()
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName != context.packageName }
            .map { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                AppRecord(component.flattenToString(), info.loadLabel(pm).toString(), component.packageName,
                    runCatching { info.loadIcon(pm).toBitmap(128, 128, Bitmap.Config.ARGB_8888) }.getOrNull())
            }.distinctBy { it.id }.toList()
            .sortedWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
    }
}
