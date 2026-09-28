package com.excavplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.excavplayer.domain.model.Playlist
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ExcavIconButton
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.PlaylistCard
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.excavBackground

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onFavoritesClick: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val favoritesCount by viewModel.favoritesCount.collectAsState()
    val favoritesThumbnail by viewModel.favoritesThumbnail.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .excavBackground()
    ) {
        ExcavTopBar(
            title = "Playlists",
            actions = {
                ExcavIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick = onNavigateToSearch,
                    touchTargetSize = 38.dp,
                    iconSize = 22.dp,
                    tint = TextPrimary
                )

                Spacer(modifier = Modifier.size(6.dp))

                // Plus (+) circular action button (40dp with high contrast)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyanAccent),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Playlist",
                            tint = Color(0xFF09111A),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // First item is always "My Favorites" virtual playlist as seen in mockup screen 3
            item {
                val favPlaylist = Playlist(
                    id = -1L,
                    title = "My Favorites",
                    itemCount = favoritesCount
                )
                PlaylistCard(
                    playlist = favPlaylist,
                    previewThumbnailUri = favoritesThumbnail,
                    isFavoritesVirtualPlaylist = true,
                    onClick = onFavoritesClick,
                    onRenameClick = {},
                    onDeleteClick = {},
                    onPlayAllClick = onFavoritesClick
                )
            }

            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    previewThumbnailUri = null,
                    onClick = { onPlaylistClick(playlist) },
                    onRenameClick = { playlistToRename = playlist },
                    onDeleteClick = { viewModel.deletePlaylist(playlist.id) },
                    onPlayAllClick = {
                        // Play all items
                        viewModel.observePlaylistItems(playlist.id)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showCreateDialog) {
            CreatePlaylistDialog(
                titleLabel = "New Playlist",
                confirmButtonLabel = "Create",
                onConfirm = { viewModel.createPlaylist(it) },
                onDismiss = { showCreateDialog = false }
            )
        }

        playlistToRename?.let { target ->
            CreatePlaylistDialog(
                initialTitle = target.title,
                titleLabel = "Rename Playlist",
                confirmButtonLabel = "Save",
                onConfirm = { viewModel.renamePlaylist(target.id, it) },
                onDismiss = { playlistToRename = null }
            )
        }
    }
}
