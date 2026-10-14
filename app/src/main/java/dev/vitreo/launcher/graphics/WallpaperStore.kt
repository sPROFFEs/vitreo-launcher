package dev.vitreo.launcher.graphics

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Both bitmaps are immutable once exposed. Do not recycle a bitmap still used by Compose. */
data class WallpaperFrame(val sharp: Bitmap, val frosted: Bitmap)

class WallpaperStore(private val context: Context) {
    private val file = File(context.filesDir, "wallpaper.png")

    @Synchronized
    fun load(usePhoto: Boolean, palette: Int): WallpaperFrame {
        val bitmap = if (usePhoto) runCatching {
            AtomicFile(file).openRead().use { BitmapFactory.decodeStream(it) }
        }.getOrNull() else null
        return frame(bitmap ?: procedural(palette))
    }

    @Synchronized
    fun importPhoto(uri: Uri) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width > 0 && height > 0) { "Imagen no v\u00e1lida." }
            val scale = min(1.0, 1600.0 / max(width, height))
            decoder.setTargetSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val opaque = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply { drawColor(Color.rgb(16, 27, 43)); drawBitmap(decoded, 0f, 0f, null) }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            check(opaque.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "No se pudo guardar la imagen." }
            atomic.finishWrite(stream)
        } catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    private fun procedural(palette: Int): Bitmap {
        val b = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888)
        val colors = when (palette.coerceIn(0, 3)) {
            1 -> intArrayOf(0xff2c246f.toInt(), 0xff9476d7.toInt(), 0xffedacbb.toInt())
            2 -> intArrayOf(0xff5c2447.toInt(), 0xffec8061.toInt(), 0xffffd3a0.toInt())
            3 -> intArrayOf(0xff050914.toInt(), 0xff142c48.toInt(), 0xff45657e.toInt())
            else -> intArrayOf(0xff102664.toInt(), 0xff326de0.toInt(), 0xff8fd2ed.toInt())
        }
        val canvas = Canvas(b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 100f, 720f, 1450f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 720f, 1600f, paint)
        // Original flowing ribbons: rendered once, not a bundled Apple wallpaper.
        val ribbon = Path().apply {
            moveTo(-180f, -100f); cubicTo(80f, 420f, 1180f, 100f, 800f, 780f)
            cubicTo(430f, 1380f, -250f, 960f, 250f, 1850f)
        }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 380f
        paint.shader = LinearGradient(0f, 0f, 620f, 1650f,
            intArrayOf(0x66ffffff, 0x20ffffff, 0x05ffffff), null, Shader.TileMode.CLAMP)
        canvas.drawPath(ribbon, paint)
        paint.strokeWidth = 285f
        paint.shader = LinearGradient(0f, 0f, 720f, 1400f,
            intArrayOf(0x02ffffff, 0x46d9f3ff, 0x00ffffff), null, Shader.TileMode.CLAMP)
        canvas.drawPath(ribbon, paint)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(660f, 300f, 800f,
            intArrayOf(0x55ffcee9, 0x00ffcee9), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 720f, 1600f, paint)
        paint.shader = LinearGradient(0f, 800f, 0f, 1600f,
            intArrayOf(0x000a102b, 0x880a102b.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 720f, 1600f, paint)
        return b
    }

    private fun frame(original: Bitmap): WallpaperFrame {
        // The frosted texture is prepared once, off the UI thread. No continuous CPU/GPU blur loop.
        val width = min(180, original.width)
        val height = max(1, (original.height * width.toFloat() / original.width).roundToInt())
        val small = Bitmap.createScaledBitmap(original, width, height, true)
        var pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(3) { pixels = boxBlur(pixels, width, height, 4) }
        val frost = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return WallpaperFrame(original, frost)
    }

    private fun boxBlur(source: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val horizontal = IntArray(source.size)
        val result = IntArray(source.size)
        val n = radius * 2 + 1
        fun pass(input: IntArray, out: IntArray, horizontalPass: Boolean) {
            val rows = if (horizontalPass) height else width
            val columns = if (horizontalPass) width else height
            fun index(row: Int, column: Int) = if (horizontalPass) row * width + column else column * width + row
            for (row in 0 until rows) {
                var red = 0; var green = 0; var blue = 0
                fun add(column: Int, sign: Int) {
                    val color = input[index(row, column.coerceIn(0, columns - 1))]
                    red += ((color ushr 16) and 255) * sign
                    green += ((color ushr 8) and 255) * sign
                    blue += (color and 255) * sign
                }
                for (x in -radius..radius) add(x, 1)
                for (column in 0 until columns) {
                    out[index(row, column)] = (255 shl 24) or ((red / n) shl 16) or ((green / n) shl 8) or (blue / n)
                    add(column - radius, -1); add(column + radius + 1, 1)
                }
            }
        }
        pass(source, horizontal, true); pass(horizontal, result, false)
        return result
    }
}
