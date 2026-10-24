package dev.vitreo.launcher.news

import android.content.Context
import android.util.AtomicFile
import dev.vitreo.launcher.core.NewsSettings
import dev.vitreo.launcher.net.HttpsClient
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

interface NewsSource {
    suspend fun cached(settings: NewsSettings): NewsFeed = NewsFeed()
    suspend fun load(settings: NewsSettings, force: Boolean = false): NewsFeed
}
class NewsRepository(context: Context, private val locale: Locale = Locale.getDefault()) : NewsSource {
    private val directory = File(context.applicationContext.cacheDir,"news").apply { mkdirs() }
    private fun file(s: NewsSettings) = AtomicFile(File(directory,"${s.sanitized().edition(locale)}-${s.sanitized().topic}.json"))
    override suspend fun cached(settings: NewsSettings): NewsFeed = withContext(Dispatchers.IO) {
        runCatching {
            val cache = file(settings); require(cache.baseFile.length() <= 262144)
            val obj = JSONObject(cache.readFully().toString(Charsets.UTF_8)); val items = obj.getJSONArray("articles")
            require(items.length() <= 50)
            NewsFeed((0 until items.length()).mapNotNull { i -> val a = items.getJSONObject(i)
                a.getString("url").takeIf(NewsParser::safeLink)?.let { NewsArticle(a.getString("title").take(500),a.getString("publisher").take(120),it,a.optLong("time")) }
            }, obj.getLong("fetchedAt"))
        }.getOrDefault(NewsFeed())
    }
    override suspend fun load(settings: NewsSettings, force: Boolean): NewsFeed = withContext(Dispatchers.IO) {
        val cache = cached(settings); val now = System.currentTimeMillis()
        if (!force && cache.articles.isNotEmpty() && now - cache.fetchedAt in 0..20*60*1000L) return@withContext cache
        try {
            val articles = NewsParser.parse(HttpsClient.text(settings.url(locale), HttpsClient.newsHosts))
            require(articles.isNotEmpty()) { "No headlines were returned." }
            val result = NewsFeed(articles,now)
            val encoded = JSONObject().put("fetchedAt",now).put("articles",JSONArray().apply { articles.forEach {
                put(JSONObject().put("title",it.title).put("publisher",it.publisher).put("url",it.url).put("time",it.time))
            } }).toString().toByteArray()
            if (encoded.size <= 262144) runCatching {
                val target = file(settings); val stream = target.startWrite()
                try { stream.write(encoded); target.finishWrite(stream) } catch (e: Exception) { target.failWrite(stream); throw e }
            }
            result
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { cache.copy(error = "Could not refresh the news. Check your connection and try again.") }
    }
}
