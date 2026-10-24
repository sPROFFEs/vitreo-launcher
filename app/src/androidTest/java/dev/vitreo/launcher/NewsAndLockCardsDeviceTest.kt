package dev.vitreo.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.LockNotice
import dev.vitreo.launcher.news.*
import dev.vitreo.launcher.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NewsAndLockCardsDeviceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun newsOnlyLoadsWhenThePageIsActive() {
        val calls=AtomicInteger(); var active by mutableStateOf(false)
        val source=object:NewsSource { override suspend fun load(settings:NewsSettings,force:Boolean):NewsFeed { calls.incrementAndGet(); return NewsFeed(listOf(NewsArticle("A story","Publisher","https://example.com/1",0))) } }
        compose.setContent { VitreoTheme { NewsPage(NewsSettings(true),active,{}, {},source) } }
        compose.waitForIdle(); assertEquals(0,calls.get())
        compose.runOnIdle { active=true }; compose.waitUntil(10000) { calls.get()==1 }
        compose.onNodeWithText("A story").assertIsDisplayed()
    }
    @Test fun cachedHeadlinesRemainVisibleWithRetryOnNetworkFailure() {
        val calls=AtomicInteger()
        val source=object:NewsSource { override suspend fun load(settings:NewsSettings,force:Boolean):NewsFeed { calls.incrementAndGet(); return NewsFeed(listOf(NewsArticle("Saved story","Source","https://example.com/2",0)),1,"Offline") } }
        compose.setContent { VitreoTheme { NewsPage(NewsSettings(true),true,{}, {},source) } }
        compose.waitUntil(10000) { calls.get()==1 }; compose.onNodeWithText("Saved story").assertIsDisplayed()
        compose.onNodeWithTag("news_retry").performClick(); compose.waitUntil(10000) { calls.get()==2 }
    }
    @Test fun switchingTodayModePreservesWidgetLayout() {
        var a by mutableStateOf(Appearance(todaySections=DEFAULT_TODAY_SECTIONS.reversed(),hiddenTodaySections=setOf("suggestions")))
        val original=a
        compose.setContent { VitreoTheme { Column { NewsSettingsPage(a) { a=it } } } }
        compose.onNodeWithText("News",useUnmergedTree=true).performClick()
        compose.runOnIdle { assertTrue(a.news.enabled); assertEquals(original.todaySections,a.todaySections); assertEquals(original.hiddenTodaySections,a.hiddenTodaySections) }
        compose.onNodeWithText("Widgets",useUnmergedTree=true).performClick()
        compose.runOnIdle { assertFalse(a.news.enabled); assertEquals(original.todaySections,a.todaySections) }
    }
    @Test fun lockCardsAreBoundedAndOnlyExposeSafeSummariesInBothThemes() {
        var selected=""; var theme by mutableIntStateOf(0)
        compose.setContent { VitreoTheme(preset=theme) { Box(Modifier.fillMaxSize()) {
            LockNotificationCards(listOf(LockNotice("one","Example app",2,0)),Modifier.height(160.dp)) { selected=it }
        } } }
        compose.onNodeWithText("Example app").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("one",selected); theme=1 }
        compose.onNodeWithText("2 notifications · Unlock to view").assertIsDisplayed()
    }
}
