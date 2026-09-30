package com.excavplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object ExcavShapes {
    val Squircle = RoundedCornerShape(18.dp)
    val Card = RoundedCornerShape(18.dp)
    val CardInner = RoundedCornerShape(14.dp)
    val Row = RoundedCornerShape(14.dp)
    val Panel = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val Pill = RoundedCornerShape(50)
    val Badge = RoundedCornerShape(6.dp)
}

val ExcavMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = ExcavShapes.Card,
    large = ExcavShapes.Panel,
    extraLarge = RoundedCornerShape(28.dp)
)
