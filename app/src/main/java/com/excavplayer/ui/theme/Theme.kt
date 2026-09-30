package com.excavplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ExcavDarkColors = darkColorScheme(
    primary = ExcavPalette.Blue,
    onPrimary = ExcavPalette.Ink,
    secondary = ExcavPalette.BlueDeep,
    background = ExcavPalette.Ink,
    surface = ExcavPalette.InkElevated,
    surfaceVariant = ExcavPalette.Glass,
    onBackground = ExcavPalette.Text,
    onSurface = ExcavPalette.Text,
    onSurfaceVariant = ExcavPalette.TextMuted,
    outline = ExcavPalette.Line,
    error = ExcavPalette.Error
)

@Composable
fun ExcavTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ExcavDarkColors, typography = ExcavTypography, shapes = ExcavMaterialShapes, content = content)
}
