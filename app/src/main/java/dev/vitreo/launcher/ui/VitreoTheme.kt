package dev.vitreo.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val type = if (cyber) Typography(
        bodyLarge = TextStyle(fontFamily = font, fontSize = 17.sp),
        bodyMedium = TextStyle(fontFamily = font, fontSize = 15.sp),
        labelLarge = TextStyle(fontFamily = font, fontSize = 16.sp)) else Typography(
        displayLarge = TextStyle(fontFamily = font, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = .25.sp),
        headlineLarge = TextStyle(fontFamily = font, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = .2.sp),
        headlineMedium = TextStyle(fontFamily = font, fontSize = 22.sp, lineHeight = 28.sp),
        headlineSmall = TextStyle(fontFamily = font, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = font, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.2).sp),
        titleMedium = TextStyle(fontFamily = font, fontSize = 16.sp, lineHeight = 21.sp),
        titleSmall = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = font, fontSize = 17.sp, letterSpacing = (-.2).sp),
        bodyMedium = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = font, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = font, fontSize = 16.sp, lineHeight = 21.sp),
        labelMedium = TextStyle(fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = font, fontSize = 11.sp, lineHeight = 13.sp))
    MaterialTheme(colorScheme = colors, typography = type) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, LocalCyber provides cyber, content = content)
    }
}
