package dev.vitreo.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

@Composable
fun VitreoTheme(style: Int = 0, content: @Composable () -> Unit) {
    val dark = style == 2 || style == 0 && isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xff0a84ff), secondary = Color(0xff30d158),
        background = Color.Black, surface = Color(0xff1c1c1e), surfaceVariant = Color(0xff2c2c2e),
        onSurface = Color(0xfff5f5f7), onSurfaceVariant = Color(0xff98989d), outline = Color(0xff38383a))
    else lightColorScheme(primary = Color(0xff007aff), secondary = Color(0xff34c759),
        background = Color(0xfff2f2f7), surface = Color.White, surfaceVariant = Color(0xffe5e5ea),
        onSurface = Color(0xff1c1c1e), onSurfaceVariant = Color(0xff8e8e93), outline = Color(0xffc6c6c8))
    MaterialTheme(colorScheme = colors, typography = Typography(
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp)), content = content)
}
