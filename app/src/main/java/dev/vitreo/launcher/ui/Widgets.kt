package dev.vitreo.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
fun ClockWidgetCard(
    now: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    editing: Boolean = false,
    onEdit: (() -> Unit)? = null,
) {
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val secondsVisible = !LocalGlass.current.appearance.reducedEffects
    val timeLabel = DateFormat.getTimeFormat(LocalContext.current).format(Date(now))
    val classic = LocalGlass.current.appearance.iosClassic

    Box(modifier.testTag("clock_widget_card")) {
        GlassPanel(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Clock, $timeLabel" }
                .clickable(onClick = if (editing && onEdit != null) onEdit else onClick),
            radius = 28f,
            tint = if (classic) .18f else .12f
        ) {
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val r = min(size.width, size.height) * .44f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xfff7f7f5), r, center)
                repeat(12) { i ->
                    val angle = i * PI / 6
                    val isCardinal = i % 3 == 0
                    val inner = r * if (isCardinal) .78f else .84f
                    val outer = r * .93f
                    drawLine(
                        color = if (isCardinal) Color(0xff1c1c1e) else Color(0xff8e8e93),
                        start = center + Offset((sin(angle) * inner).toFloat(), (-cos(angle) * inner).toFloat()),
                        end = center + Offset((sin(angle) * outer).toFloat(), (-cos(angle) * outer).toFloat()),
                        strokeWidth = if (isCardinal) 2.5.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                fun hand(angle: Double, length: Float, width: Float, color: Color, tail: Float = 0f) {
                    val start = if (tail > 0f) center - Offset((sin(angle) * tail).toFloat(), (-cos(angle) * tail).toFloat()) else center
                    val end = center + Offset((sin(angle) * length).toFloat(), (-cos(angle) * length).toFloat())
                    drawLine(color, start, end, width, cap = StrokeCap.Round)
                }
                val minute = calendar.get(Calendar.MINUTE)
                val hour = calendar.get(Calendar.HOUR)
                hand((hour + minute / 60.0) * PI / 6, r * .52f, 3.6.dp.toPx(), Color(0xff1c1c1e))
                hand(minute * PI / 30, r * .75f, 2.6.dp.toPx(), Color(0xff1c1c1e))
                if (secondsVisible) {
                    val sec = calendar.get(Calendar.SECOND)
                    hand(sec * PI / 30, r * .84f, 1.2.dp.toPx(), Color(0xffff9500), tail = r * .22f)
                }
                drawCircle(Color(0xff1c1c1e), 3.2.dp.toPx(), center)
                if (secondsVisible) drawCircle(Color(0xffff9500), 1.8.dp.toPx(), center)
            }
        }
        if (editing) {
            IconButton(
                onClick = { onEdit?.invoke() ?: onClick() },
                modifier = Modifier.align(Alignment.TopEnd).size(36.dp).padding(4.dp)
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                    LauncherSymbol(Icons.Outlined.MoreHoriz, "Edit Clock Widget", Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun CalendarWidgetCard(
    now: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    editing: Boolean = false,
    onEdit: (() -> Unit)? = null,
) {
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val locale = Locale.getDefault()
    val dayOfWeek = SimpleDateFormat("EEEE", locale).format(Date(now)).uppercase(locale)
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH).toString()
    val monthYear = SimpleDateFormat("MMMM yyyy", locale).format(Date(now)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    val classic = LocalGlass.current.appearance.iosClassic
    val shape = RoundedCornerShape(28.dp)

    Box(modifier.testTag("calendar_widget_card")) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .shadow(if (classic) 1.dp else 6.dp, shape, clip = false,
                    ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .12f))
                .clip(shape)
                .background(Color(0xfff9f9f6))
                .clickable(onClick = if (editing && onEdit != null) onEdit else onClick)
                .padding(16.dp)
        ) {
            val fontScale = LocalDensity.current.fontScale
            val captionSize = minOf(13f, maxHeight.value * .15f / fontScale)
            val daySize = minOf(52f, maxHeight.value * .50f / fontScale)

            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = dayOfWeek,
                    fontSize = captionSize.sp,
                    lineHeight = (captionSize * 1.2f).sp,
                    color = Color(0xffff3b30),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .4.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = dayOfMonth,
                    fontSize = daySize.sp,
                    lineHeight = (daySize * 1.05f).sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xff1c1c1e),
                    maxLines = 1
                )
                Text(
                    text = monthYear,
                    fontSize = captionSize.sp,
                    lineHeight = (captionSize * 1.2f).sp,
                    color = Color(0xff8e8e93),
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        if (editing) {
            IconButton(
                onClick = { onEdit?.invoke() ?: onClick() },
                modifier = Modifier.align(Alignment.TopEnd).size(36.dp).padding(4.dp)
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                    LauncherSymbol(Icons.Outlined.MoreHoriz, "Edit Calendar Widget", Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun HomeWidgets(now: Long, modifier: Modifier = Modifier, onClock: () -> Unit, onCalendar: () -> Unit,
    showClock: Boolean = true, showCalendar: Boolean = true,
    editing: Boolean = false, onEditClock: (() -> Unit)? = null, onEditCalendar: (() -> Unit)? = null) {
    if (LocalCyber.current) { CyberHomeWidgets(now, modifier, onClock, onCalendar, showClock, showCalendar); return }
    if (!showClock && !showCalendar) return

    val contentModifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)

    if (showClock && showCalendar) {
        Row(contentModifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClockWidgetCard(now, Modifier.weight(1f).fillMaxHeight(), onClock, editing, onEditClock)
            CalendarWidgetCard(now, Modifier.weight(1f).fillMaxHeight(), onCalendar, editing, onEditCalendar)
        }
    } else if (showClock) {
        Row(contentModifier) {
            ClockWidgetCard(now, Modifier.fillMaxWidth(.5f).fillMaxHeight().padding(end = 5.dp), onClock, editing, onEditClock)
        }
    } else {
        Row(contentModifier) {
            CalendarWidgetCard(now, Modifier.fillMaxWidth(.5f).fillMaxHeight().padding(end = 5.dp), onCalendar, editing, onEditCalendar)
        }
    }
}

@Composable
fun NativeWidget(controller: WidgetController, id: Int, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        NativeWidgetContent(controller, id)
        WidgetCaption(controller, id, onEdit)
    }
}

@Composable
internal fun WidgetCaption(controller: WidgetController, id: Int, onEdit: () -> Unit) {
    val context = LocalContext.current
    val label = controller.manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager).orEmpty()
    Row(Modifier.fillMaxWidth().height(30.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onEdit, modifier = Modifier.size(30.dp).testTag("edit_widget:$id")) {
            LauncherSymbol(Icons.Outlined.MoreHoriz, "Edit $label widget", Modifier.size(19.dp), tint = LocalGlassContentColor.current)
        }
    }
}

/** The same host is used on Home and Today. A fixed Home rectangle fits the whole
 * provider minimum before scaling, so oversized RemoteViews never escape the card. */
@Composable
internal fun NativeWidgetContent(controller: WidgetController, id: Int, modifier: Modifier = Modifier, fitToBounds: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val provider = controller.manager.getAppWidgetInfo(id) ?: return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.value.coerceAtLeast(1f)
        val providerPadding = remember(context, provider.provider, density.density) {
            android.appwidget.AppWidgetHostView.getDefaultPaddingForWidget(context, provider.provider, null)
        }
        val paddingWidth = (providerPadding.left + providerPadding.right) / density.density
        val paddingHeight = (providerPadding.top + providerPadding.bottom) / density.density
        val resizeWidth = fitToBounds && provider.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && provider.minResizeWidth > 0
        val resizeHeight = fitToBounds && provider.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0 && provider.minResizeHeight > 0
        val minWidth = ((if (resizeWidth) provider.minResizeWidth else provider.minWidth) / density.density + paddingWidth).coerceAtLeast(1f)
        val minHeight = ((if (resizeHeight) provider.minResizeHeight else provider.minHeight) / density.density + paddingHeight).coerceIn(1f, 1600f)
        val height = if (fitToBounds) maxHeight.value.coerceAtLeast(1f)
            else maxOf(140f, minHeight * minOf(1f, width / minWidth))
        val fit = minOf(1f, width / minWidth, height / minHeight).coerceAtLeast(.01f)
        val renderWidth = (width / fit).roundToInt().coerceAtLeast(1)
        val renderHeight = (height / fit).roundToInt().coerceAtLeast(1)
        AndroidView(factory = {
            WidgetHostContainer(context).apply {
                addView(controller.host.createView(context, id, provider),
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        }, modifier = Modifier.fillMaxWidth().height(height.dp).testTag("native_widget:$id"), update = { container ->
            val view = container.getChildAt(0) as android.appwidget.AppWidgetHostView
            val pixelsWide = (renderWidth * density.density).roundToInt()
            val pixelsHigh = (renderHeight * density.density).roundToInt()
            if (view.layoutParams.width != pixelsWide || view.layoutParams.height != pixelsHigh) {
                view.layoutParams = FrameLayout.LayoutParams(pixelsWide, pixelsHigh)
            }
            view.pivotX = 0f; view.pivotY = 0f
            view.scaleX = fit; view.scaleY = fit
            // RemoteViews updates/layout are expensive; the Home clock must not
            // send another provider resize broadcast every second.
            val dimensions = renderWidth to renderHeight
            if (view.tag != dimensions) {
                view.tag = dimensions
                view.updateAppWidgetSize(Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }, listOf(android.util.SizeF(renderWidth.toFloat(), renderHeight.toFloat())))
            }
        }, onRelease = { it.removeAllViews() })
    }
}

/** AndroidView children need a native clip as well as the Compose card's clip. */
internal class WidgetHostContainer(context: android.content.Context) : FrameLayout(context) {
    init {
        clipChildren = true
        clipToPadding = true
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 26f * resources.displayMetrics.density)
            }
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
    }
}

@Composable
fun WidgetGallery(controller: WidgetController, showHomeWidgets: Boolean, onToggleHome: (Boolean) -> Unit,
    onConfigure: (AppWidgetHost, Int, (Boolean) -> Unit) -> Unit, onClose: () -> Unit, onError: (String) -> Unit,
    initialHomePage: Int? = 0, appearance: dev.vitreo.launcher.core.Appearance? = null,
    onAppearance: ((dev.vitreo.launcher.core.Appearance) -> Unit)? = null, onEditBuiltin: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val providers = remember { controller.providers() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var destinationHome by remember { mutableStateOf(initialHomePage != null) }
    var size by remember { mutableIntStateOf(0) }
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
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp).testTag("widget_catalog"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Add what matters", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 15.dp))
                CompositionLocalProvider(LocalGlassContentColor provides MaterialTheme.colorScheme.onSurface) {
                    HomeWidgets(LocalHomeClock.current, Modifier.height(190.dp),
                        onClock = { onEditBuiltin?.invoke("clock") },
                        onCalendar = { onEditBuiltin?.invoke("calendar") },
                        showClock = appearance?.showClockWidget ?: true,
                        showCalendar = appearance?.showCalendarWidget ?: true)
                }
                IosGroup {
                    IosToggleRow("Clock & Calendar on Home", showHomeWidgets, onChange = onToggleHome)
                    if (showHomeWidgets && appearance != null && onAppearance != null) {
                        IosDivider()
                        IosToggleRow("Clock Widget", appearance.showClockWidget) { onAppearance(appearance.copy(showClockWidget = it)) }
                        IosDivider()
                        IosToggleRow("Calendar Widget", appearance.showCalendarWidget) { onAppearance(appearance.copy(showCalendarWidget = it)) }
                    }
                }
                IosSectionLabel("Add to")
                IosSegmented(listOf("Home Screen", "Today"), if (destinationHome) 0 else 1,
                    Modifier.testTag("widget_destination")) { destinationHome = it == 0 }
                if (destinationHome) {
                    IosSectionLabel("Size")
                    IosSegmented(listOf("Small", "Medium", "Large"), size, Modifier.testTag("gallery_widget_size")) { size = it }
                }
                IosSectionLabel("Widgets from your apps")
                Text(if (destinationHome) "Add to Home page ${(initialHomePage ?: 0) + 1}. Apps make room automatically."
                    else "Add widgets to Today, to the left of Home.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 17.dp, bottom = 8.dp))
                IosGroup {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        LauncherSymbol(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(query, { query = it.take(100) }, Modifier.weight(1f).padding(start = 10.dp).testTag("widget_search"),
                            singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            decorationBox = { inner -> if (query.isEmpty()) Text("Search Widgets", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() })
                    }
                }
                if (controller.ids.size >= 8) Text("You have 8 widgets. Remove one to add another.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            items(filtered, key = { it.provider.flattenToString() }) { info ->
                IosGroup {
                    WidgetPreview(info)
                    IosRow(info.loadLabel(context.packageManager), "Add", enabled = controller.ids.size < 8 && controller.pendingId < 0) {
                        try {
                            val id = controller.beginAdd(if (destinationHome) initialHomePage ?: 0 else null,
                                columns = if (size == 0) 2 else 4, rows = if (size == 2) 4 else 2) ?: return@IosRow
                            if (controller.manager.bindAppWidgetIdIfAllowed(id, info.provider)) configure(id)
                            else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile))
                        } catch (_: Exception) { controller.cancelAdd(); onError("Could not add this widget.") }
                    }
                    Text(if (destinationHome) "${if (size == 0) 2 else 4} × ${if (size == 2) 4 else 2} · Home Screen" else "Today", fontSize = 12.sp,
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
