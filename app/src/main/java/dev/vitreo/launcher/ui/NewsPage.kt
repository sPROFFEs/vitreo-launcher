package dev.vitreo.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.NewsSettings
import dev.vitreo.launcher.news.*
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsPage(settings: NewsSettings, active: Boolean, onSettings: () -> Unit, onTopic: (String) -> Unit,
    source: NewsSource? = null, imageSource: NewsImageSource? = null) {
    val context = LocalContext.current
    val repository = remember(context, source) { source ?: NewsRepository(context) }
    val images = remember(context, imageSource) { imageSource ?: NewsImages(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val view = androidx.compose.ui.platform.LocalView.current
    var focused by remember { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        val focus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused = it }
        lifecycle.addObserver(observer)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        onDispose {
            lifecycle.removeObserver(observer)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
        }
    }
    val visible = active && resumed && focused
    var feed by remember(settings.market, settings.topic, settings.provider, settings.fallback) { mutableStateOf(NewsFeed()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var handled by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.market, settings.topic, settings.provider, settings.fallback, visible, refresh, repository) {
        if (!visible) return@LaunchedEffect
        loading = true
        try {
            if (feed.articles.isEmpty()) feed = repository.cached(settings)
            val request = refresh
            feed = repository.load(settings, force = request != handled)
            handled = request
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { feed = feed.copy(error = newsError(e)) }
        finally { loading = false }
    }
    val ink = LocalGlassContentColor.current
    val reduced = LocalGlass.current.appearance.reducedEffects
    val expectedSource = if (settings.provider == "google") "Google News" else settings.directEndpoint().publisher
    val actualSource = if (feed.articles.isEmpty()) expectedSource else feed.sourceName
    val topics = settings.availableTopics()
    PullToRefreshBox(isRefreshing = loading, onRefresh = { refresh++ }, modifier = Modifier.fillMaxSize().testTag("news_page")) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 21.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 30.dp)) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(Date()).uppercase(Locale.ENGLISH),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = .65f))
                        Text("Today", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = ink)
                        Text(actualSource, fontSize = 12.sp, color = ink.copy(alpha = .7f))
                    }
                    IconButton(onClick = { refresh++ }, enabled = !loading) { LauncherSymbol(Icons.Outlined.Refresh, "Refresh news", tint = ink) }
                    IconButton(onClick = onSettings) { LauncherSymbol(Icons.Outlined.Settings, "Settings", tint = ink) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.forEach { (key, title) ->
                        FilterChip(selected = settings.topic == key, onClick = { onTopic(key) }, label = { Text(title) },
                            shape = if (LocalCyber.current) CyberShape else RoundedCornerShape(24.dp),
                            colors = FilterChipDefaults.filterChipColors(labelColor = ink, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedContainerColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }
            if (feed.fallback) item(key = "fallback") {
                Text("Google News is unavailable. Showing ${feed.sourceName} RSS.", color = ink.copy(alpha = .8f),
                    fontSize = 12.sp, modifier = Modifier.testTag("news_fallback"))
            }
            if (loading && feed.articles.isEmpty()) item(key = "loading") {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = ink, strokeWidth = 2.dp)
                    Text("Loading headlines…", Modifier.padding(start = 12.dp), color = ink, fontSize = 14.sp)
                }
            }
            if (feed.error != null || !loading && feed.articles.isEmpty()) item(key = "error") {
                GlassPanel(Modifier.fillMaxWidth(), radius = 24f, tint = .2f) {
                    Column(Modifier.padding(18.dp)) {
                        Text(feed.error ?: "No headlines yet.", color = ink)
                        if (feed.articles.isNotEmpty()) Text("Showing saved headlines.", color = ink.copy(alpha = .7f), fontSize = 13.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { refresh++ }, modifier = Modifier.testTag("news_retry")) { Text("Try again", color = ink) }
                            TextButton(onClick = onSettings) { Text("News settings", color = ink) }
                        }
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"))) } }) { Text("Android app settings", color = ink) }
                    }
                }
            }
            itemsIndexed(feed.articles, key = { _, article -> article.url }) { index, article ->
                val hero = index == 0
                val photo = article.imageUrl?.takeIf { settings.images && NewsParser.safeImage(it) }
                GlassPanel(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(if (reduced) 0 else 160), fadeOutSpec = tween(if (reduced) 0 else 120))
                    .clickable(onClickLabel = "Read ${article.title}") {
                        if (NewsParser.safeLink(article.url)) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) }
                    }, radius = 26f, tint = .2f, interactive = true) {
                    if (hero) Column {
                        if (photo != null) NewsArtwork(photo, visible, images, Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("news_hero_image"))
                        NewsArticleText(article, true, Modifier.padding(20.dp))
                    } else Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        NewsArticleText(article, false, Modifier.weight(1f))
                        if (photo != null) NewsArtwork(photo, visible, images, Modifier.size(88.dp)
                            .clip(if (LocalCyber.current) CyberShape else RoundedCornerShape(16.dp)).testTag("news_thumbnail"))
                    }
                }
            }
            item(key = "footer") {
                Text("$actualSource · Public RSS\nNo Google account or Play Services required. Articles open in your browser.",
                    color = ink.copy(alpha = .65f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun NewsArticleText(article: NewsArticle, hero: Boolean, modifier: Modifier) {
    val ink = LocalGlassContentColor.current
    Column(modifier) {
        Text(article.publisher.ifBlank { "News" }, color = ink.copy(alpha = .7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(article.title, color = ink, fontSize = if (hero) 23.sp else 16.sp, lineHeight = if (hero) 28.sp else 22.sp,
            maxLines = if (hero) 6 else 5, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp))
        if (article.time > 0) Text(android.text.format.DateUtils.getRelativeTimeSpanString(article.time, System.currentTimeMillis(), 60_000L).toString(),
            fontSize = 11.sp, color = ink.copy(alpha = .65f))
    }
}

@Composable
private fun NewsArtwork(url: String, active: Boolean, source: NewsImageSource, modifier: Modifier) {
    var bitmap by remember(url, source) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url, active, source) { if (active && bitmap == null) bitmap = source.load(url) }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f,
        tween(if (LocalGlass.current.appearance.reducedEffects) 0 else 180), label = "news photo")
    Box(modifier.background(LocalGlassContentColor.current.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
        if (bitmap == null) LauncherSymbol(Icons.Outlined.Newspaper, null, Modifier.size(28.dp), tint = LocalGlassContentColor.current.copy(alpha = .25f))
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = ContentScale.Crop) }
    }
}

@Composable
fun NewsSettingsPage(a: Appearance, onAppearance: (Appearance) -> Unit) {
    val settings = a.news
    var choosing by remember { mutableStateOf<String?>(null) }
    IosGroup {
        IosSegmented(listOf("Widgets", "News"), if (settings.enabled) 1 else 0, Modifier.padding(12.dp)) {
            onAppearance(a.copy(news = settings.copy(enabled = it == 1)))
        }
    }
    Text("Choose what appears to the left of Home. Switching back to Widgets keeps your existing widgets, order and hidden cards.",
        Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    IosGroup {
        IosRow("News source", NewsSettings.PROVIDERS[settings.provider].orEmpty()) { choosing = "provider" }; IosDivider()
        IosRow("News edition", NewsSettings.MARKETS[settings.market].orEmpty()) { choosing = "market" }; IosDivider()
        IosRow("Default topic", settings.availableTopics()[settings.topic] ?: "Top stories") { choosing = "topic" }
    }
    val endpoint = settings.directEndpoint()
    Text(if (settings.provider == "direct") "Direct public RSS from ${endpoint.publisher} in ${endpoint.language}. No Google connection, account or Play Services. The edition selects a publisher language, not a personalized local feed."
        else "Public Google News RSS. No Google account or Play Services. Google's RSS does not provide article photos; choose Publisher RSS for illustrated stories.",
        Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    IosGroup {
        IosToggleRow("Show article images", settings.images) { onAppearance(a.copy(news = settings.copy(images = it))) }
        if (settings.provider == "google") {
            IosDivider()
            IosToggleRow("Use publisher RSS if Google fails", settings.fallback) { onAppearance(a.copy(news = settings.copy(fallback = it))) }
        }
    }
    Text("News is optional and off by default. Headlines load only while this page is visible. Photos load as cards appear and can be turned off. The selected publishers and image servers receive your IP address; no layout, app list, location or notifications are sent. Headlines and images have a bounded local cache. On GrapheneOS, allow Network for Vitreo in Android app settings.",
        Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    choosing?.let { field ->
        val choices = when (field) { "provider" -> NewsSettings.PROVIDERS; "market" -> NewsSettings.MARKETS; else -> settings.availableTopics() }
        AlertDialog(onDismissRequest = { choosing = null }, title = { Text(when (field) { "provider" -> "News source"; "market" -> "News edition"; else -> "Default topic" }) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { (key, title) -> TextButton(onClick = {
                    var next = when (field) { "provider" -> settings.copy(provider = key); "market" -> settings.copy(market = key); else -> settings.copy(topic = key) }
                    if (next.topic !in next.availableTopics()) next = next.copy(topic = "top")
                    onAppearance(a.copy(news = next)); choosing = null
                }) { Text(title) } }
            } }, confirmButton = { TextButton(onClick = { choosing = null }) { Text("Done") } })
    }
}
