package dev.vitreo.launcher.news

import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import dev.vitreo.launcher.net.HttpsClient

data class NewsArticle(val title: String, val publisher: String, val url: String, val time: Long, val imageUrl: String? = null)
data class NewsFeed(
    val articles: List<NewsArticle> = emptyList(), val fetchedAt: Long = 0, val error: String? = null,
    val provider: String = "direct", val sourceName: String = "Publisher RSS", val fallback: Boolean = false,
)

object NewsParser {
    fun safeLink(value: String): Boolean = runCatching { val u = URI(value)
        u.scheme == "https" && !u.host.isNullOrEmpty() && u.userInfo == null && u.port in setOf(-1,443)
    }.getOrDefault(false)
    fun safeImage(value: String): Boolean = value.length <= 4096 && HttpsClient.allowed(value, HttpsClient.newsImageHosts)

    private fun image(item: Element): String? {
        // Only publisher-owned image CDNs are accepted, including inside embedded RSS HTML.
        // Never load article pages or scripts; refuse unknown image hosts.
        val nodes = item.getElementsByTagName("*")
        val candidates = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.mapNotNull { e ->
            when (e.localName ?: e.tagName.substringAfter(':')) {
                "thumbnail" -> e.getAttribute("url")
                "content" -> e.getAttribute("url").takeIf { e.getAttribute("medium") == "image" || e.getAttribute("type").startsWith("image/") ||
                    e.getAttribute("medium").isBlank() && e.getAttribute("type").isBlank() && safeImage(it) }
                "enclosure" -> e.getAttribute("url").takeIf { e.getAttribute("type").startsWith("image/") }
                else -> null
            }
        }
        val embedded = listOf("description", "content:encoded").flatMap { name ->
            val html = item.getElementsByTagName(name).item(0)?.textContent.orEmpty().take(100_000)
            Regex("<img\\b[^>]*\\bsrc\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                .findAll(html).take(4).map { it.groupValues[1].replace("&amp;", "&") }.toList()
        }
        return (candidates + embedded).firstOrNull(::safeImage)?.let { url ->
            when (URI(url).host) {
                "ichef.bbci.co.uk", "ichef.bbci.com" -> url.replace("/standard/240/", "/standard/960/")
                "imagenes.elpais.com" -> if ("width=" !in url) url + (if ('?' in url) "&" else "?") + "width=960&height=540&smart=true" else url
                "images.tagesschau.de" -> url.replace("width=1920", "width=960")
                else -> url
            }
        }
    }

    fun parse(xml: String, publisherName: String = ""): List<NewsArticle> {
        require(xml.toByteArray().size <= 2*1024*1024)
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "External XML entities are forbidden." }
        val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
        val builder = factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
        val document = builder.parse(InputSource(StringReader(xml)))
        require(document.documentElement.tagName == "rss")
        val items = document.getElementsByTagName("item")
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply { isLenient = false }
        fun clean(value: String, limit: Int) = value.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim().take(limit)
        return (0 until minOf(items.length,100)).mapNotNull { i ->
            val e = items.item(i) as? Element ?: return@mapNotNull null
            fun field(name: String) = e.getElementsByTagName(name).item(0)?.textContent.orEmpty()
            val url = field("link").trim(); if (!safeLink(url) || url.length > 4096) return@mapNotNull null
            val publisher = publisherName.ifBlank { clean(field("source"),120) }
            val title = clean(field("title"),500).removeSuffix(if (publisher.isNotEmpty()) " - $publisher" else "\u0000")
            if (title.isBlank()) return@mapNotNull null
            NewsArticle(title,publisher,url,runCatching { date.parse(field("pubDate"))?.time ?: 0 }.getOrDefault(0),image(e))
        }.distinctBy { it.url }.take(50)
    }
}
