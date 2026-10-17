package dev.vitreo.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

@Composable
fun VitreoTheme(style: Int = 0, preset: Int = 0, content: @Composable () -> Unit) {
    val cyber = preset == 1
    val dark = style == 2 || style == 0 && isSystemInDarkTheme()
    val colors = if (cyber) darkColorScheme(primary = CyberColors.Cyan, onPrimary = CyberColors.Background,
        secondary = CyberColors.Red, background = CyberColors.Background, surface = CyberColors.Panel,
        surfaceVariant = Color(0xff202c37), onSurface = CyberColors.Text, onSurfaceVariant = Color(0xffa8bac4),
        outline = CyberColors.Red.copy(alpha = .7f), error = CyberColors.Red)
    else if (dark) darkColorScheme(primary = Color(0xff0a84ff), secondary = Color(0xff30d158),
        background = Color.Black, surface = Color(0xff1c1c1e), surfaceVariant = Color(0xff2c2c2e),
        onSurface = Color(0xfff5f5f7), onSurfaceVariant = Color(0xff98989d), outline = Color(0xff38383a))
    else lightColorScheme(primary = Color(0xff007aff), secondary = Color(0xff34c759),
        background = Color(0xfff2f2f7), surface = Color.White, surfaceVariant = Color(0xffe5e5ea),
        onSurface = Color(0xff1c1c1e), onSurfaceVariant = Color(0xff626269), outline = Color(0xffc6c6c8))
    val font = if (cyber) CyberFont else FontFamily.SansSerif
    MaterialTheme(colorScheme = colors, typography = Typography(
        bodyLarge = TextStyle(fontFamily = font, fontSize = 17.sp),
        bodyMedium = TextStyle(fontFamily = font, fontSize = 15.sp),
        labelLarge = TextStyle(fontFamily = font, fontSize = 16.sp))) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, LocalCyber provides cyber, content = content)
    }
}
