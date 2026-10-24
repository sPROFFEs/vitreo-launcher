package dev.vitreo.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vitreo.launcher.core.NewsSettings
import dev.vitreo.launcher.news.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsPage(settings: NewsSettings, active: Boolean, onSettings: () -> Unit, onTopic: (String) -> Unit,
    source: NewsSource? = null) {
    val context=LocalContext.current
    val repository=remember(context,source) { source ?: NewsRepository(context) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val view=androidx.compose.ui.platform.LocalView.current
    var focused by remember { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(lifecycle,view) {
        val observer=LifecycleEventObserver { _,_ -> resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        val focus=android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused=it }
        lifecycle.addObserver(observer); view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        onDispose { lifecycle.removeObserver(observer); if(view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus) }
    }
    var feed by remember(settings.market,settings.topic) { mutableStateOf(NewsFeed()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var handled by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.market,settings.topic,active,resumed,focused,refresh,repository) {
        if(!active || !resumed || !focused) return@LaunchedEffect
        loading=true
        try {
            if(feed.articles.isEmpty()) feed=repository.cached(settings)
            val request=refresh
            feed=repository.load(settings,force=request!=handled)
            handled=request
        } finally { loading=false }
    }
    val ink=LocalGlassContentColor.current
    val reduced=LocalGlass.current.appearance.reducedEffects
    PullToRefreshBox(isRefreshing=loading,onRefresh={refresh++},modifier=Modifier.fillMaxSize().testTag("news_page")) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal=21.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item(key="header") {
                Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today",fontSize=32.sp,fontWeight=FontWeight.Bold,color=ink)
                        Text("Google News · ${NewsSettings.MARKETS[settings.edition()]}",fontSize=12.sp,color=ink.copy(alpha=.7f))
                    }
                    IconButton(onClick={refresh++}) { LauncherSymbol(Icons.Outlined.Refresh,"Refresh news",tint=ink) }
                    IconButton(onClick=onSettings) { LauncherSymbol(Icons.Outlined.Settings,"Settings",tint=ink) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    NewsSettings.TOPICS.forEach { (key,title) ->
                        FilterChip(selected=settings.topic==key,onClick={onTopic(key)},label={Text(title)},
                            shape=if(LocalCyber.current) CyberShape else androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            colors=FilterChipDefaults.filterChipColors(labelColor=ink,selectedLabelColor=MaterialTheme.colorScheme.onPrimary,selectedContainerColor=MaterialTheme.colorScheme.primary))
                    }
                }
            }
            if(feed.error!=null || !loading && feed.articles.isEmpty()) item(key="error") {
                GlassPanel(Modifier.fillMaxWidth(),radius=24f,tint=.2f) {
                    Column(Modifier.padding(18.dp)) {
                        Text(feed.error ?: "No headlines yet.",color=ink)
                        if(feed.articles.isNotEmpty()) Text("Showing saved headlines.",color=ink.copy(alpha=.7f),fontSize=13.sp)
                        TextButton(onClick={refresh++},modifier=Modifier.testTag("news_retry")) { Text("Try again",color=ink) }
                    }
                }
            }
            items(feed.articles,key={it.url}) { article ->
                GlassPanel(Modifier.fillMaxWidth().animateItem(fadeInSpec=tween(if(reduced) 0 else 160),fadeOutSpec=tween(if(reduced) 0 else 120))
                    .clickable(onClickLabel="Read ${article.title}") {
                        if(NewsParser.safeLink(article.url)) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(article.url))) }
                    },radius=26f,tint=.18f,interactive=true) {
                    Column(Modifier.padding(20.dp)) {
                        Text(article.publisher.ifBlank{"News"},color=ink.copy(alpha=.7f),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                        Text(article.title,color=ink,fontSize=if(article==feed.articles.firstOrNull()) 23.sp else 18.sp,
                            lineHeight=if(article==feed.articles.firstOrNull()) 28.sp else 24.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=9.dp))
                        if(article.time>0) Text(android.text.format.DateUtils.getRelativeTimeSpanString(article.time,System.currentTimeMillis(),60_000L).toString(),fontSize=12.sp,color=ink.copy(alpha=.65f))
                    }
                }
            }
            item(key="footer") { Text("Headlines from Google News. Articles open in your browser.",color=ink.copy(alpha=.65f),fontSize=12.sp,modifier=Modifier.padding(vertical=18.dp)) }
        }
    }
}

@Composable
fun NewsSettingsPage(a: dev.vitreo.launcher.core.Appearance, onAppearance: (dev.vitreo.launcher.core.Appearance) -> Unit) {
    val settings=a.news
    var choosing by remember { mutableStateOf<String?>(null) }
    IosGroup {
        IosSegmented(listOf("Widgets","News"),if(settings.enabled) 1 else 0,Modifier.padding(12.dp)) {
            onAppearance(a.copy(news=settings.copy(enabled=it==1)))
        }
    }
    Text("Choose what appears to the left of Home. Switching back to Widgets keeps your existing widgets, order and hidden cards.",Modifier.padding(16.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    IosGroup {
        IosRow("News edition",NewsSettings.MARKETS[settings.market].orEmpty()) { choosing="market" }; IosDivider()
        IosRow("Default topic",NewsSettings.TOPICS[settings.topic].orEmpty()) { choosing="topic" }
    }
    Text("News is optional and off by default. When visible, it requests public Google News RSS headlines for your edition and topic, then keeps a short local cache. Google receives the request and your IP address. No launcher layout, installed apps, location or notification data is sent. Articles open in your browser.",Modifier.padding(16.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    choosing?.let { field -> AlertDialog(onDismissRequest={choosing=null},title={Text(if(field=="market") "News edition" else "Default topic")},
        text={ Column(Modifier.verticalScroll(rememberScrollState())) {
            (if(field=="market") NewsSettings.MARKETS else NewsSettings.TOPICS).forEach { (key,title) ->
                TextButton(onClick={ onAppearance(a.copy(news=if(field=="market") settings.copy(market=key) else settings.copy(topic=key))); choosing=null }) { Text(title) }
            }
        } },confirmButton={TextButton(onClick={choosing=null}) {Text("Done")} }) }
}
