package dev.vitreo.launcher.news

import android.app.Notification
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
class NewsAndLockTest {
    private fun rss(items: String) = "<rss><channel>$items</channel></rss>"
    private fun item(title: String="Headline - Publisher",link: String="https://news.google.com/read/1") =
        "<item><title>$title</title><link>$link</link><source>Publisher</source><pubDate>Sat, 24 Oct 2026 12:00:00 GMT</pubDate></item>"
    @Test fun parsesHeadlinesDeduplicatesAndStripsPublisherSuffix() {
        val list=NewsParser.parse(rss(item()+item())); assertEquals(1,list.size); assertEquals("Headline",list.single().title)
        assertEquals("Publisher",list.single().publisher); assertTrue(list.single().time>0)
    }
    @Test fun refusesExternalEntitiesAndUnsafeArticleLinks() {
        assertTrue(runCatching { NewsParser.parse("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+rss(item())) }.isFailure)
        for(link in listOf("http://example.com","https://user@example.com/","javascript:alert(1)","https://example.com:8080")) assertTrue(NewsParser.parse(rss(item(link=link))).isEmpty())
    }
    @Test fun boundsResultsAndCleansEmbeddedMarkup() {
        val list=NewsParser.parse(rss((1..80).joinToString("") { item("&lt;b&gt;Story $it&lt;/b&gt;","https://example.com/$it") }))
        assertEquals(50,list.size); assertEquals("Story 1",list.first().title)
    }
    @Test fun localeTopicAndBackupArePortableWithoutChangingWidgetOrder() {
        assertTrue(NewsSettings().url(Locale("es","ES")).contains("ceid=ES:es"))
        assertEquals("top",NewsSettings(topic="malicious/path").sanitized().topic)
        val original=LauncherState(appearance=Appearance(news=NewsSettings(true,"GB","science"),todaySections=DEFAULT_TODAY_SECTIONS.reversed()))
        assertEquals(original.sanitized(),StateCodec.decode(StateCodec.encode(original)))
        val old=JSONObject(StateCodec.encode(original)); old.getJSONObject("appearance").remove("news")
        assertFalse(StateCodec.decode(old.toString()).appearance.news.enabled)
    }
    @Test fun lockCardsNeverCarryPrivateFieldsAndExcludeAppAndChannelSecrets() {
        fun notice(key:String,visibility:Int=Notification.VISIBILITY_PRIVATE,channel:Int=android.service.notification.NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE) = Notice(key,"dev.example","Example","Secret title","Secret body",1,false,null,visibility=visibility,channelVisibility=channel)
        val cards=lockNotices(listOf(notice("a"),notice("b"),notice("secret",Notification.VISIBILITY_SECRET),notice("channel",channel=Notification.VISIBILITY_SECRET)))
        assertEquals(1,cards.size); assertEquals(2,cards.single().count)
        assertFalse(cards.toString().contains("Secret"))
        assertEquals(setOf("key","app","count","time"),LockNotice::class.java.declaredFields.filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
    }
    @Test fun lockIdleAndNotificationsHaveSafeLegacyDefaultsAndBounds() {
        assertEquals(10,LockScreenSettings(idleTimeoutSeconds=-1).sanitized().idleTimeoutSeconds)
        assertTrue(LockScreenCodec.decode(null).showNotifications)
        val options=LockScreenSettings(showNotifications=false,idleTimeoutSeconds=5)
        assertEquals(options,LockScreenCodec.decode(LockScreenCodec.encode(options)))
    }
}
