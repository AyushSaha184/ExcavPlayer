package com.excavplayer.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.excavplayer.ui.components.GlassmorphicBackButton
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.R
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.Flow

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    favorites: List<Video> = emptyList(),
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onPlay: (Video, List<Video>) -> Unit = { _, _ -> },
    onToggleFavorite: (Video) -> Unit = {},
    onAddToPlaylist: (Long, Video) -> Unit = { _, _ -> },
    onRenameVideo: (Video, String) -> Unit = { _, _ -> },
    onDeleteVideo: (Video) -> Unit = {},
    onRemoveFromPlaylist: (Long, String) -> Unit = { _, _ -> },
    observePlaylistItems: (Long) -> Flow<List<PlaylistItem>> = { kotlinx.coroutines.flow.emptyFlow() },
    onSearch: () -> Unit = {},
    onRefresh: (() -> Unit)? = null
) {
    var isCreateDialogOpen by rememberSaveable { mutableStateOf(false) }
    var menuForPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamePlaylist by rememberSaveable { mutableStateOf<Playlist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }

    BackHandler(enabled = selectedPlaylist != null) {
        selectedPlaylist = null
    }

    if (selectedPlaylist != null) {
        val playlist = selectedPlaylist!!
        val playlistItems by observePlaylistItems(playlist.id).collectAsState(initial = emptyList())
        val videos = remember(playlistItems) { playlistItems.mapNotNull { it.video } }

        val detailListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val isDetailScrolled by remember {
            derivedStateOf {
                detailListState.firstVisibleItemIndex > 0 || detailListState.firstVisibleItemScrollOffset > 10
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val hazeState = LocalHazeState.current
            LazyColumn(
                state = detailListState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(videos, key = { it.id }) { video ->
                    ListVideoRow(
                        video = video,
                        onClick = { onPlay(video, videos) },
                        onMoreClick = { selectedVideoForMenu = video },
                        dropdownMenu = {
                                val menuShape = RoundedCornerShape(14.dp)
                                DropdownMenu(
                                    expanded = selectedVideoForMenu?.id == video.id,
                                    onDismissRequest = { selectedVideoForMenu = null },
                                    shape = menuShape,
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = null,
                                    modifier = Modifier.darkUltraThinBlur(
                                        shape = menuShape,
                                        backgroundColor = Color(0xF2101216),
                                        strokeColor = Color.White.copy(alpha = 0.16f)
                                    )
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from Playlist", color = ExcavPalette.Error) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ExcavPalette.Error) },
                                        onClick = {
                                            onRemoveFromPlaylist(playlist.id, video.id)
                                            selectedVideoForMenu = null
                                        }
                                    )
                                    val isFav = favorites.any { it.id == video.id }
                                    DropdownMenuItem(
                                        text = { Text(if (isFav) "Remove from Favorites" else "Add to Favorites", color = ExcavPalette.Text) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = null,
                                                tint = ExcavPalette.Text
                                            )
                                        },
                                        onClick = {
                                            onToggleFavorite(video)
                                            selectedVideoForMenu = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename", color = ExcavPalette.Text) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ExcavPalette.Text) },
                                        onClick = {
                                            renameVideoTarget = video
                                            selectedVideoForMenu = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Properties", color = ExcavPalette.Text) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = ExcavPalette.Text) },
                                        onClick = {
                                            propertiesVideo = video
                                            selectedVideoForMenu = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = ExcavPalette.Error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ExcavPalette.Error) },
                                        onClick = {
                                            deleteVideoTarget = video
                                            selectedVideoForMenu = null
                                        }
                                    )
                                }
                            }
                        )
                    }
                }

            // Floating Header with Back Button and Title
            ProgressiveHeaderContainer(
                modifier = Modifier.align(Alignment.TopCenter),
                isScrolled = isDetailScrolled,
                fadeHeight = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassmorphicBackButton(
                        onClick = { selectedPlaylist = null },
                        size = 38.dp
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            color = ExcavPalette.Text,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${videos.size} ${if (videos.size == 1) "video" else "videos"}",
                            color = ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (videos.isNotEmpty()) {
                        Button(
                            onClick = { onPlay(videos.first(), videos) },
                            colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                            shape = ExcavShapes.Pill,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = ExcavPalette.Ink,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Play", color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else {
        val rootGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val isRootScrolled by remember {
            derivedStateOf {
                rootGridState.firstVisibleItemIndex > 0 || rootGridState.firstVisibleItemScrollOffset > 10
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val hazeState = LocalHazeState.current
            LazyVerticalGrid(
                state = rootGridState,
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(stringResource(R.string.playlists)) {
                        Button(
                            onClick = { isCreateDialogOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                            shape = ExcavShapes.Pill,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_add_playlist),
                                tint = ExcavPalette.Ink,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "New",
                                color = ExcavPalette.Ink,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    gridItems(playlists, key = { "playlist_${it.id}" }) { playlist ->
                        val playlistItems by observePlaylistItems(playlist.id).collectAsState(initial = emptyList())
                        val videos = remember(playlistItems) { playlistItems.mapNotNull { item -> item.video } }

                        PlaylistGridCard(
                            playlist = playlist,
                            videos = videos,
                            onOverflow = { menuForPlaylistId = playlist.id },
                            onClick = { selectedPlaylist = playlist },
                            dropdownMenu = {
                                val menuShape = RoundedCornerShape(14.dp)
                                DropdownMenu(
                                    expanded = menuForPlaylistId == playlist.id,
                                    onDismissRequest = { menuForPlaylistId = null },
                                    shape = menuShape,
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = null,
                                    modifier = Modifier.darkUltraThinBlur(
                                        shape = menuShape,
                                        backgroundColor = Color(0xF2101216),
                                        strokeColor = Color.White.copy(alpha = 0.16f)
                                    )
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.rename),
                                                color = ExcavPalette.Text,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        onClick = {
                                            renamePlaylist = playlist
                                            menuForPlaylistId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.delete),
                                                color = ExcavPalette.Error,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        onClick = {
                                            onDelete(playlist.id)
                                            menuForPlaylistId = null
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            BrandHeader(
                onSearch = onSearch,
                onRefresh = onRefresh,
                isScrolled = isRootScrolled,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (isCreateDialogOpen) {
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            confirmButtonLabel = stringResource(R.string.create),
            onConfirm = {
                onCreate(it)
                isCreateDialogOpen = false
            },
            onDismiss = { isCreateDialogOpen = false }
        )
    }

    renamePlaylist?.let { playlist ->
        PlaylistNameDialog(
            title = stringResource(R.string.rename),
            confirmButtonLabel = stringResource(R.string.rename),
            initialValue = playlist.title,
            onConfirm = {
                onRename(playlist.id, it)
                renamePlaylist = null
            },
            onDismiss = { renamePlaylist = null }
        )
    }

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
            onSelectPlaylist = { playlistId -> onAddToPlaylist(playlistId, video) },
            onCreatePlaylist = { title -> onCreate(title) },
            onDismiss = { playlistVideoTarget = null }
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    confirmButtonLabel: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(initialValue) { mutableStateOf(initialValue) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .darkUltraThinBlur(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0xE6101216),
                    strokeColor = Color.White.copy(alpha = 0.18f)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name), color = ExcavPalette.TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExcavPalette.Blue,
                        unfocusedBorderColor = ExcavPalette.Line,
                        focusedTextColor = ExcavPalette.Text,
                        unfocusedTextColor = ExcavPalette.Text,
                        focusedContainerColor = Color(0x33141822),
                        unfocusedContainerColor = Color(0x33141822)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = { onConfirm(name.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                        shape = ExcavShapes.Pill
                    ) {
                        Text(confirmButtonLabel, color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
