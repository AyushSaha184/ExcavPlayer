package com.excavplayer.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ExcavIconButton
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.VideoCard
import com.excavplayer.ui.components.VideoDetailsDialog
import com.excavplayer.ui.components.VideoOptionsSheet
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.excavBackground

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()

    var selectedVideoForOptions by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForDetails by remember { mutableStateOf<Video?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .excavBackground()
    ) {
        // App Top Bar
        ExcavTopBar(
            showBrandLogo = true,
            actions = {
                ExcavIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick = onNavigateToSearch,
                    touchTargetSize = 38.dp,
                    iconSize = 22.dp,
                    tint = TextPrimary
                )
                ExcavIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    onClick = onNavigateToSettings,
                    touchTargetSize = 38.dp,
                    iconSize = 22.dp,
                    tint = TextPrimary
                )
            }
        )

        if (continueWatching.isEmpty() && allVideos.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Movie,
                title = "No Videos Found",
                message = "Scan your device or add a media folder to start watching videos in ExcavPlayer.",
                actionButtonText = "Scan Library",
                onActionClick = { viewModel.refreshLibrary() }
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Continue Watching Section
                if (continueWatching.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        com.excavplayer.ui.components.ExcavSectionHeader(
                            title = "Continue Watching",
                            fontSize = 18.sp
                        )
                    }

                    items(continueWatching, key = { "cw_${it.id}" }) { video ->
                        val progress = if (video.durationMs > 0 && (video.resumePositionMs ?: 0L) > 0L) {
                            (video.resumePositionMs!!.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else null

                        VideoCard(
                            video = video,
                            progressPercentage = progress,
                            onClick = {
                                viewModel.playVideo(video)
                                onPlayVideo(video)
                            },
                            onOptionsClick = { selectedVideoForOptions = video }
                        )
                    }
                }

                // All Videos / Library Section
                val remainingVideos = allVideos.filter { v -> continueWatching.none { it.id == v.id } }
                if (remainingVideos.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Spacer(modifier = Modifier.height(6.dp))
                        com.excavplayer.ui.components.ExcavSectionHeader(
                            title = if (continueWatching.isNotEmpty()) "All Videos" else "Recent Videos",
                            fontSize = 18.sp
                        )
                    }

                    items(remainingVideos, key = { "all_${it.id}" }) { video ->
                        VideoCard(
                            video = video,
                            onClick = {
                                viewModel.playVideo(video)
                                onPlayVideo(video)
                            },
                            onOptionsClick = { selectedVideoForOptions = video }
                        )
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Context menu options bottom sheet
        selectedVideoForOptions?.let { video ->
            VideoOptionsSheet(
                video = video,
                isFavorite = video.isFavorite,
                onDismiss = { selectedVideoForOptions = null },
                onPlay = {
                    viewModel.playVideo(video)
                    onPlayVideo(video)
                },
                onPlayNext = { viewModel.playNext(video) },
                onAddToPlaylist = { /* Add to playlist */ },
                onToggleFavorite = { viewModel.toggleFavorite(video.id) },
                onShowDetails = { selectedVideoForDetails = video },
                onDelete = { viewModel.deleteVideo(video.id) }
            )
        }

        // Details Dialog
        selectedVideoForDetails?.let { video ->
            VideoDetailsDialog(
                video = video,
                onDismiss = { selectedVideoForDetails = null }
            )
        }
    }
}
