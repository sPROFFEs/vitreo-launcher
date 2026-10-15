package dev.vitreo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

val LocalHomeClock = compositionLocalOf { System.currentTimeMillis() }

/** Original vector artwork for common Android apps, not an imported icon pack. */
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
    "com.android.music", "com.google.android.apps.youtube.music", "com.sec.android.app.music", "org.fossify.musicplayer" -> "music"
    "com.android.documentsui", "com.google.android.documentsui", "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles" -> "files"
    "com.android.calculator2", "com.google.android.calculator", "com.sec.android.app.popupcalculator", "org.fossify.math" -> "calculator"
    "com.android.vending" -> "store"
    "com.google.android.apps.maps" -> "maps"
    "com.google.android.gm", "com.android.email" -> "mail"
    else -> null
}

@Composable
fun IosAppIcon(kind: String, style: Int, tint: Color, modifier: Modifier = Modifier) {
    val colors = when (kind) {
        "phone", "messages" -> listOf(Color(0xff63e479), Color(0xff16bb43))
        "music" -> listOf(Color(0xffff667c), Color(0xffed294f))
        "mail", "store" -> listOf(Color(0xff4cb3ff), Color(0xff0878f3))
        "maps" -> listOf(Color(0xffa7e0b8), Color(0xff39ac88))
        "contacts" -> listOf(Color(0xffcfbca6), Color(0xffaa9581))
        "settings", "camera" -> listOf(Color(0xffe2e5e9), Color(0xffa9b1bb))
        "clock", "calculator" -> listOf(Color(0xff202226), Color(0xff050609))
        else -> listOf(Color(0xfffafafa), Color(0xfff1f4fa))
    }
    val ink = if (style == 2) tint else Color.White
    BoxWithConstraints(modifier) {
        val scale = maxWidth.value / 60f
        val textScale = scale / LocalDensity.current.fontScale
        if (style == 3) GlassPanel(Modifier.fillMaxSize(), radius = 15f * scale, tint = .1f) {}
        else Box(Modifier.fillMaxSize().background(if (style > 0) Brush.verticalGradient(listOf(Color(0xff30343c), Color(0xff11151b))) else Brush.verticalGradient(colors)))
        when (kind) {
            "clock" -> {
                val time = LocalHomeClock.current
                val cal = remember(time) { Calendar.getInstance().apply { timeInMillis = time } }
                val seconds = !LocalGlass.current.appearance.reducedEffects
                Canvas(Modifier.fillMaxSize().padding((5 * scale).dp)) {
                    val center = Offset(size.width / 2, size.height / 2); val r = size.minDimension * .47f
                    repeat(12) { index ->
                        val a = index * PI / 6
                        drawLine(ink, center + Offset((sin(a)*r*.85).toFloat(),(-cos(a)*r*.85).toFloat()),
                            center + Offset((sin(a)*r).toFloat(),(-cos(a)*r).toFloat()),(1.4 * scale).dp.toPx(),cap=StrokeCap.Round)
                    }
                    fun hand(a: Double, length: Float, width: Float, color: Color) {
                        drawLine(color,center,center+Offset((sin(a)*length).toFloat(),(-cos(a)*length).toFloat()),width,cap=StrokeCap.Round)
                    }
                    hand((cal.get(Calendar.HOUR)+cal.get(Calendar.MINUTE)/60.0)*PI/6,r*.5f,(2.7 * scale).dp.toPx(),ink)
                    hand(cal.get(Calendar.MINUTE)*PI/30,r*.78f,(1.8 * scale).dp.toPx(),ink)
                    if (seconds) hand(cal.get(Calendar.SECOND)*PI/30,r*.88f,(1 * scale).dp.toPx(),Color(0xffff9f0a))
                    drawCircle(Color(0xffff9f0a),(1.7 * scale).dp.toPx(),center)
                }
            }
            "calendar" -> {
                val time = LocalHomeClock.current
                val cal = remember(time) { Calendar.getInstance().apply { timeInMillis = time } }
                Column(Modifier.fillMaxSize().padding(top = (7 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.ENGLISH).orEmpty().uppercase(Locale.ENGLISH),
                        color = if (style == 0) Color(0xffff453a) else ink, fontSize = (9 * textScale).sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(cal.get(Calendar.DAY_OF_MONTH).toString(), color = if (style == 0) Color(0xff171719) else ink,
                        fontSize = (32 * textScale).sp, fontWeight = FontWeight.Light, lineHeight = (33 * textScale).sp)
                }
            }
            "photos" -> Canvas(Modifier.fillMaxSize().padding((9 * scale).dp)) {
                val center=Offset(size.width/2,size.height/2)
                val shades=listOf(0xffffac33,0xffffd93b,0xffa6d84a,0xff36ba88,0xff5bc9e2,0xff687cdb,0xffd877c3,0xfff46a78)
                repeat(8) { i ->
                    val angle=i*PI/4
                    drawCircle(if(style==0) Color(shades[i]).copy(alpha=.85f) else ink.copy(alpha=.6f),size.minDimension*.24f,
                        center+Offset((sin(angle)*size.width*.24).toFloat(),(-cos(angle)*size.height*.24).toFloat()))
                }
            }
            "browser" -> Canvas(Modifier.fillMaxSize().padding((6 * scale).dp)) {
                val center=Offset(size.width/2,size.height/2);val r=size.minDimension*.49f
                drawCircle(if(style==0)Color(0xff189ee6) else ink.copy(alpha=.2f),r,center)
                repeat(24) { i ->
                    val a=i*PI/12;drawLine(ink.copy(alpha=.75f),center+Offset((sin(a)*r*.88).toFloat(),(-cos(a)*r*.88).toFloat()),
                        center+Offset((sin(a)*r*.96).toFloat(),(-cos(a)*r*.96).toFloat()),(.6 * scale).dp.toPx())
                }
                val red=Path().apply {moveTo(center.x+r*.62f,center.y-r*.62f);lineTo(center.x-r*.13f,center.y-r*.13f);lineTo(center.x+r*.13f,center.y+r*.13f);close()}
                val white=Path().apply {moveTo(center.x-r*.62f,center.y+r*.62f);lineTo(center.x-r*.13f,center.y-r*.13f);lineTo(center.x+r*.13f,center.y+r*.13f);close()}
                drawPath(red,if(style==0)Color(0xffff5262) else ink);drawPath(white,Color.White)
            }
            else -> {
                val vector = when(kind) {
                    "phone"->Icons.Filled.Phone; "messages"->Icons.Filled.ChatBubble; "camera"->Icons.Filled.CameraAlt
                    "settings"->Icons.Filled.Settings; "contacts"->Icons.Filled.Person; "music"->Icons.Filled.MusicNote
                    "files"->Icons.Filled.Folder; "calculator"->Icons.Filled.Calculate; "store"->Icons.Filled.ShoppingBag
                    "maps"->Icons.Filled.Map; else->Icons.Filled.Email
                }
                val color=when { style>0->ink; kind=="camera"||kind=="settings"->Color(0xff40464e); kind=="files"->Color(0xff409bfa);else->Color.White }
                Icon(vector,null,Modifier.fillMaxSize().padding(((if (kind == "settings") 5 else 10) * scale).dp),tint=color)
            }
        }
    }
}
