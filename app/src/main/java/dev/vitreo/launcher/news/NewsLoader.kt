package dev.vitreo.launcher.news

import dev.vitreo.launcher.core.NewsSettings
import dev.vitreo.launcher.net.HttpsClient
import dev.vitreo.launcher.net.RateLimitedException
import dev.vitreo.launcher.net.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URI
import java.util.Locale
import javax.net.ssl.SSLException

/** Separate transport from parsing/fallback so failures are testable without Google services. */
class NewsLoader(
    private val fetch: suspend (String) -> String = {
        val host = URI(it).host
        require(host in HttpsClient.newsHosts)
        // A publisher cannot redirect the service-independent source to Google.
        HttpsClient.text(it, setOf(host))
    },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(settings: NewsSettings, locale: Locale): NewsFeed {
        val s = settings.sanitized()
        suspend fun direct(fallback: Boolean): NewsFeed {
            val endpoint = s.directEndpoint(locale)
            val articles = NewsParser.parse(fetch(endpoint.url), endpoint.publisher)
            if (articles.isEmpty()) throw IOException("The publisher returned no headlines.")
            return NewsFeed(articles, clock(), provider = "direct", sourceName = endpoint.publisher, fallback = fallback)
        }
        if (s.provider == "direct") return direct(false)
        try {
            val articles = NewsParser.parse(fetch(s.url(locale)))
            if (articles.isEmpty()) throw IOException("Google News returned no headlines.")
            return NewsFeed(articles, clock(), provider = "google", sourceName = "Google News")
        } catch (e: CancellationException) { throw e }
        catch (e: SecurityException) { throw e }
        catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (!s.fallback) throw e
            return direct(true)
        }
    }
}

fun newsError(error: Exception): String = when (error) {
    is SecurityException -> "Network access is blocked. Allow Network for Vitreo in Android app settings. No Google account or Play Services is needed."
    is UnknownHostException -> "Cannot reach the news source. Check your connection, VPN or private DNS, then try again."
    is SocketTimeoutException -> "The news source took too long to respond. Try again or change News source in settings."
    is SSLException -> "A secure connection could not be established. Check the device date and your network."
    is RateLimitedException -> "The news source is temporarily limiting requests. Try again later or change News source in settings."
    is HttpStatusException -> "The news source returned HTTP ${error.code}. Try again or change News source in settings."
    else -> "Could not refresh the news. Check Network access for Vitreo, or try another News source in settings."
}
