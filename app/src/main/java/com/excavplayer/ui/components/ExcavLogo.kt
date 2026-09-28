package com.excavplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.excavplayer.ui.theme.CyanAccent

/**
 * Branded ExcavPlayer logo: Precision cyan outlined ring with centered play icon.
 */
@Composable
fun ExcavLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = CyanAccent
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.085f

        // Outer circular stroke
        drawCircle(
            color = tint,
            radius = (w - strokeWidth) / 2f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(width = strokeWidth)
        )

        // Optically balanced play triangle
        val startX = w * 0.41f
        val endX = w * 0.67f
        val topY = h * 0.33f
        val bottomY = h * 0.67f
        val midY = h * 0.50f

        val playPath = Path().apply {
            moveTo(startX, topY)
            lineTo(endX, midY)
            lineTo(startX, bottomY)
            close()
        }

        drawPath(
            path = playPath,
            color = tint,
            style = Fill
        )
    }
}
