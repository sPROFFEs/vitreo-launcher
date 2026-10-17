package dev.vitreo.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class AppRecord(val id: String, val label: String, val packageName: String, val icon: Bitmap?,
    val category: Int = -1, val glyph: Bitmap? = null)

/** Launchable activities in this Android user. Adaptive artwork is rendered before applying our mask. */
class AppCatalog(private val context: Context) {
    private data class Launchable(val component: ComponentName, val activity: ActivityInfo,
        val label: () -> CharSequence, val icon: () -> Drawable)
    data class QueryStats(val launcherActivities: Int = 0, val intentActivities: Int = 0, val queryFailure: String = "none")
    @Volatile var queryStats = QueryStats()
        private set
    private data class Artwork(val updated: Long, val iconResource: Int, val labelResource: Int,
        val label: String, val record: AppRecord)
    private val artwork = mutableMapOf<String, Artwork>()
    private val dirtyPackages = ConcurrentLinkedQueue<String>()
    private val clearArtwork = AtomicBoolean(false)
    private var configuration: Configuration? = null

    fun invalidate(packageName: String? = null) {
        // Package callbacks run on Main. Never wait there for the IO query's
        // bitmap/rendering lock: boot-time package changes can otherwise stall frames.
        if (packageName == null) clearArtwork.set(true) else dirtyPackages.add(packageName)
    }

    @Synchronized
    @Suppress("DEPRECATION")
    fun load(): List<AppRecord> {
        if (clearArtwork.getAndSet(false)) artwork.clear()
        val invalidated = mutableSetOf<String>()
        while (true) invalidated.add(dirtyPackages.poll() ?: break)
        if (invalidated.isNotEmpty()) artwork.entries.removeAll { it.value.record.packageName in invalidated }
        val pm = context.packageManager
        val currentConfiguration = context.resources.configuration
        if (configuration != currentConfiguration) {
            artwork.clear(); configuration = Configuration(currentConfiguration)
        }
        val collator = Collator.getInstance(Locale.ENGLISH)
        val packageUpdates = mutableMapOf<String, Long>()
        val live = mutableSetOf<String>()
        // LauncherApps supplies Android's effective component state, including icon aliases
        // enabled at runtime (Telegram, for example). ActivityInfo.enabled only describes
        // the manifest default: filtering it a second time hides those enabled aliases.
        val launcher = context.getSystemService(LauncherApps::class.java)
        val native = runCatching { launcher.getActivityList(null, Process.myUserHandle()).map { info ->
            Launchable(info.componentName, info.activityInfo, { info.label }, { info.getIcon(0) })
        } }
        val intents = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { info ->
            Launchable(ComponentName(info.activityInfo.packageName, info.activityInfo.name), info.activityInfo,
                { info.loadLabel(pm) }, { info.loadIcon(pm) })
        } }
        if (native.isFailure && intents.isFailure) throw checkNotNull(native.exceptionOrNull())
        queryStats = QueryStats(native.getOrNull()?.size ?: 0, intents.getOrNull()?.size ?: 0,
            listOfNotNull(native.exceptionOrNull(), intents.exceptionOrNull()).joinToString { it.javaClass.simpleName }.ifEmpty { "none" })
        // Union the two public launcher queries. A vendor-specific omission or one
        // failed service must not silently discard otherwise launchable activities.
        val records = (native.getOrDefault(emptyList()) + intents.getOrDefault(emptyList())).asSequence()
            .distinctBy { it.component }
            .filter { it.activity.exported && it.component.packageName != context.packageName }
            .map { info ->
                val component = info.component
                val id = component.flattenToString()
                live.add(id)
                val updated = packageUpdates.getOrPut(component.packageName) {
                    runCatching { pm.getPackageInfo(component.packageName, 0).lastUpdateTime }.getOrDefault(-1L)
                }
                // A disappearing resource during a package update must not hide every app.
                val label = runCatching { info.label().toString().trim() }.getOrDefault("")
                    .ifBlank { component.packageName }
                val activity = info.activity
                artwork[id]?.takeIf { it.updated == updated && it.iconResource == activity.iconResource &&
                    it.labelResource == activity.labelRes && it.label == label }?.let { return@map it.record }
                val images = runCatching {
                    val drawable = info.icon()
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
                    images?.first, activity.applicationInfo.category, images?.second).also {
                    artwork[id] = Artwork(updated, activity.iconResource, activity.labelRes, label, it)
                }
            }.distinctBy { it.id }.toList()
            .sortedWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
        artwork.keys.retainAll(live)
        return records
    }

    /** Confirm removal separately from a missing launcher activity. */
    @Suppress("DEPRECATION")
    fun confirmedRemovedPackages(known: Set<String>, apps: List<AppRecord>, unavailable: Set<String> = emptySet()): Set<String> {
        // Also clean old ghosts after the launcher was stopped during uninstall.
        // Both package APIs must confirm absence; disabled apps, alias changes,
        // update/storage transitions and query errors keep their placements.
        if (queryStats.queryFailure != "none") return emptySet()
        val live = apps.mapTo(mutableSetOf()) { it.packageName }
        val launcher = context.getSystemService(LauncherApps::class.java)
        fun absent(query: () -> Any?): Boolean = try { query() == null }
            catch (_: PackageManager.NameNotFoundException) { true }
            catch (_: Exception) { false }
        return known.map { it.substringBefore('/') }.distinct().filterTo(mutableSetOf()) { name ->
            name !in live && name !in unavailable && absent {
                launcher.getApplicationInfo(name, PackageManager.MATCH_DISABLED_COMPONENTS, Process.myUserHandle())
            } && absent {
                try {
                    if (Build.VERSION.SDK_INT >= 35) context.packageManager.getApplicationInfo(name,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong() or PackageManager.MATCH_ARCHIVED_PACKAGES))
                    else context.packageManager.getApplicationInfo(name, PackageManager.MATCH_DISABLED_COMPONENTS)
                } catch (_: PackageManager.NameNotFoundException) {
                    // An unmounted APK can leave an installed package setting.
                    // A deleted package retaining data has FLAG_INSTALLED clear.
                    context.packageManager.getApplicationInfo(name,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
                        .takeIf { it.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED != 0 }
                }
            }
        }
    }

    /** Preserve placement when an app swaps its launcher alias to change its icon. */
    @Suppress("DEPRECATION")
    fun aliasReplacements(known: Set<String>, apps: List<AppRecord>): Map<String, String> {
        val live = apps.mapTo(mutableSetOf()) { it.id }
        val byPackage = apps.groupBy { it.packageName }
        val pm = context.packageManager
        fun target(id: String): String? = runCatching {
            val component = checkNotNull(ComponentName.unflattenFromString(id))
            val info = pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
            info.targetActivity ?: info.name
        }.getOrNull()
        return known.asSequence().filter { it !in live }.mapNotNull { old ->
            val component = ComponentName.unflattenFromString(old) ?: return@mapNotNull null
            val candidates = byPackage[component.packageName] ?: return@mapNotNull null
            val oldTarget = target(old) ?: return@mapNotNull null
            val matches = candidates.filter { target(it.id) == oldTarget }
            matches.singleOrNull()?.let { old to it.id }
        }.toMap()
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
