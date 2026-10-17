package dev.vitreo.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Render provider-supplied content without allocating or binding an AppWidget ID. */
internal fun renderWidgetPreview(context: Context, info: AppWidgetProviderInfo, width: Int, height: Int,
    dark: Boolean): Bitmap? {
    val configuration = Configuration(context.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val themed = ContextThemeWrapper(context.createConfigurationContext(configuration),
        if (dark) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light)
    fun render(remote: RemoteViews): Bitmap? = runCatching {
        val parent = FrameLayout(themed)
        val view = remote.apply(themed, parent)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }.getOrNull()
    if (Build.VERSION.SDK_INT >= 35) {
        val generated = runCatching { AppWidgetManager.getInstance(context).getWidgetPreview(info.provider,
            info.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }.getOrNull()
        if (generated != null) render(generated)?.let { return it }
    }
    // Some preview layouts depend on a window/clock tick and draw only their
    // hands when detached. A supplied image is complete and needs no live host.
    if (info.previewImage != 0) runCatching { info.loadPreviewImage(themed, context.resources.displayMetrics.densityDpi)
        ?.let { drawable ->
            val scale = minOf(width.toFloat() / drawable.intrinsicWidth.coerceAtLeast(1), height.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1))
            drawable.toBitmap((drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1),
                (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1))
        } }.getOrNull()?.let { return it }
    if (info.previewLayout != 0) render(RemoteViews(info.provider.packageName, info.previewLayout))?.let { return it }
    return null
}

@Composable
fun WidgetPreview(info: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp)
        .testTag("widget_preview_well:${info.provider.flattenToString()}").clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.background)) {
        // Render to the provider's aspect ratio, then fit the passive bitmap inside
        // the card. Tall / wide provider layouts keep all their content visible.
        val ratio = if (info.minWidth > 0 && info.minHeight > 0) info.minHeight.toFloat() / info.minWidth else .5f
        val height = (maxWidth.value * ratio).coerceIn(120f, 240f).dp
        val widthPx = with(density) { maxWidth.roundToPx() }
        val renderWidth = maxOf(widthPx, info.minWidth).coerceIn(1, 2048)
        val renderHeight = maxOf((renderWidth * ratio).toInt(), info.minHeight).coerceIn(1, 2048)
        val preview by produceState<Bitmap?>(null, info.provider, renderWidth, renderHeight, dark) {
            // Inflate/draw Android Views on Main. The result is a passive bitmap: preview taps cannot launch an app.
            value = withContext(Dispatchers.Main.immediate) { renderWidgetPreview(context, info, renderWidth, renderHeight, dark) }
        }
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(22.dp)).padding(18.dp), contentAlignment = Alignment.Center) {
            if (preview != null) Image(checkNotNull(preview).asImageBitmap(), "${info.loadLabel(context.packageManager)} widget preview",
                Modifier.fillMaxSize().testTag("widget_preview:${info.provider.flattenToString()}"), contentScale = ContentScale.Fit)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LauncherSymbol(Icons.Outlined.Widgets, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No preview provided by this app", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}
