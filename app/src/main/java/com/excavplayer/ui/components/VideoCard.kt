package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.theme.BadgeBackground
import com.excavplayer.ui.theme.CardShape
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.FavoriteRed
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDark
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.ThumbnailShape

@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleText: String? = null,
    showHeartBadge: Boolean = false,
    progressPercentage: Float? = null,
    onOptionsClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncThumbnail(
                uriString = video.uri,
                durationText = video.formattedDuration,
                progressPercentage = progressPercentage,
                modifier = Modifier.matchParentSize()
            )

            // Favorite Badge (26dp black translucent circle, 8dp from edge)
            if (showHeartBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(BadgeBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = FavoriteRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName.substringBeforeLast("."),
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                val subtitle = subtitleText ?: formatVideoSubtitle(video, progressPercentage)
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onOptionsClick != null) {
                ExcavIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    onClick = onOptionsClick,
                    touchTargetSize = 30.dp,
                    iconSize = 18.dp,
                    tint = TextSecondary
                )
            }
        }
    }
}

fun formatVideoSubtitle(video: Video, progressPercentage: Float?): String {
    return if (progressPercentage != null && progressPercentage > 0f) {
        val currentMs = ((video.resumePositionMs ?: 0L)).coerceAtLeast(0L)
        val currentStr = formatDuration(currentMs)
        val totalStr = formatDuration(video.durationMs)
        val percent = (progressPercentage * 100).toInt()
        "$currentStr / $totalStr · $percent%"
    } else {
        val sizeFormatted = formatFileSize(video.sizeBytes)
        val durationStr = formatDuration(video.durationMs)
        "$durationStr · $sizeFormatted"
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0m"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}
