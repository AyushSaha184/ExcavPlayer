package com.excavplayer.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.excavplayer.R
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import dev.chrisbanes.haze.hazeSource

@Composable
fun SearchScreen(
    query: String,
    results: List<Video>,
    playlists: List<Playlist> = emptyList(),
    favorites: List<Video> = emptyList(),
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (Video) -> Unit,
    onToggleFavorite: (Video) -> Unit = {},
    onAddToPlaylist: (Long, Video) -> Unit = { _, _ -> },
    onCreatePlaylist: (String) -> Unit = {},
    onRenameVideo: (Video, String) -> Unit = { _, _ -> },
    onDeleteVideo: (Video) -> Unit = {}
) {
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val isScrolled by remember {
            derivedStateOf {
                searchListState.firstVisibleItemIndex > 0 || searchListState.firstVisibleItemScrollOffset > 10
            }
        }

        val hazeState = LocalHazeState.current
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(
            state = searchListState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 64.dp,
                bottom = 100.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            if (results.isEmpty() && query.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            label = "No videos matching \"$query\""
                        )
                    }
                }
            } else {
                items(results, key = { it.id }) { video ->
                    ListVideoRow(
                        video = video,
                        onClick = { onPlay(video) },
                        onMoreClick = { selectedVideoForMenu = video },
                        dropdownMenu = {
                            if (selectedVideoForMenu?.id == video.id) {
                                val isFav = favorites.any { it.id == video.id }
                                VideoOptionsMenu(
                                    expanded = true,
                                    video = video,
                                    isFavorite = isFav,
                                    onDismiss = { selectedVideoForMenu = null },
                                    onToggleFavorite = { onToggleFavorite(video) },
                                    onAddToPlaylist = { playlistVideoTarget = video },
                                    onRename = { renameVideoTarget = video },
                                    onProperties = { propertiesVideo = video },
                                    onDelete = { deleteVideoTarget = video }
                                )
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        // Floating Top Glass Search Bar
        ProgressiveHeaderContainer(
            modifier = Modifier.align(Alignment.TopCenter),
            isScrolled = isScrolled,
            fadeHeight = 20.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassmorphicBackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                GlassmorphicItem(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 20,
                    blurRadius = 15
                ) {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search), color = ExcavPalette.TextMuted) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = ExcavPalette.TextMuted
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = ExcavPalette.TextMuted
                                    )
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = ExcavPalette.Text,
                            unfocusedTextColor = ExcavPalette.Text
                        )
                    )
                }
            }
        }
    }

    // Modals & Dialogs
    propertiesVideo?.let { video ->
        VideoPropertiesDialog(
            video = video,
            onDismiss = { propertiesVideo = null }
        )
    }

    renameVideoTarget?.let { video ->
        RenameVideoDialog(
            video = video,
            onRename = { onRenameVideo(video, it) },
            onDismiss = { renameVideoTarget = null }
        )
    }

    deleteVideoTarget?.let { video ->
        DeleteConfirmDialog(
            video = video,
            onDelete = { onDeleteVideo(video) },
            onDismiss = { deleteVideoTarget = null }
        )
    }

    playlistVideoTarget?.let { video ->
        AddToPlaylistDialog(
            video = video,
            playlists = playlists,
            onSelectPlaylist = { playlistId ->
                onAddToPlaylist(playlistId, video)
                playlistVideoTarget = null
            },
            onCreatePlaylist = { name ->
                onCreatePlaylist(name)
            },
            onDismiss = { playlistVideoTarget = null }
        )
    }
}
