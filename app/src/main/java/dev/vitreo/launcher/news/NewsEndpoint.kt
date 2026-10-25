package dev.vitreo.launcher.news

import dev.vitreo.launcher.core.NewsSettings
import java.util.Locale

/** Public publisher feeds. Edition chooses the language; no account, SDK or device location. */
data class NewsEndpoint(val url: String, val publisher: String, val language: String)

fun NewsSettings.directEndpoint(locale: Locale = Locale.getDefault()): NewsEndpoint {
    val s = sanitized()
    return when (s.edition(locale)) {
        "ES", "MX" -> {
            val section = mapOf("world" to "internacional", "technology" to "tecnologia", "business" to "economia",
                "science" to "ciencia", "health" to "salud-y-bienestar", "sports" to "deportes", "entertainment" to "cultura")[s.topic]
            NewsEndpoint("https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/" +
                (section?.let { "section/$it/" } ?: "") + "portada", "EL PAÍS", "Spanish")
        }
        "FR" -> {
            val section = mapOf("world" to "international", "technology" to "pixels", "business" to "economie",
                "science" to "sciences", "health" to "sante", "sports" to "sport", "entertainment" to "culture")[s.topic]
            NewsEndpoint("https://www.lemonde.fr/" + (section?.let { "$it/rss_full.xml" } ?: "rss/une.xml"), "Le Monde", "French")
        }
        "DE" -> {
            val section = when (s.topic) {
                "world" -> "ausland/"; "business" -> "wirtschaft/"
                "technology", "science", "health" -> "wissen/"
                else -> ""
            }
            NewsEndpoint("https://www.tagesschau.de/${section}index~rss2.xml", "tagesschau", "German")
        }
        else -> {
            val path = when (s.topic) {
                "world" -> "news/world/"; "technology" -> "news/technology/"; "business" -> "news/business/"
                "science" -> "news/science_and_environment/"; "health" -> "news/health/"
                "sports" -> "sport/"; "entertainment" -> "news/entertainment_and_arts/"; else -> "news/"
            }
            NewsEndpoint("https://feeds.bbci.co.uk/${path}rss.xml", "BBC News", "English")
        }
    }
}

fun NewsSettings.availableTopics(locale: Locale = Locale.getDefault()): Map<String, String> =
    if (sanitized().provider == "direct" && edition(locale) == "DE") NewsSettings.TOPICS.filterKeys { it !in setOf("sports", "entertainment") }
    else NewsSettings.TOPICS
