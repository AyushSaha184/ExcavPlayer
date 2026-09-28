package com.excavplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ExcavSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val CardShape = RoundedCornerShape(16.dp)
val SurfaceShape = RoundedCornerShape(16.dp)
val ThumbnailShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(100.dp)
val BottomSheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
val DialogShape = RoundedCornerShape(18.dp)
val BadgeShape = RoundedCornerShape(4.dp)
