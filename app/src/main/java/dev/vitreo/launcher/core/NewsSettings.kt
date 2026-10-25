package dev.vitreo.launcher.core

import java.util.Locale

data class NewsSettings(
    val enabled: Boolean = false,
    val market: String = "auto",
    val topic: String = "top",
    val provider: String = "direct",
    val fallback: Boolean = true,
    val images: Boolean = true,
) {
    fun sanitized() = copy(
        market = market.takeIf { it in MARKETS } ?: "auto",
        topic = topic.takeIf { it in TOPICS } ?: "top",
        provider = provider.takeIf { it in PROVIDERS } ?: "direct",
    )
    fun edition(locale: Locale = Locale.getDefault()): String = if (market != "auto") market else when (locale.language) {
        "es" -> if (locale.country == "MX") "MX" else "ES"
        "fr" -> "FR"; "de" -> "DE"; else -> if (locale.country == "GB") "GB" else "US"
    }
    fun url(locale: Locale = Locale.getDefault()): String {
        val country = sanitized().edition(locale)
        val language = when(country) { "ES","MX" -> "es"; "FR" -> "fr"; "DE" -> "de"; else -> "en" }
        val path = if (sanitized().topic == "top") "/rss" else "/rss/headlines/section/topic/${sanitized().topic.uppercase(Locale.ROOT)}"
        return "https://news.google.com$path?hl=$language-$country&gl=$country&ceid=$country:$language"
    }
    companion object {
        val PROVIDERS = linkedMapOf("direct" to "Publisher RSS", "google" to "Google News")
        val MARKETS = linkedMapOf("auto" to "Device language", "US" to "United States", "GB" to "United Kingdom", "ES" to "Spain", "MX" to "Mexico", "FR" to "France", "DE" to "Germany")
        val TOPICS = linkedMapOf("top" to "Top stories", "world" to "World", "technology" to "Technology", "business" to "Business", "science" to "Science", "health" to "Health", "sports" to "Sports", "entertainment" to "Entertainment")
    }
}
