package dev.vitreo.launcher.news

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.LruCache
import dev.vitreo.launcher.net.HttpsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

fun interface NewsImageSource {
    suspend fun load(url: String): Bitmap?
}

/** Visible-card loads only, two connections, bounded disk/memory, no cookies or article scraping. */
class NewsImages(context: Context) : NewsImageSource {
    private val directory = File(context.applicationContext.cacheDir, "news-images").apply { mkdirs() }
    private val permits = Semaphore(2)
    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failed = LruCache<String, Long>(100)

    override suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!NewsParser.safeImage(url)) return@withContext null
        memory.get(url)?.let { return@withContext it }
        if (failed.get(url)?.let { System.currentTimeMillis() - it in 0..300_000L } == true) return@withContext null
        permits.withPermit {
            currentCoroutineContext().ensureActive()
            memory.get(url)?.let { return@withPermit it }
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val target = AtomicFile(File(directory, key))
            try {
                val saved = runCatching { if (target.baseFile.length() in 1..MAX_BYTES.toLong()) target.readFully() else null }.getOrNull()
                val bytes = saved ?: HttpsClient.bytes(url, HttpsClient.newsImageHosts, MAX_BYTES, image = true)
                currentCoroutineContext().ensureActive()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: error("Invalid news image")
                memory.put(url, image)
                if (saved == null) runCatching {
                    val stream = target.startWrite()
                    try { stream.write(bytes); target.finishWrite(stream) }
                    catch (e: Exception) { target.failWrite(stream); throw e }
                    var total = 0L
                    directory.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}")) }
                        ?.sortedByDescending { it.lastModified() }?.forEach { file ->
                            total += file.length()
                            if (total > 24L * 1024 * 1024) file.delete()
                        }
                }
                image
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { target.delete(); failed.put(url, System.currentTimeMillis()); null }
        }
    }

    companion object { private const val MAX_BYTES = 3 * 1024 * 1024 }
}
