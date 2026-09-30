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
            val downloadFolder = folders.find {
                it.name.equals("Download", ignoreCase = true) ||
                it.name.equals("Downloads", ignoreCase = true) ||
                it.path.endsWith("/Download", ignoreCase = true) ||
                it.path.endsWith("/Downloads", ignoreCase = true)
            }
            if (downloadFolder != null) {
                onSelectFolder(downloadFolder)
            } else {
                onSelectFolder(Folder("Internal Storage", "/storage/emulated/0", 0, 0))
            }
            initialFolderChecked = true
        }
    }

    // Active directory path
    val currentPath = selectedFolder?.path.orEmpty()
    val normCurrent = if (currentPath.isEmpty()) "/storage/emulated/0" else currentPath.trimEnd('/')

    // Subfolders inside current directory computed from device paths
    val subfolders = remember(folders, normCurrent) {
        val childDirs = mutableMapOf<String, Pair<Int, Long>>() // childPath -> (videoCount, totalBytes)
        folders.forEach { folder ->
            val fPath = folder.path.trimEnd('/')
            if (fPath != normCurrent && fPath.startsWith("$normCurrent/")) {
                val relative = fPath.removePrefix("$normCurrent/").trimStart('/')
                val directChildName = relative.substringBefore('/')
                val directChildPath = "$normCurrent/$directChildName"
                val existing = childDirs[directChildPath] ?: Pair(0, 0L)
                childDirs[directChildPath] = Pair(
                    existing.first + folder.videoCount,
                    existing.second + folder.totalSizeBytes
                )
            } else if (normCurrent == "/storage/emulated/0" && !fPath.startsWith("/storage/emulated/0")) {
                val segments = fPath.split('/').filter { it.isNotEmpty() }
                if (segments.size >= 2) {
                    val rootSegment = "/" + segments.take(2).joinToString("/")
                    val existing = childDirs[rootSegment] ?: Pair(0, 0L)
                    childDirs[rootSegment] = Pair(
                        existing.first + folder.videoCount,
                        existing.second + folder.totalSizeBytes
                    )
                }
            }
        }

        childDirs.map { (path, stats) ->
            val name = path.substringAfterLast('/')
            Folder(
                name = name,
                path = path,
                videoCount = stats.first,
                totalSizeBytes = stats.second
            )
        }.sortedBy { it.name.lowercase() }
    }

    val canGoBack = selectedFolder != null && selectedFolder.path.isNotEmpty() && selectedFolder.path != "/storage/emulated/0"

    androidx.activity.compose.BackHandler(enabled = canGoBack) {
        if (selectedFolder != null) {
            val parentPath = selectedFolder.path.substringBeforeLast('/', "")
            if (parentPath.isEmpty() || parentPath == "/storage/emulated/0") {
                onSelectFolder(Folder("Internal Storage", "/storage/emulated/0", 0, 0))
            } else {
                val parentFolder = folders.find { it.path == parentPath }
                    ?: Folder(name = parentPath.substringAfterLast('/'), path = parentPath, videoCount = 0, totalSizeBytes = 0)
                onSelectFolder(parentFolder)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Breadcrumb and Navigation Bar (Right side of back button)
        BreadcrumbBar(
            currentPath = if (currentPath.isEmpty()) "/storage/emulated/0" else currentPath,
            onNavigateToPath = { path ->
                if (path.isEmpty() || path == "/storage/emulated/0") {
                    onSelectFolder(Folder("Internal Storage", "/storage/emulated/0", 0, 0))
                } else {
                    val match = folders.find { it.path == path }
                        ?: Folder(name = path.substringAfterLast('/'), path = path, videoCount = 0, totalSizeBytes = 0)
                    onSelectFolder(match)
                }
            },
            onBack = {
                if (canGoBack) {
                    val parentPath = selectedFolder?.path?.substringBeforeLast('/', "").orEmpty()
                    if (parentPath.isEmpty() || parentPath == "/storage/emulated/0") {
                        onSelectFolder(Folder("Internal Storage", "/storage/emulated/0", 0, 0))
                    } else {
                        val parentFolder = folders.find { it.path == parentPath }
                            ?: Folder(name = parentPath.substringAfterLast('/'), path = parentPath, videoCount = 0, totalSizeBytes = 0)
                        onSelectFolder(parentFolder)
                    }
                }
            }
        )

        val isFolderEmpty = subfolders.isEmpty() && folderVideos.isEmpty() && (selectedFolder != null || folders.isEmpty())

        if (isFolderEmpty) {
            EmptyState(
                icon = Icons.Default.FolderOff,
                label = if (selectedFolder != null) "No media or subfolders in ${selectedFolder.name}" else stringResource(R.string.empty_folders)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section 1: Child Subfolders (if any)
                if (subfolders.isNotEmpty()) {
                    item {
                        Text(
                            text = if (selectedFolder == null) "Folders" else "Subfolders (${subfolders.size})",
                            color = ExcavPalette.TextSecondary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(subfolders, key = { "folder_${it.path}" }) { folder ->
                        FolderRow(
                            folder = folder,
                            onClick = { onSelectFolder(folder) }
                        )
                    }
                }

                // Section 2: Videos directly in this directory
                if (selectedFolder != null && folderVideos.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Videos (${folderVideos.size})",
                            color = ExcavPalette.TextSecondary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(folderVideos, key = { "video_${it.id}" }) { video ->
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
                }

                item {
                    Spacer(Modifier.height(80.dp))
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
