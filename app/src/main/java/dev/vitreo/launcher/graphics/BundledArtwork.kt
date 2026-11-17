package dev.vitreo.launcher.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** Raster theme icons shared by Home, folders and Library; all decoding is on IO. */
internal object BundledArtwork {
    private val decodeLock = Any()
    private val cache = object : LruCache<Int, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
    }
    fun peek(id: Int): Bitmap? = cache.get(id)
    fun load(context: Context, id: Int): Bitmap? = synchronized(decodeLock) {
        cache.get(id) ?: runCatching {
            val decoded = checkNotNull(BitmapFactory.decodeResource(context.resources, id,
                BitmapFactory.Options().apply { inScaled = false }))
            val scale = minOf(1f, 320f / maxOf(decoded.width, decoded.height))
            if (scale == 1f) decoded else Bitmap.createScaledBitmap(decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1), true).also { decoded.recycle() }
        }.getOrNull()?.also { cache.put(id, it) }
    }
    fun clear() { cache.evictAll() } // Never wait for IO or recycle art still on screen.
}
