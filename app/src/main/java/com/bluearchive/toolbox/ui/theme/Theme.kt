package com.bluearchive.toolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// BA 蓝主色调
private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5BD0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF4B6CAE),
    onSecondary = Color.White,
    tertiary = Color(0xFFE07B39),
    surface = Color(0xFFF7F9FF),
    background = Color(0xFFF7F9FF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF1A4490),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFA8C7FF),
    tertiary = Color(0xFFFFB68C),
)

@Composable
fun ToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
