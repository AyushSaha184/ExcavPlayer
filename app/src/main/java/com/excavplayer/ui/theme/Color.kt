package com.excavplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Backgrounds (atmospheric blue-black depth, not pure black)
val BackgroundDark = Color(0xFF09111A)
val BackgroundDarkSecondary = Color(0xFF0C151F)
val BackgroundGradientTop = Color(0xFF0F1B27)
val BackgroundGradientBottom = Color(0xFF080D14)

val SurfaceDark = Color(0xFF111B26)
val SurfaceDarkElevated = Color(0xFF172330)

// Frosted Glass Surfaces
val SurfaceGlass = Color(0xCC172330)
val SurfaceGlassLight = Color(0x661D2B3A)

// Subtle Card Borders
val SurfaceBorder = Color(0x263B5367)
val SurfaceBorderStrong = Color(0x40415D72)
val SurfaceBorderLight = Color(0x1A476277)

// Accents (Cyan used sparingly: selected nav, progress, active switches, tiny status)
val CyanAccent = Color(0xFF00B8F0)
val CyanAccentBright = Color(0xFF28C7FF)
val CyanAccentDark = Color(0xFF008BB8)
val CyanGlow = Color(0x2600B8F0)

// Status & Special Colors
val FavoriteRed = Color(0xFFEF4444)
val FavoriteRedGlow = Color(0x33EF4444)
val FolderYellow = Color(0xFFF59E0B)
val CompletedGreen = Color(0xFF10B981)
val ScrubberBuffer = Color(0x33FFFFFF)
val ScrubberInactive = Color(0x33FFFFFF)

// Text Colors (Refined contrast)
val TextPrimary = Color(0xFFF4F7FA)
val TextSecondary = Color(0xFFA5B4C2)
val TextTertiary = Color(0xFF718295)

// Translucents & Badges
val ScrimDark = Color(0xCC000000)
val ScrimLight = Color(0x80000000)
val BadgeBackground = Color(0xB3000000)
val FloatingPill = Color(0xCC111B26)

val ExcavBackgroundGradient = Brush.verticalGradient(
    colors = listOf(BackgroundGradientTop, BackgroundGradientBottom)
)

fun Modifier.excavBackground(): Modifier = this.background(ExcavBackgroundGradient)
