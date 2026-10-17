package dev.vitreo.launcher.graphics

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import dev.vitreo.launcher.core.Appearance
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One rendering path for the local fallback and Android's Home wallpaper. */
object HomeBackground {
    fun render(frame: WallpaperFrame, appearance: Appearance, width: Int, height: Int, density: Float): Bitmap {
        val scale = min(1f, 1600f / max(width, height).coerceAtLeast(1))
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap); val source = frame.sharp
            val cover = max(w.toFloat() / source.width, h.toFloat() / source.height)
            val left = (w - source.width * cover) / 2f; val top = (h - source.height * cover) / 2f
            canvas.drawBitmap(source, null, RectF(left, top, left + source.width * cover, top + source.height * cover), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawColor(Color.argb((appearance.wallpaperDim * 255).roundToInt(), 0, 0, 0))
            if (appearance.themePreset == 1) drawCyber(canvas, w.toFloat(), h.toFloat(), density * scale, appearance.usePhoto)
        }
    }

    fun drawCyber(canvas: Canvas, width: Float, height: Float, density: Float, photo: Boolean) {
        canvas.drawColor(if (photo) 0xc7090d13.toInt() else 0xff090d13.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; color = 0x0663f5f7 }
        val step = (44 * density).coerceAtLeast(1f)
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height, paint); x += step }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width, y, paint); y += step }
        paint.color = 0x4dff4b5c; paint.strokeWidth = density
        canvas.drawLine(width * .7f, height * .08f, width * .94f, height * .08f, paint)
        canvas.drawLine(width * .94f, height * .08f, width, height * .08f + width * .06f, paint)
        paint.color = 0x2e63f5f7
        canvas.drawLine(0f, height * .82f, width * .18f, height * .82f, paint)
    }
}

/** Only called after Vitreo holds HOME and synchronization is enabled. No wallpaper reads. */
class SystemHomeWallpaper(context: Context) {
    private val manager = WallpaperManager.getInstance(context)
    private val preferences = context.getSharedPreferences("system-home-wallpaper", Context.MODE_PRIVATE)
    private var lastBitmap: Bitmap? = null
    private var lastDigest = ""

    @Synchronized
    fun apply(bitmap: Bitmap): Boolean {
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return false
        if (lastBitmap !== bitmap) {
            val bytes = ByteBuffer.allocate(bitmap.byteCount).also(bitmap::copyPixelsToBuffer).array()
            lastDigest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + ":${bitmap.width}:${bitmap.height}"
            lastBitmap = bitmap
        }
        val digest = lastDigest
        val current = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        if (current > 0 && preferences.getInt("id", -1) == current && preferences.getString("digest", "") == digest) return true
        val id = manager.setBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), false, WallpaperManager.FLAG_SYSTEM)
        if (id <= 0) return false
        preferences.edit().putInt("id", id).putString("digest", digest).apply()
        return true
    }
}
