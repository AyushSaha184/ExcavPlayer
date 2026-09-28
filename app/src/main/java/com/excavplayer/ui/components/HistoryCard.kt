package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.model.WatchHistoryEntry
import com.excavplayer.ui.theme.CompletedGreen
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.FavoriteRed
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.ThumbnailShape

@Composable
fun HistoryCard(
    entry: WatchHistoryEntry,
    video: Video?,
    onClick: () -> Unit,
    onPlayFromStart: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with bottom progress bar
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 58.dp)
                .clip(ThumbnailShape)
        ) {
            if (video != null) {
                AsyncThumbnail(
                    uriString = video.uri,
                    durationText = video.formattedDuration,
                    progressPercentage = if (entry.isCompleted) 1f else entry.completionPercentage,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(ThumbnailShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            val title = video?.displayName?.substringBeforeLast(".") ?: "Video ${entry.videoId}"
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (entry.isCompleted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Completed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = CompletedGreen
                    )
                    val durationStr = if (video != null && video.durationMs > 0) {
                        " · " + formatDuration(video.durationMs)
                    } else if (entry.totalWatchDurationMs > 0) {
                        " · " + formatDuration(entry.totalWatchDurationMs)
                    } else ""
                    Text(
                        text = durationStr,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            } else {
                val currentStr = formatDuration(entry.lastPositionMs)
                val totalStr = if (video != null && video.durationMs > 0) formatDuration(video.durationMs) else ""
                val percent = (entry.completionPercentage * 100).toInt()
                val subtitle = if (totalStr.isNotEmpty()) "$currentStr / $totalStr · $percent%" else "$currentStr · $percent%"

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceDarkElevated)
            ) {
                DropdownMenuItem(
                    text = { Text("Resume Playback", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Play from Beginning", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onPlayFromStart()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove from History", color = FavoriteRed) },
                    onClick = {
                        showMenu = false
                        onRemoveFromHistory()
                    }
                )
            }
        }
    }
}
