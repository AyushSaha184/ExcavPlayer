package com.excavplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ExcavDarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = ExcavPalette.TextSecondary,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
    error = ExcavPalette.Error
)

@Composable
fun ExcavTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ExcavDarkColors, typography = ExcavTypography, shapes = ExcavMaterialShapes, content = content)
}
