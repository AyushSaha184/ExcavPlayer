package com.excavplayer.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.excavplayer.ui.components.backdrop.Backdrop
import com.excavplayer.ui.components.backdrop.backdrops.LayerBackdrop
import com.excavplayer.ui.components.backdrop.drawBackdrop
import com.excavplayer.ui.components.backdrop.effects.blur
import com.excavplayer.ui.components.backdrop.effects.colorControls
import com.excavplayer.ui.components.backdrop.effects.lens
import com.excavplayer.ui.components.backdrop.highlight.Highlight
import com.excavplayer.ui.components.backdrop.highlight.HighlightElement
import com.excavplayer.ui.components.backdrop.internal.ShapeProvider
import com.excavplayer.ui.components.backdrop.shadow.Shadow

import com.excavplayer.ui.theme.ExcavPalette

/** Whether liquid glass is enabled — backed by user settings. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** Whether dynamic blur is completely removed (solid colors used) when liquid glass is off. */
val LocalRemoveDynamicBlur = staticCompositionLocalOf { false }

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }

internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f
private const val GLASS_RESOLUTION_SCALE = 0.33f

internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.12f)

/**
 * Returns the adaptive border stroke used across card, folder, and item containers:
 * - Liquid glass enabled: crisp refraction-style luminous white hairline border (GLASS_EDGE_COLOR)
 * - Liquid glass off: neutral line border (ExcavPalette.Line)
 */
@Composable
fun adaptiveCardBorder(
    isSelected: Boolean = false,
    selectedColor: Color = ExcavPalette.Gray
): BorderStroke {
    val isGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    return remember(isGlass, isSelected, selectedColor) {
        if (isSelected) {
            BorderStroke(1.5.dp, selectedColor)
        } else if (isGlass) {
            BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f))
        } else {
            BorderStroke(1.dp, ExcavPalette.Line)
        }
    }
}

@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }

@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color = Color(0xF21C1C1E),
): Modifier {
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val glassTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val shapeProvider = remember(shape) { ShapeProvider { shape } }

    return clip(shape)
        .background(
            color = if (useGlass) glassTint.copy(alpha = SURFACE_OPACITY) else fallbackColor,
            shape = shape,
        )
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = { Highlight.Default },
                )
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

@Composable
fun Modifier.liquidGlassRim(shape: CornerBasedShape): Modifier {
    if (!LocalLiquidGlassEnabled.current || !isGlassSupported()) return this
    val shapeProvider = remember(shape) { ShapeProvider { shape } }
    return then(
        HighlightElement(
            shapeProvider = shapeProvider,
            highlight = { Highlight.Default },
        ),
    )
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop].
 * Falls back cleanly when Liquid Glass is disabled or unsupported.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    surfaceTint: Color? = null,
): Modifier {
    val isEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    val removeDynamicBlur = LocalRemoveDynamicBlur.current
    val backdrop = LocalAppBackdrop.current
    if (!isEnabled || backdrop == null) {
        // Fallback to solid or frosted dark surface with subtle border
        val fallbackTint = when {
            removeDynamicBlur -> ExcavPalette.SurfaceCard
            surfaceTint != null -> surfaceTint
            else -> ExcavPalette.SurfaceCard.copy(alpha = 0.88f)
        }
        return clip(shape)
            .background(fallbackTint, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }

    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val themeTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = surfaceTint ?: themeTint.copy(alpha = SURFACE_OPACITY)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = {
            drawRect(color = surfaceTintColor, size = size)
        },
        exportedBackdrop = exportedBackdrop,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

/**
 * Universal interactive Liquid Glass button matching the navbar and search button design.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    shape: CornerBasedShape = RoundedCornerShape(percent = 50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit
) {
    val isGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val removeDynamicBlur = LocalRemoveDynamicBlur.current

    val surfaceTint = when {
        isDestructive -> ExcavPalette.Error.copy(alpha = if (isGlass) 0.35f else 0.85f)
        isPrimary -> ExcavPalette.Blue.copy(alpha = if (isGlass) 0.35f else 0.85f)
        isGlass -> Color.White.copy(alpha = 0.08f)
        removeDynamicBlur -> ExcavPalette.SurfaceCardHighlight
        else -> Color.White.copy(alpha = 0.10f)
    }

    val borderColor = when {
        isDestructive -> ExcavPalette.Error.copy(alpha = 0.5f)
        isPrimary -> ExcavPalette.Blue.copy(alpha = 0.5f)
        isGlass -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.16f)
    }

    val shapeProvider = remember(shape) { ShapeProvider { shape } }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(surfaceTint, shape)
            .then(
                if (isGlass) {
                    HighlightElement(
                        shapeProvider = shapeProvider,
                        highlight = { Highlight.Default }
                    )
                } else {
                    Modifier
                }
            )
            .border(GLASS_EDGE_WIDTH, borderColor, shape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = if (isDestructive) ExcavPalette.Error else ExcavPalette.Blue),
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}


