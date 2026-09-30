package com.excavplayer.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.Flow

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    favorites: List<Video> = emptyList(),
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onPlay: (Video) -> Unit = {},
    onToggleFavorite: (Video) -> Unit = {},
    onAddToPlaylist: (Long, Video) -> Unit = { _, _ -> },
    onRenameVideo: (Video, String) -> Unit = { _, _ -> },
    onDeleteVideo: (Video) -> Unit = {},
    onRemoveFromPlaylist: (Long, String) -> Unit = { _, _ -> },
    observePlaylistItems: (Long) -> Flow<List<PlaylistItem>> = { kotlinx.coroutines.flow.emptyFlow() }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header with Back Button and Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedPlaylist = null },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ExcavPalette.SurfaceCard)
                        .border(1.dp, ExcavPalette.Line, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ExcavPalette.Text,
                        modifier = Modifier.size(20.dp)
                    )
                }

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
                        onClick = { onPlay(videos.first()) },
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

            if (videos.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.VideoLibrary,
                    label = "No videos in \"${playlist.title}\""
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(videos, key = { it.id }) { video ->
                        Box {
                            ListVideoRow(
                                video = video,
                                onClick = { onPlay(video) },
                                onMoreClick = { selectedVideoForMenu = video }
                            )

                            DropdownMenu(
                                expanded = selectedVideoForMenu?.id == video.id,
                                onDismissRequest = { selectedVideoForMenu = null },
                                modifier = Modifier
                                    .background(ExcavPalette.SurfaceCard)
                                    .border(1.dp, ExcavPalette.Line, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Playlist", color = ExcavPalette.Error) },
                                    onClick = {
                                        onRemoveFromPlaylist(playlist.id, video.id)
                                        selectedVideoForMenu = null
                                    }
                                )
                                val isFav = favorites.any { it.id == video.id }
                                DropdownMenuItem(
                                    text = { Text(if (isFav) "Remove from Favorites" else "Add to Favorites", color = ExcavPalette.Text) },
                                    onClick = {
                                        onToggleFavorite(video)
                                        selectedVideoForMenu = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename", color = ExcavPalette.Text) },
                                    onClick = {
                                        renameVideoTarget = video
                                        selectedVideoForMenu = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Properties", color = ExcavPalette.Text) },
                                    onClick = {
                                        propertiesVideo = video
                                        selectedVideoForMenu = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = ExcavPalette.Error) },
                                    onClick = {
                                        deleteVideoTarget = video
                                        selectedVideoForMenu = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
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

            if (playlists.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = stringResource(R.string.empty_playlists)
                ) {
                    Button(
                        onClick = { isCreateDialogOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                        shape = ExcavShapes.Pill
                    ) {
                        Text(
                            text = stringResource(R.string.new_playlist),
                            color = ExcavPalette.Ink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Box {
                            PlaylistRow(
                                playlist = playlist,
                                onOverflow = { menuForPlaylistId = playlist.id },
                                onClick = { selectedPlaylist = playlist }
                            )

                            DropdownMenu(
                                expanded = menuForPlaylistId == playlist.id,
                                onDismissRequest = { menuForPlaylistId = null },
                                modifier = Modifier
                                    .background(ExcavPalette.SurfaceCard)
                                    .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename), color = ExcavPalette.Text) },
                                    onClick = {
                                        renamePlaylist = playlist
                                        menuForPlaylistId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = ExcavPalette.Error) },
                                    onClick = {
                                        onDelete(playlist.id)
                                        menuForPlaylistId = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
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
                    unfocusedTextColor = ExcavPalette.Text
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                shape = ExcavShapes.Pill
            ) {
                Text(confirmButtonLabel, color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted)
            }
        },
        containerColor = ExcavPalette.SurfaceCard,
        shape = ExcavShapes.Card
    )
}
