package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.excavplayer.ui.theme.CardShape
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceGlass

/**
 * Atmospheric frosted-glass container with subtle translucent border.
 * Acts as the foundational container for cards, panels, and modal sheets.
 */
@Composable
fun ExcavSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    backgroundColor: Color = SurfaceGlass,
    borderColor: Color = SurfaceBorder,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape),
        content = content
    )
}
