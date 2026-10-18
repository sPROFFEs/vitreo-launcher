package dev.vitreo.launcher.ui

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.vitreo.launcher.R
import dev.vitreo.launcher.data.IconPacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object CyberColors {
    val Background = Color(0xff090d13)
    val Panel = Color(0xff141b23)
    val Red = Color(0xffff4b5c)
    val Cyan = Color(0xff63f5f7)
    val Yellow = Color(0xfffcee0a)
    val Text = Color(0xffeaf2f2)
}
val CyberFont = FontFamily(Font(R.font.rajdhani_medium), Font(R.font.rajdhani_semibold, FontWeight.SemiBold))
val CyberShape = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
val LocalCyber = staticCompositionLocalOf { false }
data class PackEnvironment(val reader: IconPacks? = null, val revision: Int = 0)
val LocalIconPacks = staticCompositionLocalOf { PackEnvironment() }

/** CYBERCORE outline variants, converted to native vectors; see third_party/cybercore. */
@Composable
fun LauncherSymbol(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current) {
    val resource = if (!LocalCyber.current) null else when (imageVector.name.substringAfterLast('.')) {
        "Search" -> R.drawable.cyber_ui_search; "Settings" -> R.drawable.cyber_ui_settings
        "ChevronLeft" -> R.drawable.cyber_ui_chevron_left; "ChevronRight" -> R.drawable.cyber_ui_chevron_right
        "Wifi" -> R.drawable.cyber_ui_wifi; "Bluetooth" -> R.drawable.cyber_ui_bluetooth
        "Add" -> R.drawable.cyber_ui_plus; "Remove" -> R.drawable.cyber_ui_minus
        "MoreHoriz" -> R.drawable.cyber_ui_menu_dots; "VolumeUp" -> R.drawable.cyber_ui_volume_high
        "Home" -> R.drawable.cyber_ui_home; "Tune" -> R.drawable.cyber_ui_sliders
        "Check" -> R.drawable.cyber_ui_check
        else -> null
    }
    if (resource == null) Icon(imageVector, contentDescription, modifier, tint)
    else Icon(painterResource(resource), contentDescription, modifier, if (tint == Color.White) CyberColors.Cyan else tint)
}

/** Original native HUD geometry. A single bounded reveal; resting surfaces have no animation loop. */
@Composable
fun CyberPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val appearance = LocalGlass.current.appearance
    val animated = appearance.cyberEffects && !appearance.reducedEffects
    val phase = LocalCyberPhase.current
    val reveal = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(animated) { if (animated) reveal.animateTo(1f, tween(180)) else reveal.snapTo(1f) }
    Box(modifier.clip(CyberShape).background(CyberColors.Panel).border(1.dp, CyberColors.Red.copy(alpha = .7f), CyberShape)) {
        Canvas(Modifier.matchParentSize()) {
            val p = reveal.value
            if (appearance.cyberImmersive && animated) {
                val sweep = phase.value
                drawLine(CyberColors.Yellow.copy(alpha = .75f), Offset(size.width * sweep, 1.dp.toPx()),
                    Offset((size.width * sweep + size.width * .16f).coerceAtMost(size.width), 1.dp.toPx()), 2.dp.toPx())
            }
            drawLine(CyberColors.Cyan.copy(alpha = .65f), Offset(0f, 1.dp.toPx()), Offset(size.width * .22f, 1.dp.toPx()), 2.dp.toPx())
            drawLine(CyberColors.Red, Offset(size.width - 2.dp.toPx(), size.height * .3f), Offset(size.width - 2.dp.toPx(), size.height * .7f), 2.dp.toPx())
            if (p < 1f) {
                val x = size.width * p
                drawLine(CyberColors.Cyan.copy(alpha = (1f - p) * .5f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                // A brief decorative displacement, never applied to the panel's text/content.
                if (p in .15f.. .5f) drawLine(CyberColors.Red.copy(alpha = .4f), Offset(3.dp.toPx(), size.height * .32f), Offset(size.width * .35f, size.height * .32f), 2.dp.toPx())
            }
        }
        CompositionLocalProvider(LocalGlassContentColor provides CyberColors.Text, LocalContentColor provides CyberColors.Text, content = { content() })
    }
}

@Composable
fun CyberBackdrop(photo: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.testTag("cyber_backdrop")) {
        drawRect(CyberColors.Background.copy(alpha = if (photo) .78f else 1f))
        val step = 44.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(CyberColors.Cyan.copy(alpha = .025f), Offset(x, 0f), Offset(x, size.height)); x += step }
        var y = 0f
        while (y < size.height) { drawLine(CyberColors.Cyan.copy(alpha = .025f), Offset(0f, y), Offset(size.width, y)); y += step }
        val w = size.width; val h = size.height
        drawLine(CyberColors.Red.copy(alpha = .3f), Offset(w * .7f, h * .08f), Offset(w * .94f, h * .08f), 1.dp.toPx())
        drawLine(CyberColors.Red.copy(alpha = .3f), Offset(w * .94f, h * .08f), Offset(w, h * .08f + w * .06f), 1.dp.toPx())
        drawLine(CyberColors.Cyan.copy(alpha = .18f), Offset(0f, h * .82f), Offset(w * .18f, h * .82f), 1.dp.toPx())
    }
}

fun cyberArtwork(kind: String?): Int? = when (kind) {
    "phone" -> R.drawable.cyber_phone; "messages" -> R.drawable.cyber_messages
    "browser" -> R.drawable.cyber_browser; "camera" -> R.drawable.cyber_camera
    "photos" -> R.drawable.cyber_photos; "clock" -> R.drawable.cyber_clock
    "calendar" -> R.drawable.cyber_calendar; "settings" -> R.drawable.cyber_settings
    "contacts" -> R.drawable.cyber_contacts; "music" -> R.drawable.cyber_music
    "files" -> R.drawable.cyber_files; "calculator" -> R.drawable.cyber_calculator
    "store" -> R.drawable.cyber_store; "maps" -> R.drawable.cyber_maps
    "mail" -> R.drawable.cyber_mail; "notes" -> R.drawable.cyber_notes
    "weather" -> R.drawable.cyber_weather; "telegram" -> R.drawable.cyber_telegram
    "whatsapp" -> R.drawable.cyber_whatsapp; "signal" -> R.drawable.cyber_signal
    "youtube" -> R.drawable.cyber_youtube; "spotify" -> R.drawable.cyber_spotify
    "youtube_music" -> R.drawable.cyber_youtube_music
    else -> null
}

@Composable
fun packIcon(id: String): Bitmap? {
    val env = LocalIconPacks.current
    val appearance = LocalGlass.current.appearance
    val override = appearance.iconOverrides[id]
    val pack = appearance.iconPack
    val bitmap by produceState<Bitmap?>(null, id, pack, override, env) {
        value = null
        if (env.reader != null && override?.isTheme != true && (pack.isNotEmpty() || override != null))
            value = withContext(Dispatchers.IO) { env.reader.resolve(id, pack, override) }
    }
    return bitmap
}

@Composable
fun CyberHomeWidgets(now: Long, modifier: Modifier, onClock: () -> Unit, onCalendar: () -> Unit) {
    val context = LocalContext.current
    val date = remember(now / 60_000) { Date(now) }
    Row(modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(true, false).forEach { clock ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                CyberPanel(Modifier.weight(1f).fillMaxWidth().clickable(onClick = if (clock) onClock else onCalendar)
                    .testTag(if (clock) "clock_widget_card" else "calendar_widget_card")) {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(13.dp)) {
                        val fontScale = LocalDensity.current.fontScale
                        val large = minOf(if (clock) 36f else 56f, maxWidth.value / (if (clock) 2.9f else 1.8f) / fontScale)
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(if (clock) "LOCAL TIME" else SimpleDateFormat("EEEE", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH),
                                color = CyberColors.Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (clock) DateFormat.getTimeFormat(context).format(date) else SimpleDateFormat("dd", Locale.ENGLISH).format(date),
                                color = CyberColors.Cyan, fontFamily = CyberFont, fontWeight = FontWeight.SemiBold, fontSize = large.sp, maxLines = 1)
                            Text(if (clock) SimpleDateFormat("EEE / MMM dd", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH)
                                else SimpleDateFormat("MMM / yyyy", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH),
                                color = CyberColors.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(if (clock) "Clock" else "Calendar", Modifier.padding(top = 6.dp), color = CyberColors.Text, fontSize = 12.sp)
            }
        }
    }
}
