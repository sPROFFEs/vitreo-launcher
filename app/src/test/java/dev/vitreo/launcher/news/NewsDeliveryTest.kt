package dev.vitreo.launcher.news

import dev.vitreo.launcher.core.NewsSettings
import dev.vitreo.launcher.data.StateCodec
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.LauncherState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.Locale

class NewsDeliveryTest {
    private fun rss(media: String = "") = """<rss xmlns:m="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><item><title>Story</title><link>https://example.com/story</link>$media</item></channel></rss>"""

    @Test fun defaultSourceAndEveryDirectEditionAvoidGoogle() {
        assertEquals("direct", NewsSettings().provider)
        for (market in NewsSettings.MARKETS.keys) for (topic in NewsSettings.TOPICS.keys) {
            val s = NewsSettings(market = market, topic = topic)
            val endpoint = s.directEndpoint(Locale.US)
            assertTrue(dev.vitreo.launcher.net.HttpsClient.allowed(endpoint.url, dev.vitreo.launcher.net.HttpsClient.newsHosts))
            assertFalse(endpoint.url.contains("google"))
        }
        assertEquals("EL PAÍS", NewsSettings(market = "MX").directEndpoint().publisher)
        assertEquals("Le Monde", NewsSettings(market = "FR").directEndpoint().publisher)
        assertEquals("tagesschau", NewsSettings(market = "DE").directEndpoint().publisher)
        assertEquals("direct", NewsSettings(provider = "https://evil.example").sanitized().provider)
    }

    @Test fun parsesNamespacedPhotosEnclosuresAndEmbeddedPublisherImages() {
        val bbc = NewsParser.parse(rss("""<m:thumbnail url="https://ichef.bbci.co.uk/ace/standard/240/photo.jpg"/>"""), "BBC News").single()
        assertEquals("BBC News", bbc.publisher)
        assertEquals("https://ichef.bbci.co.uk/ace/standard/960/photo.jpg", bbc.imageUrl)
        assertTrue(NewsParser.parse(rss("""<enclosure url="https://img.lemde.fr/photo.jpg" type="image/jpeg"/>""")).single().imageUrl!!.contains("lemde"))
        val html = """<content:encoded>&lt;p&gt;&lt;img src="https://images.tagesschau.de/photo.jpg?width=1920&amp;amp;x=1" /&gt;&lt;/p&gt;</content:encoded>"""
        assertEquals("https://images.tagesschau.de/photo.jpg?width=960&x=1", NewsParser.parse(rss(html)).single().imageUrl)
        val media = """<m:content url="https://imagenes.elpais.com/photo.jpg?auth=token" medium="image" type="image/jpeg"/>"""
        assertTrue(NewsParser.parse(rss(media)).single().imageUrl!!.endsWith("&width=960&height=540&smart=true"))
    }

    @Test fun ignoresVideosTrackersUnsafeImagesAndMissingPhotos() {
        for (url in listOf("http://ichef.bbci.co.uk/a.jpg", "https://evil.example/a.jpg", "https://127.0.0.1/a.jpg", "https://ichef.bbci.co.uk.evil.example/a.jpg", "https://user@ichef.bbci.co.uk/a.jpg")) {
            assertNull(NewsParser.parse(rss("""<m:thumbnail url="$url"/>""")).single().imageUrl)
        }
        assertNull(NewsParser.parse(rss("""<m:content url="https://ichef.bbci.co.uk/video.mp4" type="video/mp4"/>""")).single().imageUrl)
        assertNull(NewsParser.parse(rss()).single().imageUrl)
    }

    @Test fun directModeNeverContactsGoogleAndHasCorrectAttribution() = runBlocking {
        val requests = mutableListOf<String>()
        val loader = NewsLoader({ requests += it; rss() }, { 123 })
        val feed = loader.load(NewsSettings(market = "ES"), Locale.US)
        assertEquals(1, requests.size); assertFalse(requests.single().contains("google"))
        assertEquals("EL PAÍS", feed.sourceName); assertEquals("EL PAÍS", feed.articles.single().publisher)
        assertEquals(123L, feed.fetchedAt); assertFalse(feed.fallback)
    }

    @Test fun googleFailureFallsBackToIndependentFeedWithoutDiscardingSelection() = runBlocking {
        val requests = mutableListOf<String>()
        val loader = NewsLoader({ url -> requests += url; if (url.contains("google")) throw IOException("blocked") else rss() })
        val settings = NewsSettings(market = "ES", provider = "google")
        val feed = loader.load(settings, Locale.US)
        assertEquals(2, requests.size); assertEquals("google", settings.provider)
        assertEquals("direct", feed.provider); assertTrue(feed.fallback); assertNull(feed.error)
        assertEquals("EL PAÍS", feed.sourceName)
    }

    @Test fun successfulGoogleRequestDoesNotContactOtherProviders() = runBlocking {
        val requests = mutableListOf<String>()
        val feed = NewsLoader({ requests += it; rss() }).load(NewsSettings(provider = "google"), Locale.US)
        assertEquals(1, requests.size); assertTrue(requests.single().contains("news.google.com"))
        assertEquals("google", feed.provider); assertFalse(feed.fallback)
    }

    @Test fun optingOutOfFallbackPreventsPublisherConnections() = runBlocking {
        val requests = mutableListOf<String>()
        val loader = NewsLoader({ requests += it; throw IOException("blocked") })
        assertTrue(runCatching { loader.load(NewsSettings(provider = "google", fallback = false), Locale.US) }.isFailure)
        assertEquals(1, requests.size)
    }

    @Test fun cancellationAndNetworkPermissionDenialDoNotStartFallback() = runBlocking {
        for (failure in listOf(CancellationException("hidden"), SecurityException("Network denied"))) {
            var calls = 0
            val loader = NewsLoader({ calls++; throw failure })
            val result = runCatching { loader.load(NewsSettings(provider = "google"), Locale.US) }
            assertSame(failure, result.exceptionOrNull()); assertEquals(1, calls)
        }
        assertTrue(newsError(SecurityException()).contains("Allow Network"))
        assertTrue(newsError(UnknownHostException()).contains("DNS"))
    }

    @Test fun backupRetainsProviderImageAndFallbackPreferencesAndMigratesOldNews() {
        val state = LauncherState(appearance = Appearance(news = NewsSettings(true, "ES", "technology", "google", false, false)))
        assertEquals(state.sanitized(), StateCodec.decode(StateCodec.encode(state)))
        val old = JSONObject(StateCodec.encode(state))
        val news = old.getJSONObject("appearance").getJSONObject("news")
        listOf("provider", "fallback", "images").forEach { news.remove(it) }
        val migrated = StateCodec.decode(old.toString()).appearance.news
        assertTrue(migrated.enabled); assertEquals("ES", migrated.market); assertEquals("technology", migrated.topic)
        assertEquals("direct", migrated.provider); assertTrue(migrated.images); assertTrue(migrated.fallback)
    }
}
