package com.excavplayer.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.VideoCard
import com.excavplayer.ui.components.VideoDetailsDialog
import com.excavplayer.ui.components.VideoOptionsSheet
import com.excavplayer.ui.components.formatFileSize
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.TextPrimary
import java.util.Calendar

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToSearch: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsState()
    var selectedVideoForOptions by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForDetails by remember { mutableStateOf<Video?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        ExcavTopBar(
            title = "Favorites",
            actions = {
                IconButton(onClick = onNavigateToSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = { /* Grid view toggle */ }) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "View Options",
                        tint = TextPrimary
                    )
                }
            }
        )

        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Favorite,
                title = "No Favorites Yet",
                message = "Tap the heart icon or menu on any video to add it to your favorites."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favorites, key = { it.id }) { video ->
                    val year = if (video.dateAddedSeconds > 0) {
                        Calendar.getInstance().apply {
                            timeInMillis = video.dateAddedSeconds * 1000L
                        }.get(Calendar.YEAR)
                    } else 2024
                    val sizeFormatted = formatFileSize(video.sizeBytes)
                    val metadataSubtitle = "$year · $sizeFormatted"

                    VideoCard(
                        video = video,
                        showHeartBadge = true,
                        subtitleText = metadataSubtitle,
                        onClick = {
                            viewModel.playFavorite(video)
                            onPlayVideo(video)
                        },
                        onOptionsClick = { selectedVideoForOptions = video }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        selectedVideoForOptions?.let { video ->
            VideoOptionsSheet(
                video = video,
                isFavorite = true,
                onDismiss = { selectedVideoForOptions = null },
                onPlay = {
                    viewModel.playFavorite(video)
                    onPlayVideo(video)
                },
                onPlayNext = { /* play next */ },
                onAddToPlaylist = { /* add */ },
                onToggleFavorite = { viewModel.toggleFavorite(video.id) },
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
