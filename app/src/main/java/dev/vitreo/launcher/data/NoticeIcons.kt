package dev.vitreo.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/** Actual application identities, independent of Home's icon packs and overrides. */
internal object NoticeIcons {
    private val cache = LruCache<String, Bitmap>(64)
    private val generation = java.util.concurrent.atomic.AtomicInteger()
    fun clear() { generation.incrementAndGet(); cache.evictAll() }
    fun load(context: Context, packageName: String, fallback: Icon?): Bitmap? {
        cache.get(packageName)?.let { return it }
        val token = generation.get()
        val bitmap = runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(64, 64) }.getOrNull()
        if (bitmap != null && generation.get() == token) cache.put(packageName, bitmap)
        return bitmap ?: runCatching { fallback?.loadDrawable(context)?.toBitmap(64, 64) }.getOrNull()
    }
}
