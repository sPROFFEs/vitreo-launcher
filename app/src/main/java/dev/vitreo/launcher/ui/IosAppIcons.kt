package dev.vitreo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import dev.vitreo.launcher.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

val LocalHomeClock = compositionLocalOf { System.currentTimeMillis() }

/** Bundled SVG Stack artwork; see third_party/svgstack and the APK's license notice. */
fun commonIconKind(packageName: String): String? = when (packageName) {
    "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "org.fossify.phone" -> "phone"
    "com.android.messaging", "com.android.mms", "com.google.android.apps.messaging", "com.samsung.android.messaging", "org.fossify.messages" -> "messages"
    "com.android.chrome", "org.chromium.chrome", "org.mozilla.firefox", "org.mozilla.fenix", "app.vanadium.browser", "com.sec.android.app.sbrowser" -> "browser"
    "com.android.camera", "com.android.camera2", "app.grapheneos.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera" -> "camera"
    "com.android.gallery3d", "com.google.android.apps.photos", "org.fossify.gallery", "com.sec.android.gallery3d" -> "photos"
    "com.android.deskclock", "com.google.android.deskclock", "org.fossify.clock", "com.sec.android.app.clockpackage" -> "clock"
    "com.android.calendar", "com.google.android.calendar", "com.samsung.android.calendar", "org.fossify.calendar" -> "calendar"
    "com.android.settings" -> "settings"
    "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts", "org.fossify.contacts" -> "contacts"
    "com.android.music", "com.sec.android.app.music", "org.fossify.musicplayer" -> "music"
    "com.google.android.apps.youtube.music" -> "youtube_music"
    "org.telegram.messenger", "org.telegram.messenger.web" -> "telegram"
    "com.whatsapp" -> "whatsapp"
    "org.thoughtcrime.securesms" -> "signal"
    "com.google.android.youtube" -> "youtube"
    "com.spotify.music" -> "spotify"
    "com.samsung.android.app.notes", "com.google.android.keep", "org.fossify.notes" -> "notes"
    "com.google.android.apps.weather", "com.sec.android.daemonapp" -> "weather"
    "com.android.documentsui", "com.google.android.documentsui", "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles" -> "files"
    "com.android.calculator2", "com.google.android.calculator", "com.sec.android.app.popupcalculator", "org.fossify.math" -> "calculator"
    "com.android.vending" -> "store"
    "com.google.android.apps.maps" -> "maps"
    "com.google.android.gm", "com.android.email" -> "mail"
    else -> null
}

@Composable
fun IosAppIcon(kind: String, style: Int, tint: Color, modifier: Modifier = Modifier) {
    val artwork = when (kind) {
        "phone" -> R.drawable.ios_phone; "messages" -> R.drawable.ios_messages
        "browser" -> R.drawable.ios_browser; "camera" -> R.drawable.ios_camera
        "photos" -> R.drawable.ios_photos; "clock" -> R.drawable.ios_clock
        "calendar" -> R.drawable.ios_calendar; "settings" -> R.drawable.ios_settings
        "contacts" -> R.drawable.ios_contacts; "music" -> R.drawable.ios_music
        "files" -> R.drawable.ios_files; "calculator" -> R.drawable.ios_calculator
        "store" -> R.drawable.ios_store; "maps" -> R.drawable.ios_maps
        "mail" -> R.drawable.ios_mail; "notes" -> R.drawable.ios_notes
        "weather" -> R.drawable.ios_weather; "telegram" -> R.drawable.ios_telegram
        "whatsapp" -> R.drawable.ios_whatsapp; "signal" -> R.drawable.ios_signal
        "youtube" -> R.drawable.ios_youtube; "spotify" -> R.drawable.ios_spotify
        "youtube_music" -> R.drawable.ios_youtube_music
        else -> return
    }
    val monochrome = if (style < 2) null else ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        .2126f * tint.red, .7152f * tint.red, .0722f * tint.red, 0f, 0f,
        .2126f * tint.green, .7152f * tint.green, .0722f * tint.green, 0f, 0f,
        .2126f * tint.blue, .7152f * tint.blue, .0722f * tint.blue, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)))
    BoxWithConstraints(modifier) {
        val scale = maxWidth.value / 60f
        val textScale = scale / LocalDensity.current.fontScale
        if (style == 3) GlassPanel(Modifier.fillMaxSize(), radius = 15f * scale, tint = .1f) {}
        Image(painterResource(artwork), null, Modifier.fillMaxSize(), colorFilter = monochrome,
            alpha = if (style == 3) .72f else 1f)
        if (style == 1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)))
        // Keep the clock and date live while retaining the supplied clock case.
        if (kind == "clock") {
            val time = LocalHomeClock.current
            val cal = remember(time) { Calendar.getInstance().apply { timeInMillis = time } }
            val seconds = !LocalGlass.current.appearance.reducedEffects
            val ink = if (style == 0) Color(0xff171719) else if (style == 2) tint else Color.White
            val face = if (style == 0) Color(0xfff5f5f7) else Color(0xff202226)
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2); val r = size.minDimension * .435f
                drawCircle(face, r, center)
                val numbers = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink.toArgb(); textSize = size.minDimension * .112f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                }
                drawIntoCanvas { canvas ->
                    for (hour in 1..12) {
                        val a = hour * PI / 6
                        val position = center + Offset((sin(a) * r * .82).toFloat(), (-cos(a) * r * .82).toFloat())
                        canvas.nativeCanvas.drawText(hour.toString(), position.x,
                            position.y - (numbers.ascent() + numbers.descent()) / 2, numbers)
                    }
                }
                fun hand(a: Double, length: Float, width: Float, color: Color) {
                    drawLine(color, center, center + Offset((sin(a) * length).toFloat(), (-cos(a) * length).toFloat()), width, cap = StrokeCap.Round)
                }
                hand((cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60.0) * PI / 6, r * .56f, size.width * .018f, ink)
                hand(cal.get(Calendar.MINUTE) * PI / 30, r * .86f, size.width * .013f, ink)
                if (seconds) hand(cal.get(Calendar.SECOND) * PI / 30, r * .90f, size.width * .006f, Color(0xffff9f0a))
                drawCircle(ink, size.width * .022f, center)
            }
        } else if (kind == "calendar") {
            val time = LocalHomeClock.current
            val cal = remember(time) { Calendar.getInstance().apply { timeInMillis = time } }
            val ink = if (style == 0) Color(0xff171719) else if (style == 2) tint else Color.White
            Column(Modifier.fillMaxSize().background(if (style == 0) Color.White else Color(0xff202226))
                .padding(top = (7 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.ENGLISH).orEmpty().uppercase(Locale.ENGLISH),
                    color = if (style == 0) Color(0xffff453a) else ink, fontSize = (9 * textScale).sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(cal.get(Calendar.DAY_OF_MONTH).toString(), color = ink, fontSize = (32 * textScale).sp,
                    fontWeight = FontWeight.Light, lineHeight = (33 * textScale).sp)
            }
        }
    }
}
