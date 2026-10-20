package dev.vitreo.launcher.graphics

import dev.vitreo.launcher.R
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AtomicFile
import dev.vitreo.launcher.core.PhotoTransform
import dev.vitreo.launcher.core.photoPlacement
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Both bitmaps are immutable once exposed. Do not recycle a bitmap still used by Compose. */
data class WallpaperFrame(val sharp: Bitmap, val frosted: Bitmap, val brightness: Float = 0f)

class WallpaperStore(private val context: Context) {
    private val file = File(context.filesDir, "wallpaper.png")

    @Synchronized
    fun load(usePhoto: Boolean, palette: Int, transform: PhotoTransform = PhotoTransform(),
        width: Int = 720, height: Int = 1600): WallpaperFrame {
        val bitmap = if (usePhoto) readPhoto() else null
        if (bitmap == null) return frame(procedural(palette))
        val source = frame(bitmap)
        val factor = min(1f, 1600f / max(width, height).coerceAtLeast(1))
        val w = max(1, (width * factor).roundToInt()); val h = max(1, (height * factor).roundToInt())
        val composed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawPhoto(Canvas(composed), source, w.toFloat(), h.toFloat(), transform)
        return frame(composed)
    }

    @Synchronized
    fun readPhoto(): Bitmap? = runCatching {
            AtomicFile(file).openRead().use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

    @Synchronized
    fun importPhoto(uri: Uri) {
        savePhoto(decodePhoto(uri))
    }

    fun decodePhoto(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width > 0 && height > 0) { "Invalid image." }
            val scale = min(1.0, 1600.0 / max(width, height))
            decoder.setTargetSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val opaque = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply { drawColor(Color.rgb(16, 27, 43)); drawBitmap(decoded, 0f, 0f, null) }
        return opaque
    }

    @Synchronized
    fun savePhoto(opaque: Bitmap) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            check(opaque.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Could not save this image." }
            atomic.finishWrite(stream)
        } catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    /** Small gallery copy; the full source remains available for Home/Lock rendering. */
    fun thumbnail(palette: Int): Bitmap {
        val source = procedural(palette)
        val factor = min(1f, 640f / max(source.width, source.height))
        val preview = Bitmap.createScaledBitmap(source, max(1, (source.width * factor).roundToInt()),
            max(1, (source.height * factor).roundToInt()), true)
        if (preview !== source) source.recycle() // This fresh decode has never been exposed to Compose.
        return preview
    }

    fun procedural(palette: Int): Bitmap {
        builtinResource(palette)?.let { resource -> return requireNotNull(BitmapFactory.decodeResource(context.resources,
            resource,
            BitmapFactory.Options().apply { inScaled = false; inSampleSize = 1 })) }
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

    fun frame(original: Bitmap): WallpaperFrame {
        // The frosted texture is prepared once, off the UI thread. No continuous CPU/GPU blur loop.
        val width = min(180, original.width)
        val height = max(1, (original.height * width.toFloat() / original.width).roundToInt())
        val small = Bitmap.createScaledBitmap(original, width, height, true)
        var pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(3) { pixels = boxBlur(pixels, width, height, 4) }
        val frost = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        var brightness = 0f
        repeat(8) { row -> repeat(6) { column ->
            val pixel = original.getPixel((original.width * (column + .5f) / 6).toInt().coerceAtMost(original.width - 1),
                (original.height * (row + .5f) / 8).toInt().coerceAtMost(original.height - 1))
            brightness += (.2126f * Color.red(pixel) + .7152f * Color.green(pixel) + .0722f * Color.blue(pixel)) / 255f
        } }
        return WallpaperFrame(original, frost, brightness / 48f)
    }

    companion object {
        fun builtinResource(palette: Int): Int? = when (palette) {
            4 -> R.drawable.wallpaper_neon_rider
            5 -> R.drawable.wallpaper_digital_dusk
            6 -> R.drawable.wallpaper_neural_pearl
            7 -> R.drawable.wallpaper_neural_carbon
            8 -> R.drawable.wallpaper_neural_crimson
            else -> null
        }
        /** The editor and committed wallpaper use the same placement and soft extended backdrop. */
        fun drawPhoto(canvas: Canvas, source: WallpaperFrame, width: Float, height: Float, transform: PhotoTransform) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val b = source.frosted
            val cover = max(width / b.width, height / b.height)
            val background = RectF((width - b.width * cover) / 2f, (height - b.height * cover) / 2f,
                (width + b.width * cover) / 2f, (height + b.height * cover) / 2f)
            canvas.drawBitmap(b, null, background, paint)
            canvas.drawColor(0x24000000)
            val image = source.sharp
            val p = photoPlacement(image.width.toFloat(), image.height.toFloat(), width, height, transform)
            canvas.drawBitmap(image, null, RectF(p.left, p.top, p.left + p.width, p.top + p.height), paint)
        }
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
