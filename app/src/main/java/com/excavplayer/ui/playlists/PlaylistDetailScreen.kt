package com.excavplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.AsyncThumbnail
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.formatDuration
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.FavoriteRed
import com.excavplayer.ui.theme.PillShape
import com.excavplayer.ui.theme.SurfaceDark
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.ThumbnailShape

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistTitle: String,
    viewModel: PlaylistsViewModel,
    onBackClick: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemsFlow = remember(playlistId) { viewModel.observePlaylistItems(playlistId) }
    val items by itemsFlow.collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        ExcavTopBar(
            title = playlistTitle,
            onBackClick = onBackClick
        )

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.PlayArrow,
                title = "Playlist is Empty",
                message = "Add videos to this playlist from any video menu."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${items.size} videos",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.playPlaylist(items, 0)
                                    items.firstOrNull()?.video?.let { onPlayVideo(it) }
                                },
                                shape = PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanAccent,
                                    contentColor = BackgroundDark
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    val video = item.video

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ThumbnailShape)
                            .background(SurfaceDark)
                            .clickable {
                                if (video != null) {
                                    viewModel.playPlaylist(items, index)
                                    onPlayVideo(video)
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.width(26.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 68.dp, height = 48.dp)
                                .clip(ThumbnailShape)
                        ) {
                            if (video != null) {
                                AsyncThumbnail(uriString = video.uri, modifier = Modifier.matchParentSize())
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video?.displayName?.substringBeforeLast(".") ?: "Video ${item.videoId}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = if (video != null) formatDuration(video.durationMs) else "",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.removePlaylistItem(playlistId, item.videoId) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = FavoriteRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
