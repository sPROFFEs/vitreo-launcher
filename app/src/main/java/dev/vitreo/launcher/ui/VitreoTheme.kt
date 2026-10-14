package dev.vitreo.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun VitreoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xffadf6dc), onPrimary = Color(0xff13382f),
        secondary = Color(0xffb7d9eb), surface = Color(0xff172333),
        background = Color(0xff101b2b), onSurface = Color(0xfff0f5f7),
        surfaceVariant = Color(0xff2c3c4d), onSurfaceVariant = Color(0xffc6d4dd),
    ), content = content)
}
