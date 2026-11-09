package dev.vitreo.launcher.graphics

import android.content.Context
import android.graphics.*
import android.util.AtomicFile
import dev.vitreo.launcher.core.DepthMaskPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.max

/** Optional still-image processing only. The bundled model/runtime never use a network service. */
object DepthWallpaper {
    private const val SIZE = 257
    private val mutex = Mutex()
    private var cachedKey: String? = null
    private var cachedMask: Bitmap? = null

    suspend fun mask(context: Context, source: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = digest(source)
            if (cachedKey == key) return@withLock cachedMask
            val result = try {
                val directory = File(context.cacheDir, "wallpaper-depth-v1").apply { mkdirs() }
                val file = File(directory, "$key.png")
                val stored = if (file.isFile) AtomicFile(file).openRead().use { BitmapFactory.decodeStream(it) } else null
                val bitmap = stored?.takeIf { it.width == SIZE && it.height == SIZE } ?: infer(context, source).also { image ->
                    currentCoroutineContext().ensureActive()
                    val atomic = AtomicFile(file); val stream = atomic.startWrite()
                    try { check(image.compress(Bitmap.CompressFormat.PNG, 100, stream)); atomic.finishWrite(stream) }
                    catch (e: Exception) { atomic.failWrite(stream); throw e }
                    directory.listFiles()?.filter { it.name != file.name }?.forEach { it.delete() }
                }
                val pixels = IntArray(SIZE * SIZE)
                bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
                bitmap.takeIf { DepthMaskPolicy.usable(pixels.map { Color.alpha(it) }.toIntArray()) }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { null }
              catch (_: LinkageError) { null }
              catch (_: OutOfMemoryError) { null }
            cachedKey = key; cachedMask = result
            result
        }
    }

    private fun infer(context: Context, source: Bitmap): Bitmap {
        val bytes = context.assets.open("depth/deeplab_v3.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
        val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        val image = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        image.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (image !== source) image.recycle()
        pixels.forEach { color ->
            input.putFloat(Color.red(color) / 127.5f - 1f)
            input.putFloat(Color.green(color) / 127.5f - 1f)
            input.putFloat(Color.blue(color) / 127.5f - 1f)
        }
        input.rewind()
        val output = ByteBuffer.allocateDirect(SIZE * SIZE * 21 * 4).order(ByteOrder.nativeOrder())
        Interpreter(model, Interpreter.Options().setNumThreads(2)).use { it.run(input, output) }
        output.rewind()
        val scores = FloatArray(21)
        for (pixel in pixels.indices) {
            var highest = Float.NEGATIVE_INFINITY
            for (i in scores.indices) { scores[i] = output.float; highest = max(highest, scores[i]) }
            var sum = 0f
            for (i in scores.indices) { scores[i] = exp(scores[i] - highest); sum += scores[i] }
            // PASCAL VOC class 15 = person; normalize logits, not a guessed score threshold.
            pixels[pixel] = Color.argb(DepthMaskPolicy.alpha(scores[15] / sum), 255, 255, 255)
        }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    /** Reuse the exact rendered crop/dim and the lock's readability gradient. */
    fun foreground(mask: Bitmap, source: Bitmap, background: Bitmap): Bitmap {
        val width = background.width; val height = background.height
        val result = background.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), intArrayOf(0x66000000, 0x00000000, 0x6b000000), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        val cover = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val w = source.width * cover; val h = source.height * cover
        canvas.drawBitmap(mask, null, RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f), paint)
        return result
    }

    private fun digest(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("${bitmap.width}:${bitmap.height}:deeplab-v1".toByteArray())
        val row = IntArray(bitmap.width)
        val bytes = ByteBuffer.allocate(bitmap.width * 4)
        repeat(bitmap.height) { y ->
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            bytes.clear(); row.forEach(bytes::putInt); digest.update(bytes.array())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
