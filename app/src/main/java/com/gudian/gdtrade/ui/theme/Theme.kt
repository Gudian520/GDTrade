package com.gudian.gdtrade.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5A),
    secondary = Color(0xFF2E5EAA),
    tertiary = Color(0xFF9A5A14),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF17211F),
    onSurface = Color(0xFF17211F)
)

@Composable
fun GDTradeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
