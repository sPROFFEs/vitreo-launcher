package dev.vitreo.launcher.ui

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

data class CyberPalette(
    val name: String,
    val background: Color,
    val panel: Color,
    val accent: Color,
    val structural: Color,
    val info: Color,
    val red: Color,
    val cyan: Color,
    val yellow: Color,
    val text: Color,
)

object CyberColors {
    val Background = Color(0xff090d13)
    val Panel = Color(0xff141b23)
    val Red = Color(0xffff4b5c)
    val Cyan = Color(0xff63f5f7)
    val Yellow = Color(0xfffcee0a)
    val Text = Color(0xffeaf2f2)

    val Factions = listOf(
        CyberPalette("Kiroshi Optics", Color(0xff090d13), Color(0xff141b23), Color(0xfffcee0a), Color(0xffff4b5c), Color(0xff63f5f7), Color(0xffff4b5c), Color(0xff63f5f7), Color(0xfffcee0a), Color(0xffeaf2f2)),
        CyberPalette("Arasaka Corpo", Color(0xff050508), Color(0xff111116), Color(0xffff001e), Color(0xffe82030), Color(0xffffffff), Color(0xffff001e), Color(0xffffffff), Color(0xffff001e), Color(0xfff0f2f5)),
        CyberPalette("Netrunner Deep-Net", Color(0xff040907), Color(0xff09140f), Color(0xff00ff66), Color(0xff00b34d), Color(0xff00e5ff), Color(0xff00ff66), Color(0xff00e5ff), Color(0xff39ff14), Color(0xffe6f5ec)),
        CyberPalette("Trauma Team", Color(0xff060c0c), Color(0xff0d1717), Color(0xffff7700), Color(0xff00ffaa), Color(0xffffffff), Color(0xffff7700), Color(0xff00ffaa), Color(0xffff9900), Color(0xffe8f7f5)),
        CyberPalette("David", Color(0xff070e13), Color(0xff15201c), Color(0xffffd43b), Color(0xff71f77b), Color(0xff71e4e6), Color(0xffffba40), Color(0xff71e4e6), Color(0xffffd43b), Color(0xfff4f8e8)),
        CyberPalette("Lucy", Color(0xff0b0b1b), Color(0xff181b30), Color(0xffdce9ff), Color(0xffb29afa), Color(0xff90e8f3), Color(0xffd7a2ee), Color(0xff90e8f3), Color(0xffddd3ff), Color(0xfff3f3ff)),
        CyberPalette("Rebecca", Color(0xff0a1112), Color(0xff152327), Color(0xff99f3cc), Color(0xffff65b7), Color(0xff9effe1), Color(0xffff65b7), Color(0xff9effe1), Color(0xffb7ff8b), Color(0xfff0fff7)),
        CyberPalette("Kiwi", Color(0xff120c10), Color(0xff251820), Color(0xffffb1a6), Color(0xffe85e74), Color(0xffe3d3ad), Color(0xffe85e74), Color(0xffd6e8eb), Color(0xffe3d3ad), Color(0xfffcf2ed))
    )

    fun forFaction(faction: Int): CyberPalette = Factions[faction.coerceIn(0, Factions.lastIndex)]
}
val LocalCyberPalette = staticCompositionLocalOf { CyberColors.Factions[0] }
val CyberFont = FontFamily(Font(R.font.rajdhani_medium), Font(R.font.rajdhani_semibold, FontWeight.SemiBold))
val CyberShape = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
val LocalCyber = staticCompositionLocalOf { false }
val LocalInDock = staticCompositionLocalOf { false }
data class PackEnvironment(val reader: IconPacks? = null, val revision: Int = 0)
val LocalIconPacks = staticCompositionLocalOf { PackEnvironment() }

/** CYBERCORE outline variants, converted to native vectors; see third_party/cybercore. */
@Composable
fun LauncherSymbol(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current) {
    val palette = LocalCyberPalette.current
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
    else Icon(painterResource(resource), contentDescription, modifier, if (tint == Color.White) palette.cyan else tint)
}

/** Original native HUD geometry. A single bounded reveal; resting surfaces have no animation loop. */
@Composable
fun CyberPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val appearance = LocalGlass.current.appearance
    val palette = LocalCyberPalette.current
    val animated = appearance.cyberEffects && !appearance.reducedEffects
    val phase = LocalCyberPhase.current
    val reveal = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(animated) { if (animated) reveal.animateTo(1f, tween(180)) else reveal.snapTo(1f) }
    Box(modifier.clip(CyberShape).background(palette.panel).border(1.dp, palette.structural.copy(alpha = .7f), CyberShape)) {
        Canvas(Modifier.matchParentSize()) {
            val p = reveal.value
            if (animated && dev.vitreo.launcher.core.CYBER_EFFECT_PANEL_SWEEP in appearance.cyberNightEffects) {
                val sweep = phase.value
                drawLine(palette.accent.copy(alpha = .75f), Offset(size.width * sweep, 1.dp.toPx()),
                    Offset((size.width * sweep + size.width * .16f).coerceAtMost(size.width), 1.dp.toPx()), 2.dp.toPx())
            }
            drawLine(palette.info.copy(alpha = .65f), Offset(0f, 1.dp.toPx()), Offset(size.width * .22f, 1.dp.toPx()), 2.dp.toPx())
            drawLine(palette.structural, Offset(size.width - 2.dp.toPx(), size.height * .3f), Offset(size.width - 2.dp.toPx(), size.height * .7f), 2.dp.toPx())
            drawLine(palette.accent.copy(alpha = .6f), Offset(2.dp.toPx(), size.height - 10.dp.toPx()), Offset(2.dp.toPx(), size.height - 3.dp.toPx()), 1.5.dp.toPx())
            if (p < 1f) {
                val x = size.width * p
                drawLine(palette.info.copy(alpha = (1f - p) * .5f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                if (p in .15f.. .5f) drawLine(palette.structural.copy(alpha = .4f), Offset(3.dp.toPx(), size.height * .32f), Offset(size.width * .35f, size.height * .32f), 2.dp.toPx())
            }
        }
        CompositionLocalProvider(LocalGlassContentColor provides palette.text, LocalContentColor provides palette.text, content = { content() })
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
fun CyberHomeWidgets(now: Long, modifier: Modifier, onClock: () -> Unit, onCalendar: () -> Unit,
    showClock: Boolean = true, showCalendar: Boolean = true) {
    if (!showClock && !showCalendar) return
    val context = LocalContext.current
    val appearance = LocalGlass.current.appearance
    val palette = LocalCyberPalette.current
    val cyberdeckMode = dev.vitreo.launcher.core.CYBER_EFFECT_CYBERDECK_WIDGETS in appearance.cyberNightEffects
    val date = remember(if (cyberdeckMode) now / 1000 else now / 60_000) { Date(now) }
    val contentModifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)

    @Composable
    fun cyberCard(isClock: Boolean, cardModifier: Modifier) {
        CyberPanel(cardModifier.clickable(onClick = if (isClock) onClock else onCalendar)
            .testTag(if (isClock) "clock_widget_card" else "calendar_widget_card")) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(13.dp)) {
                val fontScale = LocalDensity.current.fontScale
                val large = minOf(if (isClock) (if (cyberdeckMode) 26f else 36f) else 52f, maxWidth.value / (if (isClock) 2.6f else 1.8f) / fontScale)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    if (isClock) {
                        if (cyberdeckMode) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("// CYBERDECK 2.1", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CyberFont)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(5.dp).background(palette.info, CircleShape))
                                    Text(" ONLINE", color = palette.info, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, fontFamily = CyberFont)
                                }
                            }
                            Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date),
                                color = palette.info, fontFamily = CyberFont, fontWeight = FontWeight.Bold, fontSize = large.sp, maxLines = 1)
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("RAM ", color = palette.structural, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CyberFont)
                                    Canvas(Modifier.weight(1f).height(5.dp)) {
                                        val totalSegs = 8
                                        val activeSegs = 6
                                        val gap = 2.dp.toPx()
                                        val segWidth = (size.width - (totalSegs - 1) * gap) / totalSegs
                                        for (i in 0 until totalSegs) {
                                            val x = i * (segWidth + gap)
                                            drawRect(if (i < activeSegs) palette.accent else palette.structural.copy(alpha = .25f),
                                                Offset(x, 0f), Size(segWidth, size.height))
                                        }
                                    }
                                    Text(" 8/8", color = palette.info, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = CyberFont)
                                }
                                Text("NIGHT CITY // SEC: OPTIMAL", color = palette.text.copy(alpha = .7f), fontSize = 10.sp, fontFamily = CyberFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Text("LOCAL TIME", color = palette.structural, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(DateFormat.getTimeFormat(context).format(date), color = palette.info, fontFamily = CyberFont, fontWeight = FontWeight.SemiBold, fontSize = large.sp, maxLines = 1)
                            Text(SimpleDateFormat("EEE / MMM dd", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH), color = palette.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        if (cyberdeckMode) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("// DATA SHARD", color = palette.structural, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CyberFont)
                                Text("TIER 5", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CyberFont)
                            }
                            Text(SimpleDateFormat("dd", Locale.ENGLISH).format(date),
                                color = palette.info, fontFamily = CyberFont, fontWeight = FontWeight.Bold, fontSize = large.sp, maxLines = 1)
                            Column {
                                Text(SimpleDateFormat("yyyy.MM.dd /// EEE", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH),
                                    color = palette.text, fontSize = 11.sp, fontFamily = CyberFont, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("CLEARANCE: GRANTED", color = palette.accent, fontSize = 10.sp, fontFamily = CyberFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Text(SimpleDateFormat("EEEE", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH), color = palette.structural, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(SimpleDateFormat("dd", Locale.ENGLISH).format(date), color = palette.info, fontFamily = CyberFont, fontWeight = FontWeight.SemiBold, fontSize = large.sp, maxLines = 1)
                            Text(SimpleDateFormat("MMM / yyyy", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH), color = palette.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showClock && showCalendar) {
        Row(contentModifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            cyberCard(true, Modifier.weight(1f).fillMaxHeight())
            cyberCard(false, Modifier.weight(1f).fillMaxHeight())
        }
    } else if (showClock) {
        Row(contentModifier) {
            cyberCard(true, Modifier.fillMaxWidth(.5f).fillMaxHeight().padding(end = 5.dp))
        }
    } else {
        Row(contentModifier) {
            cyberCard(false, Modifier.fillMaxWidth(.5f).fillMaxHeight().padding(end = 5.dp))
        }
    }
}
