package com.excavplayer.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.VideoCard
import com.excavplayer.ui.components.VideoDetailsDialog
import com.excavplayer.ui.components.VideoOptionsSheet
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.PillShape
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.excavBackground

@Composable
fun FolderDetailScreen(
    folderPath: String,
    folderName: String,
    viewModel: FoldersViewModel,
    onBackClick: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val videosFlow = remember(folderPath) { viewModel.observeVideosInFolder(folderPath) }
    val videos by videosFlow.collectAsState(initial = emptyList())

    var selectedVideoForOptions by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForDetails by remember { mutableStateOf<Video?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .excavBackground()
    ) {
        ExcavTopBar(
            title = folderName,
            onBackClick = onBackClick
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${videos.size} videos",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )

                    if (videos.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.playAll(videos)
                                onPlayVideo(videos.first())
                            },
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanAccent,
                                contentColor = BackgroundDark
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play All",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            items(videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    onClick = {
                        viewModel.playVideo(video, videos)
                        onPlayVideo(video)
                    },
                    onOptionsClick = { selectedVideoForOptions = video }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        selectedVideoForOptions?.let { video ->
            VideoOptionsSheet(
                video = video,
                isFavorite = video.isFavorite,
                onDismiss = { selectedVideoForOptions = null },
                onPlay = {
                    viewModel.playVideo(video, videos)
                    onPlayVideo(video)
                },
                onPlayNext = { /* queue */ },
                onAddToPlaylist = { /* playlist */ },
                onToggleFavorite = { /* favorite */ },
                onShowDetails = { selectedVideoForDetails = video },
                onDelete = { }
            )
        }

        selectedVideoForDetails?.let { video ->
            VideoDetailsDialog(
                video = video,
                onDismiss = { selectedVideoForDetails = null }
            )
        }
    }
}
