package com.zhiwei.filemanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = ZiBlueLight,
    onPrimary = Color(0xFF0A1020),
    secondary = ZiCyan,
    onSecondary = Color(0xFF04212A),
    background = ZiDarkBg,
    onBackground = Color(0xFFE6E9F2),
    surface = ZiDarkSurface,
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFB9C0D0),
    outline = Color(0xFF48506A),
    error = Color(0xFFFF6B6B)
)

private val LightScheme = lightColorScheme(
    primary = ZiBlue,
    onPrimary = Color.White,
    secondary = ZiCyanDark,
    onSecondary = Color.White,
    background = ZiLightBg,
    onBackground = Color(0xFF1A1C22),
    surface = ZiLightSurface,
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF4C5468),
    outline = Color(0xFFC3C9D8),
    error = Color(0xFFD3302F)
)

@Composable
fun ZhiweiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ZhiweiTypography,
        content = content
    )
}
