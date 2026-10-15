package dev.vitreo.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.vitreo.launcher.data.WidgetController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.*

@Composable
fun HomeWidgets(now: Long, modifier: Modifier = Modifier, onClock: () -> Unit, onCalendar: () -> Unit) {
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val secondsVisible = !LocalGlass.current.appearance.reducedEffects
    val timeLabel = DateFormat.getTimeFormat(LocalContext.current).format(Date(now))
    Row(modifier.fillMaxWidth().padding(top = 5.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassPanel(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "Clock, $timeLabel" }
                .clickable(onClick = onClock), radius = 28f, tint = .1f) {
                Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                    val r = min(size.width, size.height) * .48f
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(Color(0xfff7f7f5), r, center)
                    repeat(60) { index ->
                        val angle = index * PI / 30
                        val inner = r * if (index % 5 == 0) .81f else .9f
                        drawLine(Color(0xff333336), center + Offset((sin(angle) * inner).toFloat(), (-cos(angle) * inner).toFloat()),
                            center + Offset((sin(angle) * r * .94f).toFloat(), (-cos(angle) * r * .94f).toFloat()),
                            strokeWidth = if (index % 5 == 0) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
                    }
                    fun hand(angle: Double, length: Float, width: Float, color: Color) {
                        drawLine(color, center, center + Offset((sin(angle) * length).toFloat(), (-cos(angle) * length).toFloat()),
                            width, cap = StrokeCap.Round)
                    }
                    val minute = calendar.get(Calendar.MINUTE)
                    hand((calendar.get(Calendar.HOUR) + minute / 60.0) * PI / 6, r * .52f, 4.dp.toPx(), Color(0xff232326))
                    hand(minute * PI / 30, r * .73f, 3.dp.toPx(), Color(0xff232326))
                    if (secondsVisible) hand(calendar.get(Calendar.SECOND) * PI / 30, r * .78f, 1.dp.toPx(), Color(0xffff9f0a))
                    drawCircle(Color(0xffff9f0a), 3.dp.toPx(), center)
                }
            }
            Text("Clock", color = LocalGlassContentColor.current, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xfff9f9f6))
                .clickable(onClick = onCalendar).padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date(now)).uppercase(Locale.ENGLISH),
                    fontSize = 12.sp, color = Color(0xffff453a), fontWeight = FontWeight.Medium, maxLines = 1)
                Text(calendar.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 54.sp,
                    fontWeight = FontWeight.Light, color = Color(0xff171719))
                Text(SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(now)), fontSize = 12.sp,
                    color = Color(0xff757579), maxLines = 1)
            }
            Text("Calendar", color = LocalGlassContentColor.current, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun NativeWidget(controller: WidgetController, id: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val provider = controller.manager.getAppWidgetInfo(id) ?: return
    val height = maxOf(140f, provider.minHeight / density.density).coerceAtMost(360f)
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(26.dp))) {
            val width = maxWidth.value.toInt()
            AndroidView(factory = {
                controller.host.createView(context, id, provider).apply { setPadding(0, 0, 0, 0) }
            }, modifier = Modifier.fillMaxSize(), update = { view ->
                view.updateAppWidgetSize(Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }, width, height.toInt(), width, height.toInt())
            })
        }
        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(provider.loadLabel(context.packageManager), Modifier.weight(1f), fontSize = 12.sp, color = LocalGlassContentColor.current.copy(alpha = .85f))
            IconButton(onClick = { controller.remove(id) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.RemoveCircleOutline, "Remove Widget", Modifier.size(19.dp), tint = LocalGlassContentColor.current.copy(alpha = .85f))
            }
        }
    }
}

@Composable
fun WidgetGallery(controller: WidgetController, showHomeWidgets: Boolean, onToggleHome: (Boolean) -> Unit,
    onConfigure: (AppWidgetHost, Int, (Boolean) -> Unit) -> Unit, onClose: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val providers = remember { controller.providers() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(providers, query) { providers.filter {
        dev.vitreo.launcher.core.searchKey(query) in dev.vitreo.launcher.core.searchKey(it.loadLabel(context.packageManager))
    } }
    fun finish(id: Int, accepted: Boolean) {
        if (controller.finishAdd(id, accepted)) onClose()
    }
    fun configure(id: Int) {
        if (id != controller.pendingId || id < 0) return
        val info = controller.manager.getAppWidgetInfo(id)
        if (info == null) { finish(id, false); return }
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        if (info.configure != null && !optional) onConfigure(controller.host, id) { finish(id, it) }
        else finish(id, true)
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = controller.pendingId
        if (result.resultCode == Activity.RESULT_OK) configure(id) else finish(id, false)
    }
    DisposableEffect(controller) { onDispose { controller.cancelAdd() } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        SheetHeader("Widgets", onClose, "widgets_title")
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Add what matters", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 15.dp))
                CompositionLocalProvider(LocalGlassContentColor provides MaterialTheme.colorScheme.onSurface) {
                    HomeWidgets(LocalHomeClock.current, Modifier.height(190.dp), {}, {})
                }
                IosGroup { IosToggleRow("Clock & Calendar on Home", showHomeWidgets, onChange = onToggleHome) }
                IosSectionLabel("Widgets from your apps")
                Text("Add widgets to Today, to the left of Home.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 17.dp, bottom = 8.dp))
                IosGroup {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(query, { query = it.take(100) }, Modifier.weight(1f).padding(start = 10.dp).testTag("widget_search"),
                            singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            decorationBox = { inner -> if (query.isEmpty()) Text("Search Widgets", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() })
                    }
                }
                if (controller.ids.size >= 8) Text("You have 8 widgets. Remove one from Today to add another.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            items(filtered, key = { it.provider.flattenToString() }) { info ->
                IosGroup {
                    WidgetPreview(info)
                    IosRow(info.loadLabel(context.packageManager), "Add", enabled = controller.ids.size < 8 && controller.pendingId < 0) {
                        try {
                            val id = controller.beginAdd() ?: return@IosRow
                            if (controller.manager.bindAppWidgetIdIfAllowed(id, info.provider)) configure(id)
                            else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile))
                        } catch (_: Exception) { controller.cancelAdd(); onError("Could not add this widget.") }
                    }
                    Text("${info.targetCellWidth.coerceAtLeast(1)} × ${info.targetCellHeight.coerceAtLeast(1)} · Today", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 17.dp, bottom = 14.dp))
                }
            }
            if (filtered.isEmpty() && providers.isNotEmpty()) item { Text("No matching widgets", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(15.dp)) }
            if (providers.isEmpty()) item { Text("Widgets from your installed apps will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(15.dp)) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
