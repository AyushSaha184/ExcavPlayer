package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.excavplayer.R
import com.excavplayer.ui.theme.ExcavPalette
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseIn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer

/**
 * CompositionLocal providing HazeState for blur effects.
 */
val LocalHazeState = compositionLocalOf { HazeState() }

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassmorphicItem(
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current,
    cornerRadius: Int = 20,
    blurRadius: Int = 10,
    containerColor: Color = Color(0x66141720),
    hazeStyle: HazeStyle = CupertinoMaterials.ultraThin(containerColor = containerColor),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius.dp) }

    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(state = hazeState, style = hazeStyle)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassmorphicBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    hazeState: HazeState = LocalHazeState.current
) {
    GlassmorphicItem(
        modifier = modifier.size(size),
        cornerRadius = (size.value / 2).toInt(),
        hazeState = hazeState
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = if (enabled) ExcavPalette.Text else ExcavPalette.TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassmorphicHeaderActions(
    onSearch: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassmorphicItem(
            modifier = Modifier.size(40.dp),
            cornerRadius = 20,
            hazeState = hazeState
        ) {
            IconButton(
                onClick = onSearch,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = ExcavPalette.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (onRefresh != null) {
            GlassmorphicItem(
                modifier = Modifier.size(40.dp),
                cornerRadius = 20,
                hazeState = hazeState
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = ExcavPalette.Text,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Progressive top header container that wraps header content in a seamless
 * linear blur gradient (full blur at the top, smoothly graduating to transparent below).
 * Scrim and progressive blur fade in cleanly when content is scrolled underneath.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ProgressiveHeaderContainer(
    modifier: Modifier = Modifier,
    isScrolled: Boolean = true,
    hazeState: HazeState = LocalHazeState.current,
    fadeHeight: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    val blurAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "header_blur_alpha"
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Progressive Glass Backdrop spanning full header + fade area (only visible when scrolled)
        if (blurAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = blurAlpha }
                    .hazeEffect(
                        state = hazeState,
                        style = CupertinoMaterials.ultraThin(containerColor = Color.Transparent)
                    ) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f,
                            easing = EaseInCubic
                        )
                    }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ExcavPalette.Ink.copy(alpha = 0.88f),
                                ExcavPalette.Ink.copy(alpha = 0.50f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Header content with bottom padding for the gradient fade-out
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = fadeHeight)
        ) {
            content()
        }
    }
}

/**
 * Progressive linear blur scrim positioned below headings/headers.
 * Uses Haze progressive vertical gradient with EaseInCubic curve so scrolling content smoothly blurs as it passes underneath.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ProgressiveHeaderBlur(
    modifier: Modifier = Modifier,
    isScrolled: Boolean = true,
    height: Dp = 28.dp,
    hazeState: HazeState = LocalHazeState.current,
    containerColor: Color = Color.Black.copy(alpha = 0.3f)
) {
    val blurAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "scrim_blur_alpha"
    )

    if (blurAlpha > 0.001f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { alpha = blurAlpha }
                .hazeEffect(state = hazeState, style = CupertinoMaterials.ultraThin(containerColor = containerColor)) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                        easing = EaseInCubic
                    )
                }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            containerColor,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Fallback / Player glassmorphic blur modifier used by the video player.
 */
fun Modifier.glassmorphicBlur(
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color? = null,
    surfaceBrush: Brush? = null,
    strokeColor: Color = Color.White.copy(alpha = 0.35f),
    strokeWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val strokePx = strokeWidth.toPx()
        val fillBrush = surfaceBrush ?: if (backgroundColor != null) {
            Brush.verticalGradient(
                colors = listOf(
                    backgroundColor.copy(alpha = (backgroundColor.alpha * 1.25f).coerceAtMost(1f)),
                    backgroundColor,
                    backgroundColor.copy(alpha = (backgroundColor.alpha * 0.7f))
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0x33283244),
                    Color(0x2619202E),
                    Color(0x3810141D)
                )
            )
        }

        onDrawBehind {
            drawOutline(
                outline = outline,
                brush = fillBrush
            )
            drawOutline(
                outline = outline,
                color = strokeColor,
                style = Stroke(width = strokePx)
            )
        }
    }

/**
 * Dark ultra-thin glassmorphic blur modifier used for popup boxes, dialogs, bottom sheets, and menus.
 */
fun Modifier.darkUltraThinBlur(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color(0xDE101216),
    strokeColor: Color = Color.White.copy(alpha = 0.16f),
    strokeWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val strokePx = strokeWidth.toPx()
        val fillBrush = Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = (backgroundColor.alpha * 1.15f).coerceAtMost(0.96f)),
                backgroundColor,
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.85f))
            )
        )
        val borderBrush = Brush.verticalGradient(
            colors = listOf(
                strokeColor,
                strokeColor.copy(alpha = (strokeColor.alpha * 0.4f))
            )
        )

        onDrawBehind {
            drawOutline(
                outline = outline,
                brush = fillBrush
            )
            drawOutline(
                outline = outline,
                brush = borderBrush,
                style = Stroke(width = strokePx)
            )
        }
    }

/**
 * Standard dark ultra-thin glassmorphic dialog container.
 */
@Composable
fun GlassmorphicDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .darkUltraThinBlur(shape = shape)
                .padding(22.dp)
        ) {
            content()
        }
    }
}


