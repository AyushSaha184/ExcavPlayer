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

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun FoldersScreen(
    folders: List<Folder>,
    selectedFolder: Folder?,
    folderVideos: List<Video>,
    videos: List<Video> = emptyList(),
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

    fun normalizePath(raw: String?): String {
        val trimmed = raw.orEmpty().trim().trimEnd('/')
        return when {
            trimmed.isEmpty() || trimmed == "/storage/emulated/0" || trimmed == "/storage/emulated" || trimmed.equals("Internal Storage", ignoreCase = true) -> "/storage/emulated/0"
            trimmed.startsWith("/storage/emulated/0") -> trimmed
            trimmed.startsWith("/storage/") -> trimmed
            trimmed.startsWith("/") -> trimmed
            else -> "/storage/emulated/0/$trimmed"
        }
    }

    val defaultDownloadPath = remember {
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: "/storage/emulated/0/Download"
    }

    // Auto-select the Downloads folder by default on first launch if no folder is chosen
    var initialFolderChecked by remember { mutableStateOf(false) }
    LaunchedEffect(folders) {
        if (!initialFolderChecked && selectedFolder == null) {
            val downloadFolder = folders.find {
                it.name.equals("Download", ignoreCase = true) ||
                it.name.equals("Downloads", ignoreCase = true) ||
                it.path.endsWith("/Download", ignoreCase = true) ||
                it.path.endsWith("/Downloads", ignoreCase = true)
            }
            val finalPath = if (downloadFolder != null) normalizePath(downloadFolder.path) else defaultDownloadPath
            val finalFolder = Folder(
                name = "Download",
                path = finalPath,
                videoCount = downloadFolder?.videoCount ?: 0,
                totalSizeBytes = downloadFolder?.totalSizeBytes ?: 0L
            )
            onSelectFolder(finalFolder)
            initialFolderChecked = true
        }
    }

    // Active directory path normalized
    val normCurrent = remember(selectedFolder) {
        normalizePath(selectedFolder?.path)
    }

    // Subfolders inside current directory: scan full device directories + database video stats
    val subfolders = remember(folders, normCurrent) {
        val childDirs = mutableMapOf<String, Pair<Int, Long>>() // childPath -> (videoCount, totalBytes)

        // 1. Scan real directories from device filesystem for current directory
        try {
            val dir = java.io.File(normCurrent)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                    ?.forEach { f ->
                        childDirs[f.absolutePath] = Pair(0, 0L)
                    }
            }
        } catch (_: Exception) {}

        // 2. Aggregate stats and discovered folders from MediaStore database
        folders.forEach { folder ->
            val fNorm = normalizePath(folder.path)
            if (fNorm != normCurrent && fNorm.startsWith("$normCurrent/")) {
                val relative = fNorm.removePrefix("$normCurrent/").trimStart('/')
                val directChildName = relative.substringBefore('/')
                val directChildPath = "$normCurrent/$directChildName"
                val existing = childDirs[directChildPath] ?: Pair(0, 0L)
                childDirs[directChildPath] = Pair(
                    existing.first + folder.videoCount,
                    existing.second + folder.totalSizeBytes
                )
            } else if (normCurrent == "/storage/emulated/0" && !fNorm.startsWith("/storage/emulated/0")) {
                val segments = fNorm.split('/').filter { it.isNotEmpty() }
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
        }.sortedWith(compareByDescending<Folder> { it.videoCount > 0 }.thenBy { it.name.lowercase() })
    }

    val canGoBack = normCurrent != "/storage/emulated/0"

    val selectPath: (String) -> Unit = { rawTarget ->
        val targetPath = normalizePath(rawTarget)
        if (targetPath == "/storage/emulated/0") {
            onSelectFolder(Folder("Internal Storage", "/storage/emulated/0", 0, 0L))
        } else {
            val match = folders.find {
                normalizePath(it.path) == targetPath || it.path == targetPath
            } ?: Folder(
                name = targetPath.substringAfterLast('/'),
                path = targetPath,
                videoCount = 0,
                totalSizeBytes = 0L
            )
            onSelectFolder(match.copy(path = targetPath))
        }
    }

    val navigateUp: () -> Unit = {
        if (canGoBack) {
            val parentPath = normCurrent.substringBeforeLast('/', "")
            selectPath(if (parentPath.isEmpty() || parentPath == "/storage/emulated") "/storage/emulated/0" else parentPath)
        }
    }

    androidx.activity.compose.BackHandler(enabled = canGoBack) {
        navigateUp()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val isFolderEmpty = subfolders.isEmpty() && folderVideos.isEmpty()

        if (isFolderEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.FolderOff,
                    label = if (normCurrent == "/storage/emulated/0") "No media or folders in Internal Storage" else "No media or subfolders in ${selectedFolder?.name ?: "folder"}"
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section 1: Child Subfolders (Home-style folder grid cards)
                if (subfolders.isNotEmpty()) {
                    gridItems(subfolders, key = { "folder_${it.path}" }) { folder ->
                        val subfolderVids = remember(folder.path, videos) {
                            videos.filter {
                                it.folderPath == folder.path ||
                                it.folderPath.startsWith("${folder.path}/") ||
                                (it.folderName.isNotEmpty() && it.folderName.equals(folder.name, ignoreCase = true))
                            }
                        }
                        FolderGridCard(
                            folder = folder,
                            videos = subfolderVids,
                            onClick = { onSelectFolder(folder) }
                        )
                    }
                }

                // Section 2: Videos directly in this directory (Spanning full line width as list rows)
                if (folderVideos.isNotEmpty()) {
                    gridItems(folderVideos, key = { "video_${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { video ->
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
                            }
                        )
                    }
                }
            }
        }

        // Top Floating Breadcrumb Bar
        BreadcrumbBar(
            currentPath = normCurrent,
            onNavigateToPath = selectPath,
            onBack = navigateUp,
            modifier = Modifier.align(Alignment.TopCenter)
        )
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
