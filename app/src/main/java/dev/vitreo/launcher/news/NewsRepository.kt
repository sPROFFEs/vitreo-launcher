package dev.vitreo.launcher.news

import android.content.Context
import android.util.AtomicFile
import dev.vitreo.launcher.core.NewsSettings
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

interface NewsSource {
    suspend fun cached(settings: NewsSettings): NewsFeed = NewsFeed()
    suspend fun load(settings: NewsSettings, force: Boolean = false): NewsFeed
}

class NewsRepository(context: Context, private val locale: Locale = Locale.getDefault(), private val loader: NewsLoader = NewsLoader()) : NewsSource {
    private val directory = File(context.applicationContext.cacheDir, "news").apply { mkdirs() }
    private fun file(settings: NewsSettings): AtomicFile {
        val s = settings.sanitized()
        return AtomicFile(File(directory, "${s.provider}-${s.fallback}-${s.edition(locale)}-${s.topic}.json"))
    }

    override suspend fun cached(settings: NewsSettings): NewsFeed = withContext(Dispatchers.IO) {
        runCatching {
            val cache = file(settings)
            require(cache.baseFile.length() <= 262144)
            val obj = JSONObject(cache.readFully().toString(Charsets.UTF_8))
            val items = obj.getJSONArray("articles")
            require(items.length() <= 50)
            NewsFeed((0 until items.length()).mapNotNull { i ->
                val a = items.getJSONObject(i)
                a.getString("url").takeIf(NewsParser::safeLink)?.let {
                    NewsArticle(a.getString("title").take(500), a.getString("publisher").take(120), it, a.optLong("time"),
                        a.optString("image").takeIf(NewsParser::safeImage))
                }
            }, obj.getLong("fetchedAt"), provider = obj.getString("provider"), sourceName = obj.getString("sourceName").take(120), fallback = obj.optBoolean("fallback"))
        }.getOrDefault(NewsFeed())
    }

    override suspend fun load(settings: NewsSettings, force: Boolean): NewsFeed = withContext(Dispatchers.IO) {
        val cache = cached(settings)
        val now = System.currentTimeMillis()
        if (!force && cache.articles.isNotEmpty() && now - cache.fetchedAt in 0..20*60*1000L) return@withContext cache
        try {
            val result = loader.load(settings, locale)
            currentCoroutineContext().ensureActive()
            val encoded = JSONObject().put("fetchedAt", result.fetchedAt).put("provider", result.provider)
                .put("sourceName", result.sourceName).put("fallback", result.fallback).put("articles", JSONArray().apply {
                    result.articles.forEach { put(JSONObject().put("title", it.title).put("publisher", it.publisher)
                        .put("url", it.url).put("time", it.time).put("image", it.imageUrl.orEmpty())) }
                }).toString().toByteArray()
            if (encoded.size <= 262144) runCatching {
                val target = file(settings)
                val stream = target.startWrite()
                try { stream.write(encoded); target.finishWrite(stream) }
                catch (e: Exception) { target.failWrite(stream); throw e }
                directory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                    ?.sortedByDescending { it.lastModified() }?.drop(12)?.forEach { it.delete() }
            }
            result
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { cache.copy(error = newsError(e)) }
    }
}
