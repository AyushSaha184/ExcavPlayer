package com.excavplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.excavplayer.ui.theme.ExcavPalette
import kotlin.math.roundToInt

data class InWindowMenuData(
    val anchorBounds: Rect,
    val onDismiss: () -> Unit,
    val content: @Composable ColumnScope.() -> Unit
)

@Stable
class InWindowMenuHostState {
    var menuData by mutableStateOf<InWindowMenuData?>(null)
        private set
    var isVisible by mutableStateOf(false)
        private set
    var activeKey by mutableStateOf<Any?>(null)
        private set

    fun show(key: Any, data: InWindowMenuData) {
        activeKey = key
        menuData = data
        isVisible = true
    }

    fun updateAnchor(anchor: Rect) {
        val current = menuData
        if (current != null && current.anchorBounds != anchor) {
            menuData = current.copy(anchorBounds = anchor)
        }
    }

    fun dismiss() {
        if (isVisible) {
            isVisible = false
            activeKey = null
            menuData?.onDismiss?.invoke()
        }
    }

    fun dismissIfKey(key: Any) {
        if (activeKey == key) {
            dismiss()
        }
    }
}

val LocalInWindowMenuHost = staticCompositionLocalOf<InWindowMenuHostState?> { null }

@Composable
fun InWindowMenuHost(
    host: InWindowMenuHostState,
    modifier: Modifier = Modifier
) {
    val isVisible = host.isVisible
    val menuData = host.menuData

    // Hardware / gesture back handler
    BackHandler(enabled = isVisible) {
        host.dismiss()
    }

    if (menuData != null) {
        val density = LocalDensity.current
        val menuShape = remember { RoundedCornerShape(18.dp) }
        val menuWidth = 210.dp
        val menuWidthPx = with(density) { menuWidth.toPx() }

        var measuredMenuSize by remember { mutableStateOf(IntSize.Zero) }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .zIndex(99f)
        ) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            // 1. Transparent full-screen touch interceptor to dismiss menu on tap outside
            if (isVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { host.dismiss() }
                        )
                )
            }

            // 2. Position calculation with screen & navbar bounds safety
            val anchor = menuData.anchorBounds
            val marginPx = with(density) { 12.dp.toPx() }
            val navSafeBottomPx = with(density) { 86.dp.toPx() }

            val hasValidAnchor = anchor != Rect.Zero && anchor.right > 0f && anchor.bottom > 0f
            val anchorRight = if (hasValidAnchor) anchor.right else (screenWidthPx - marginPx)
            val anchorBottom = if (hasValidAnchor) anchor.bottom else with(density) { 100.dp.toPx() }
            val anchorTop = if (hasValidAnchor) anchor.top else (anchorBottom - with(density) { 32.dp.toPx() })

            // Align right edge of menu to right edge of 3-dot button
            val rawLeftPx = anchorRight - menuWidthPx
            val clampedLeftPx = rawLeftPx.coerceIn(marginPx, (screenWidthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx))

            val currentMenuHeightPx = if (measuredMenuSize.height > 0) {
                measuredMenuSize.height.toFloat()
            } else {
                with(density) { 200.dp.toPx() }
            }

            val roomBelow = (screenHeightPx - navSafeBottomPx) - anchorBottom
            val openUpwards = hasValidAnchor && roomBelow < currentMenuHeightPx && anchorTop > currentMenuHeightPx

            val topOffsetPx = if (openUpwards) {
                (anchorTop - currentMenuHeightPx - with(density) { 6.dp.toPx() }).coerceAtLeast(marginPx)
            } else {
                (anchorBottom + with(density) { 6.dp.toPx() }).coerceIn(marginPx, (screenHeightPx - navSafeBottomPx - currentMenuHeightPx).coerceAtLeast(marginPx))
            }

            val transformOrigin = if (openUpwards) {
                TransformOrigin(pivotFractionX = 0.92f, pivotFractionY = 1.0f)
            } else {
                TransformOrigin(pivotFractionX = 0.92f, pivotFractionY = 0.0f)
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn(
                    initialScale = 0.88f,
                    transformOrigin = transformOrigin,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(130)),
                exit = scaleOut(
                    targetScale = 0.92f,
                    transformOrigin = transformOrigin,
                    animationSpec = tween(90)
                ) + fadeOut(tween(90)),
                modifier = Modifier
                    .offset { IntOffset(clampedLeftPx.roundToInt(), topOffsetPx.roundToInt()) }
                    .width(menuWidth)
                    .onSizeChanged { measuredMenuSize = it }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 12.dp, shape = menuShape, spotColor = Color.Black.copy(alpha = 0.45f))
                        .liquidGlass(shape = menuShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume clicks inside menu card
                        )
                        .padding(vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        content = menuData.content
                    )
                }
            }
        }
    }
}

/**
 * Standard item row for the in-window Liquid Glass options menu.
 */
@Composable
fun InWindowMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    contentColor: Color = if (isDestructive) ExcavPalette.Error else Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = contentColor),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp
            )
        )
    }
}

/**
 * Hairline translucent divider between menu sections.
 */
@Composable
fun InWindowMenuDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
    )
}
