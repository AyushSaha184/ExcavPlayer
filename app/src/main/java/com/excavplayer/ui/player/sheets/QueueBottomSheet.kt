package com.excavplayer.ui.player.sheets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Video
import com.excavplayer.player.queue.QueueState
import com.excavplayer.ui.components.AsyncThumbnail
import com.excavplayer.ui.components.formatDuration
import com.excavplayer.ui.theme.BottomSheetShape
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.ThumbnailShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queueState: QueueState,
    currentVideo: Video?,
    isPlaying: Boolean,
    onVideoSelected: (Int) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = SurfaceDarkElevated,
        tonalElevation = 8.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(SurfaceBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            if (queueState.items.isEmpty()) {
                Text(
                    text = "Queue is empty",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    itemsIndexed(queueState.items) { index, item ->
                        val isCurrent = index == queueState.currentIndex || item.id == currentVideo?.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ThumbnailShape)
                                .background(if (isCurrent) CyanAccent.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable {
                                    onVideoSelected(index)
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Queue Index
                            Text(
                                text = "${index + 1}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) CyanAccent else TextSecondary,
                                modifier = Modifier.width(26.dp)
                            )

                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(width = 64.dp, height = 44.dp)
                                    .clip(ThumbnailShape)
                                    .then(
                                        if (isCurrent) Modifier.border(1.5.dp, CyanAccent, ThumbnailShape)
                                        else Modifier
                                    )
                            ) {
                                AsyncThumbnail(
                                    uriString = item.uri,
                                    modifier = Modifier.matchParentSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName.substringBeforeLast("."),
                                    fontSize = 15.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                if (isCurrent) {
                                    Text(
                                        text = "Now Playing",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CyanAccent
                                    )
                                } else {
                                    Text(
                                        text = formatDuration(item.durationMs),
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Trailing Audio Waveform or Reorder Handle
                            if (isCurrent) {
                                AnimatedWaveform(isPlaying = isPlaying)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Reorder",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AnimatedWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")

    val h1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar4"
    )

    Row(
        modifier = modifier
            .width(28.dp)
            .height(20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        val multiplier = if (isPlaying) 1f else 0.3f
        Box(modifier = Modifier.width(3.dp).height((18 * h1 * multiplier).dp.coerceAtLeast(3.dp)).background(CyanAccent, CircleShape))
        Box(modifier = Modifier.width(3.dp).height((18 * h2 * multiplier).dp.coerceAtLeast(3.dp)).background(CyanAccent, CircleShape))
        Box(modifier = Modifier.width(3.dp).height((18 * h3 * multiplier).dp.coerceAtLeast(3.dp)).background(CyanAccent, CircleShape))
        Box(modifier = Modifier.width(3.dp).height((18 * h4 * multiplier).dp.coerceAtLeast(3.dp)).background(CyanAccent, CircleShape))
    }
}
