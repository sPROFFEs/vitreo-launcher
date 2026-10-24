package dev.vitreo.launcher.news

import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader

data class NewsArticle(val title: String, val publisher: String, val url: String, val time: Long)
data class NewsFeed(val articles: List<NewsArticle> = emptyList(), val fetchedAt: Long = 0, val error: String? = null)

object NewsParser {
    fun safeLink(value: String): Boolean = runCatching { val u = URI(value)
        u.scheme == "https" && !u.host.isNullOrEmpty() && u.userInfo == null && u.port in setOf(-1,443)
    }.getOrDefault(false)
    fun parse(xml: String): List<NewsArticle> {
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
            val publisher = clean(field("source"),120)
            val title = clean(field("title"),500).removeSuffix(if (publisher.isNotEmpty()) " - $publisher" else "\u0000")
            if (title.isBlank()) return@mapNotNull null
            NewsArticle(title,publisher,url,runCatching { date.parse(field("pubDate"))?.time ?: 0 }.getOrDefault(0))
        }.distinctBy { it.url }.take(50)
    }
}
