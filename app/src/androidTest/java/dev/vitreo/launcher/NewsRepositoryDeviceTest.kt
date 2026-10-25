package dev.vitreo.launcher

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import dev.vitreo.launcher.core.NewsSettings
import dev.vitreo.launcher.news.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.UnknownHostException
import java.util.Locale

class NewsRepositoryDeviceTest {
    @Test fun persistedHeadlinesAndPhotosSurviveOfflineButNeverCrossProviders() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(app.cacheDir, "news-regression-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = folder
        }
        try {
            var requests = 0
            val xml = """<rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item><title>A saved headline</title><link>https://www.bbc.co.uk/news/article</link><media:thumbnail url="https://ichef.bbci.co.uk/photo.jpg"/></item></channel></rss>"""
            val repository = NewsRepository(context, Locale.US, NewsLoader({ requests++; xml }))
            val settings = NewsSettings(true, "US")
            val initial = repository.load(settings, true)
            assertNull(initial.error); assertEquals(1, requests)
            assertEquals(initial, repository.cached(settings))
            assertEquals(initial, repository.load(settings)); assertEquals(1, requests)
            val offline = NewsRepository(context, Locale.US, NewsLoader({ throw UnknownHostException("offline") }))
            val saved = offline.load(settings, true)
            assertNotNull(saved.error); assertEquals(initial.articles, saved.articles)
            assertEquals("https://ichef.bbci.co.uk/photo.jpg", saved.articles.single().imageUrl)
            assertEquals("BBC News", saved.sourceName)
            val google = settings.copy(provider = "google", fallback = false)
            assertTrue(offline.cached(google).articles.isEmpty())
            assertTrue(offline.load(google, true).articles.isEmpty())
            assertTrue(offline.cached(settings.copy(topic = "science")).articles.isEmpty())
            assertTrue(offline.cached(settings.copy(market = "ES")).articles.isEmpty())
        } finally { folder.deleteRecursively() }
    }
}
