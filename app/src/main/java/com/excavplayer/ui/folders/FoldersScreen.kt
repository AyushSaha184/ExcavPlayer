package com.excavplayer.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.R
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes

@Composable
fun FoldersScreen(
    folders: List<Folder>,
    selectedFolder: Folder?,
    folderVideos: List<Video>,
    playlists: List<Playlist>,
    favorites: List<Video>,
    onSelectFolder: (Folder?) -> Unit,
    onPlay: (Video) -> Unit,
    onToggleFavorite: (Video) -> Unit,
    onAddToPlaylist: (Long, Video) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenameVideo: (Video, String) -> Unit,
    onDeleteVideo: (Video) -> Unit
) {
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }

    // Auto-select the Downloads folder by default on first launch if no folder is chosen
    var initialFolderChecked by remember { mutableStateOf(false) }
    LaunchedEffect(folders) {
        if (!initialFolderChecked && selectedFolder == null && folders.isNotEmpty()) {
            val downloadFolder = folders.find { it.name.equals("Download", ignoreCase = true) || it.name.equals("Downloads", ignoreCase = true) }
            if (downloadFolder != null) {
                onSelectFolder(downloadFolder)
            }
            initialFolderChecked = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Breadcrumb and Navigation Bar
        if (selectedFolder != null) {
            BreadcrumbBar(
                currentPath = selectedFolder.path,
                onNavigateToPath = { path ->
                    val match = folders.find { it.path == path }
                    if (match != null) {
                        onSelectFolder(match)
                    } else if (path.isEmpty()) {
                        onSelectFolder(null)
                    }
                },
                onBack = { onSelectFolder(null) }
            )
        }

        // Horizontal Directory Navigation Chips under BreadcrumbBar
        if (folders.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All Folders" Chip
                val isAllSelected = selectedFolder == null
                Surface(
                    modifier = Modifier
                        .clip(ExcavShapes.Pill)
                        .border(
                            width = 1.dp,
                            color = if (isAllSelected) ExcavPalette.BlueGlow else ExcavPalette.Line,
                            shape = ExcavShapes.Pill
                        )
                        .clickable { onSelectFolder(null) },
                    color = if (isAllSelected) ExcavPalette.Blue.copy(alpha = 0.18f) else ExcavPalette.SurfaceCard
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isAllSelected) ExcavPalette.Blue else ExcavPalette.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "All Folders",
                            color = if (isAllSelected) ExcavPalette.Blue else ExcavPalette.Text,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                // Individual Folder Chips
                folders.forEach { folder ->
                    val isSelected = selectedFolder?.path == folder.path
                    Surface(
                        modifier = Modifier
                            .clip(ExcavShapes.Pill)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ExcavPalette.BlueGlow else ExcavPalette.Line,
                                shape = ExcavShapes.Pill
                            )
                            .clickable { onSelectFolder(folder) },
                        color = if (isSelected) ExcavPalette.Blue.copy(alpha = 0.18f) else ExcavPalette.SurfaceCard
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = folder.name,
                                color = if (isSelected) ExcavPalette.Blue else ExcavPalette.Text,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "(${folder.videoCount})",
                                color = if (isSelected) ExcavPalette.Blue else ExcavPalette.TextMuted,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        }

        if (selectedFolder != null) {
            // Folder Detail View in List format
            if (folderVideos.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.VideoLibrary,
                    label = "No videos in ${selectedFolder.name}"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folderVideos, key = { it.id }) { video ->
                        Box {
                            ListVideoRow(
                                video = video,
                                onClick = { onPlay(video) },
                                onMoreClick = { selectedVideoForMenu = video }
                            )

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
                        }
                    }
                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        } else {
            // Folders List View
            if (folders.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FolderOff,
                    label = stringResource(R.string.empty_folders)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folders, key = { it.path }) { folder ->
                        FolderRow(folder = folder, onClick = { onSelectFolder(folder) })
                    }
                    item {
                        Spacer(Modifier.height(80.dp))
                    }
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
            onSelectPlaylist = { playlistId -> onAddToPlaylist(playlistId, video) },
            onCreatePlaylist = { title -> onCreatePlaylist(title) },
            onDismiss = { playlistVideoTarget = null }
        )
    }
}
