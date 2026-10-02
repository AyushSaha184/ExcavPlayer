package com.excavplayer.ui.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*

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
    onDeleteVideo: (Video) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    onSearch: () -> Unit = {},
    onRefresh: (() -> Unit)? = null
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

    val normCurrent = remember(selectedFolder) {
        if (selectedFolder != null) normalizePath(selectedFolder.path) else "/storage/emulated/0"
    }

    // Subfolders inside currently selected folder
    val subfolders = remember(folders, selectedFolder, normCurrent) {
        if (selectedFolder == null) {
            emptyList()
        } else {
            val childDirs = mutableMapOf<String, Pair<Int, Long>>() // childPath -> (videoCount, totalBytes)

            // 1. Scan filesystem for physical subfolders
            try {
                val dir = java.io.File(normCurrent)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                        ?.forEach { f ->
                            childDirs[f.absolutePath] = Pair(0, 0L)
                        }
                }
            } catch (_: Exception) {}

            // 2. Aggregate stats and subfolders from database
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
    }

    val canGoBack = selectedFolder != null

    val selectPath: (String) -> Unit = { rawTarget ->
        val targetPath = normalizePath(rawTarget)
        if (targetPath == "/storage/emulated/0") {
            onSelectFolder(null)
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
            if (parentPath.isEmpty() || parentPath == "/storage/emulated" || parentPath == "/storage/emulated/0") {
                onSelectFolder(null)
            } else {
                selectPath(parentPath)
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        navigateUp()
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedFolder == null) {
            // Root View: Display all Library Folders with thumbnail and pill badges
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.FolderOff,
                        label = "No video folders found on device"
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(folders, key = { "root_folder_${it.path}" }) { folder ->
                        val folderVids = remember(folder.path, folder.name, videos) {
                            val normP = normalizePath(folder.path)
                            videos.filter { v ->
                                val vNorm = normalizePath(v.folderPath)
                                vNorm == normP ||
                                vNorm.startsWith("$normP/") ||
                                (v.folderName.isNotEmpty() && v.folderName.equals(folder.name, ignoreCase = true))
                            }
                        }
                        FolderCard(
                            folder = folder,
                            videos = folderVids,
                            onClick = { onSelectFolder(folder) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }

            BrandHeader(
                onSearch = onSearch,
                onRefresh = onRefresh,
                isScrolled = isScrolled,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            // Subfolder / Selected Folder View
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
                        label = "No videos or subfolders in ${selectedFolder.name}"
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Section 1: Child Subfolders (Home-style folder grid cards)
                    if (subfolders.isNotEmpty()) {
                        gridItems(subfolders, key = { "folder_${it.path}" }) { folder ->
                            val subfolderVids = remember(folder.path, folder.name, videos) {
                                val normP = normalizePath(folder.path)
                                videos.filter { v ->
                                    val vNorm = normalizePath(v.folderPath)
                                    vNorm == normP ||
                                    vNorm.startsWith("$normP/") ||
                                    (v.folderName.isNotEmpty() && v.folderName.equals(folder.name, ignoreCase = true))
                                }
                            }
                            FolderCard(
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

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }

            // Top Floating Breadcrumb Bar
            BreadcrumbBar(
                currentPath = normCurrent,
                onNavigateToPath = selectPath,
                onBack = navigateUp,
                isScrolled = isScrolled,
                modifier = Modifier.align(Alignment.TopCenter)
            )
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
