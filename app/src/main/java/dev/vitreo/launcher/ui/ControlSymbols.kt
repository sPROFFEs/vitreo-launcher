package dev.vitreo.launcher.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Original geometric control symbols; no SF Symbols font or Apple image assets. */
internal object ControlSymbols {
    private fun symbol(name: String, data: String, filled: Boolean = false) = ImageVector.Builder(
        "Vitreo.$name", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(data).toNodes(),
            fill = if (filled) SolidColor(Color.White) else null,
            stroke = if (filled) null else SolidColor(Color.White), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    private val symbols = mapOf(
        "Wifi" to symbol("Wifi", "M3 8 Q12 1 21 8 M6.3 11.4 Q12 6.7 17.7 11.4 M9.4 14.6 Q12 12.5 14.6 14.6 M11.5 18 L12.5 18", false),
        "Bluetooth" to symbol("Bluetooth", "M7 7 L17 17 L12 22 L12 2 L17 7 L7 17"),
        "AirplanemodeActive" to symbol("Airplane", "M10.5 2 L13.5 2 L14.5 9 L22 13 L22 15 L14 13 L14 19 L17 21 L17 22 L12 21 L7 22 L7 21 L10 19 L10 13 L2 15 L2 13 L9.5 9 Z", true),
        "PhotoCamera" to symbol("Camera", "M3 7 L7 7 L9 4 L15 4 L17 7 L21 7 Q22 7 22 9 L22 19 Q22 20 20 20 L4 20 Q2 20 2 18 L2 9 Q2 7 3 7 Z M16.5 13.5 A4.5 4.5 0 1 1 7.5 13.5 A4.5 4.5 0 1 1 16.5 13.5 M18.7 9.5 L19 9.5"),
        "FlashlightOn" to symbol("Flashlight", "M6 3 L18 3 M6 6 L18 6 L15 11 L15 21 L9 21 L9 11 Z M12 12 L12 15"),
        "Calculate" to symbol("Calculator", "M7 2 L17 2 Q19 2 19 4 L19 20 Q19 22 17 22 L7 22 Q5 22 5 20 L5 4 Q5 2 7 2 Z M8 6 L16 6 M8 10 L9 10 M15 10 L16 10 M8 14 L9 14 M15 14 L16 14 M8 18 L9 18 M15 18 L16 18"),
        "Bedtime" to symbol("Focus", "M19.8 15.5 A9 9 0 0 1 8.5 4.2 A8.8 8.8 0 1 0 19.8 15.5 Z", true),
        "Notifications" to symbol("Bell", "M12 3 C7 3 6 7 6 11 L4 17 L20 17 L18 11 C18 7 17 3 12 3 Z M10 21 L14 21", true),
        "ScreenRotation" to symbol("Rotation", "M9 11 L15 11 L15 17 L9 17 Z M10 11 L10 9 A2 2 0 0 1 14 9 L14 11 M20 8 A9 9 0 1 0 20 17 M17 7 L21 8 L22 4"),
        "BrightnessHigh" to symbol("Sun", "M15.5 12 A3.5 3.5 0 1 1 8.5 12 A3.5 3.5 0 1 1 15.5 12 M12 2 L12 4 M12 20 L12 22 M2 12 L4 12 M20 12 L22 12 M5 5 L6.5 6.5 M17.5 17.5 L19 19 M5 19 L6.5 17.5 M17.5 6.5 L19 5"),
        "VolumeDown" to symbol("VolumeLow", "M3 9 L7 9 L12 5 L12 19 L7 15 L3 15 Z M16 8 Q20 12 16 16"),
        "VolumeUp" to symbol("Volume", "M3 9 L7 9 L12 5 L12 19 L7 15 L3 15 Z M16 8 Q20 12 16 16 M19 5 Q25 12 19 19"),
        "VolumeOff" to symbol("Muted", "M3 9 L7 9 L12 5 L12 19 L7 15 L3 15 Z M17 9 L22 15 M22 9 L17 15"),
        "MusicNote" to symbol("Music", "M10 17 L10 5 L21 3 L21 15 M10 8 L21 6 M10 17 A3 2.4 0 1 1 4 17 A3 2.4 0 1 1 10 17 M21 15 A3 2.4 0 1 1 15 15 A3 2.4 0 1 1 21 15"),
        "PlayArrow" to symbol("Play", "M7 3 L21 12 L7 21 Z", true),
        "Pause" to symbol("Pause", "M5 3 L10 3 L10 21 L5 21 Z M14 3 L19 3 L19 21 L14 21 Z", true),
        "SkipNext" to symbol("Next", "M2 5 L12 12 L2 19 Z M12 5 L22 12 L12 19 Z", true),
        "SkipPrevious" to symbol("Previous", "M22 5 L12 12 L22 19 Z M12 5 L2 12 L12 19 Z", true),
        "Cast" to symbol("Mirroring", "M8 5 Q8 3 10 3 L21 3 Q23 3 23 5 L23 14 Q23 16 21 16 M3 8 L16 8 Q18 8 18 10 L18 19 Q18 21 16 21 L3 21 Q1 21 1 19 L1 10 Q1 8 3 8 Z"),
        "Podcasts" to symbol("MediaOutput", "M8 21 L16 21 L12 15 Z M7.5 16 A6 6 0 1 1 16.5 16 M4.5 19 A10 10 0 1 1 19.5 19"),
        "FavoriteBorder" to symbol("Favorites", "M12 21 C9 18 2 13 2 8 A5 5 0 0 1 12 6 A5 5 0 0 1 22 8 C22 13 15 18 12 21 Z", true),
        "Timer" to symbol("Timer", "M9 2 L15 2 M12 2 L12 5 M12 9 L12 14 L15 16 M21 14 A9 9 0 1 1 3 14 A9 9 0 1 1 21 14"),
        "BatteryStd" to symbol("Battery", "M3 6 L19 6 Q21 6 21 8 L21 16 Q21 18 19 18 L3 18 Q1 18 1 16 L1 8 Q1 6 3 6 Z M23 10 L23 14 M5 10 L17 10 L17 14 L5 14 Z"),
        "BatterySaver" to symbol("BatterySaver", "M3 6 L19 6 Q21 6 21 8 L21 16 Q21 18 19 18 L3 18 Q1 18 1 16 L1 8 Q1 6 3 6 Z M23 10 L23 14 M11 9 L11 15 M8 12 L14 12"),
        "PowerSettingsNew" to symbol("Power", "M12 2 L12 11 M6.5 5 A9 9 0 1 0 17.5 5"),
        "Add" to symbol("Add", "M12 4 L12 20 M4 12 L20 12"),
        "Close" to symbol("Close", "M5 5 L19 19 M19 5 L5 19"),
        "Check" to symbol("Done", "M4 12 L10 18 L21 5")
    )
    fun resolve(icon: ImageVector) = symbols[if (icon.name.substringAfterLast('.') == "DarkMode") "Bedtime" else icon.name.substringAfterLast('.')] ?: icon
}

@Composable
internal fun ControlSymbol(icon: ImageVector, description: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    LauncherSymbol(if (LocalCyber.current) icon else ControlSymbols.resolve(icon), description, modifier, tint)
}
