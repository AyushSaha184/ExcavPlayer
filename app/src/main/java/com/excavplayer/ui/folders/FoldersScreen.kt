package com.excavplayer.ui.folders

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.NaturalVideoComparator
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import androidx.compose.ui.res.stringResource
import com.excavplayer.R
import dev.chrisbanes.haze.hazeSource

@Composable
fun FoldersScreen(
    folders: List<Folder>,
    selectedFolder: Folder?,
    folderVideos: List<Video>,
    videos: List<Video> = emptyList(),
    folderVideosMap: Map<String, List<Video>> = emptyMap(),
    playlists: List<Playlist>,
    favorites: List<Video>,
    favoriteFolders: List<Folder> = emptyList(),
    onSelectFolder: (Folder?) -> Unit,
    onPlay: (Video, List<Video>) -> Unit,
    onToggleFavorite: (Video) -> Unit,
    onToggleFavoriteFolder: (Folder) -> Unit = {},
    onSetVideosFavorite: (List<Video>, Boolean) -> Unit = { _, _ -> },
    onSetFoldersFavorite: (List<Folder>, Boolean) -> Unit = { _, _ -> },
    onAddToPlaylist: (Long, Video) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenameVideo: (Video, String) -> Unit,
    onDeleteVideo: (Video) -> Unit,
    onDeleteVideos: (List<Video>) -> Unit = {},
    onDeleteFolders: (List<Folder>) -> Unit = {},
    onRenameFolder: (Folder, String) -> Unit = { _, _ -> },
    gridState: LazyGridState = rememberLazyGridState(),
    onSearch: () -> Unit = {},
    onRefresh: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = if (selectedFolder == null) selectedFolderPaths.isNotEmpty() else selectedVideoIds.isNotEmpty()

    var isOverflowMenuOpen by remember { mutableStateOf(false) }

    // Dialog targets
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }
    var propertiesMultiVideos by remember { mutableStateOf<List<Video>?>(null) }
    var propertiesFolder by remember { mutableStateOf<Folder?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var renameFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var batchDeleteTargetVideos by remember { mutableStateOf<List<Video>?>(null) }
    var batchDeleteTargetFolders by remember { mutableStateOf<List<Folder>?>(null) }
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }

    // Clear selection when navigating into/out of folder
    LaunchedEffect(selectedFolder) {
        selectedFolderPaths = emptySet()
        selectedVideoIds = emptySet()
    }

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

    // Active directory path normalized
    val normCurrent = remember(selectedFolder) {
        normalizePath(selectedFolder?.path)
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
        }
    }

    val canGoBack = selectedFolder != null

    val selectPath: (String) -> Unit = { rawTarget ->
        val targetPath = normalizePath(rawTarget)
        if (targetPath == "/storage/emulated/0" || targetPath.isEmpty() || targetPath == "/storage/emulated") {
            onSelectFolder(null)
        } else {
            val match = folders.find {
                normalizePath(it.path) == targetPath || it.path == targetPath
            }
            if (match != null) {
                onSelectFolder(match.copy(path = targetPath))
            } else {
                val hasChildren = folders.any { normalizePath(it.path).startsWith("$targetPath/") }
                if (hasChildren) {
                    onSelectFolder(
                        Folder(
                            name = targetPath.substringAfterLast('/'),
                            path = targetPath,
                            videoCount = 0,
                            totalSizeBytes = 0L
                        )
                    )
                } else {
                    onSelectFolder(null)
                }
            }
        }
    }

    val navigateUp: () -> Unit = {
        if (canGoBack) {
            val parentPath = normCurrent.substringBeforeLast('/', "")
            if (parentPath.isEmpty() || parentPath == "/storage/emulated" || parentPath == "/storage/emulated/0") {
                onSelectFolder(null)
            } else {
                val hasAncestorVideos = folders.any {
                    val p = normalizePath(it.path)
                    p == parentPath || p.startsWith("$parentPath/")
                }
                if (hasAncestorVideos) {
                    selectPath(parentPath)
                } else {
                    onSelectFolder(null)
                }
            }
        }
    }

    BackHandler(enabled = isSelectionMode || canGoBack) {
        if (isSelectionMode) {
            selectedFolderPaths = emptySet()
            selectedVideoIds = emptySet()
        } else {
            navigateUp()
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedFolder,
            transitionSpec = {
                fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)))
            },
            label = "folderNavigationTransition",
            modifier = Modifier.fillMaxSize()
        ) { currentFolder ->
            if (currentFolder == null) {
                // Root View: Display all Library Folders with thumbnail and pill badges
                val hazeState = LocalHazeState.current
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBarTop + 96.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "section_header") {
                        SectionTitle(stringResource(R.string.folders))
                    }

                    gridItems(folders, key = { "root_folder_${it.path}" }, contentType = { "folder_card" }) { folder ->
                        val normP = remember(folder.path) { normalizePath(folder.path) }
                        val folderVids = folderVideosMap[normP] ?: folderVideosMap[folder.name.lowercase()].orEmpty()
                        val isFolderSelected = folder.path in selectedFolderPaths

                        FolderCard(
                            folder = folder,
                            videos = folderVids,
                            isSelected = isFolderSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedFolderPaths = if (isFolderSelected) {
                                        selectedFolderPaths - folder.path
                                    } else {
                                        selectedFolderPaths + folder.path
                                    }
                                } else {
                                    onSelectFolder(folder)
                                }
                            },
                            onLongClick = {
                                selectedFolderPaths = if (isFolderSelected) {
                                    selectedFolderPaths - folder.path
                                } else {
                                    selectedFolderPaths + folder.path
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(40.dp))
                    }
                }
            } else {
                // Selected Folder View: Display direct subfolders and individual videos in this directory
                val hazeState = LocalHazeState.current
                val currentNorm = remember(currentFolder.path) { normalizePath(currentFolder.path) }
                val directVideos = remember(currentFolder, folderVideos, folderVideosMap, videos) {
                    val list = if (folderVideos.isNotEmpty()) {
                        folderVideos
                    } else {
                        val fromMap = folderVideosMap[currentNorm] ?: folderVideosMap[currentFolder.name.lowercase()].orEmpty()
                        if (fromMap.isNotEmpty()) {
                            fromMap
                        } else {
                            videos.filter {
                                val vNorm = normalizePath(it.folderPath)
                                vNorm == currentNorm || it.folderPath.equals(currentFolder.path, ignoreCase = true)
                            }
                        }
                    }
                    list.sortedWith(NaturalVideoComparator)
                }
                val directSubfolders = remember(folders, currentNorm, folderVideosMap, videos) {
                    val subPathMap = mutableMapOf<String, MutableList<Video>>()

                    // Group all videos under current directory by their direct child subfolder
                    videos.forEach { v ->
                        val vNorm = normalizePath(v.folderPath)
                        if (vNorm.startsWith("$currentNorm/") && vNorm != currentNorm) {
                            val relative = vNorm.removePrefix("$currentNorm/")
                            val nextSegment = relative.substringBefore('/')
                            val directSubPath = "$currentNorm/$nextSegment"
                            subPathMap.getOrPut(directSubPath) { mutableListOf() }.add(v)
                        }
                    }

                    // Also include folders from library that start with currentNorm
                    folders.forEach { f ->
                        val fNorm = normalizePath(f.path)
                        if (fNorm.startsWith("$currentNorm/") && fNorm != currentNorm) {
                            val relative = fNorm.removePrefix("$currentNorm/")
                            val nextSegment = relative.substringBefore('/')
                            val directSubPath = "$currentNorm/$nextSegment"
                            if (!subPathMap.containsKey(directSubPath)) {
                                val fromMap = folderVideosMap[directSubPath] ?: folderVideosMap[nextSegment.lowercase()].orEmpty()
                                subPathMap[directSubPath] = fromMap.toMutableList()
                            }
                        }
                    }

                    subPathMap.map { (path, vids) ->
                        val nextSegment = path.substringAfterLast('/')
                        val matchingFolder = folders.find { normalizePath(it.path) == path }
                        val count = if (vids.isNotEmpty()) vids.size else (matchingFolder?.videoCount ?: 0)
                        val totalSize = if (vids.isNotEmpty()) vids.sumOf { it.sizeBytes } else (matchingFolder?.totalSizeBytes ?: 0L)
                        Folder(
                            name = nextSegment,
                            path = path,
                            videoCount = count,
                            totalSizeBytes = totalSize
                        )
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                }

                val folderMeta = folders.find { normalizePath(it.path) == currentNorm || it.path == currentFolder.path }
                val expectedCount = folderMeta?.videoCount ?: currentFolder.videoCount
                val folderGridState = remember(currentFolder.path) { LazyGridState() }

                LazyVerticalGrid(
                    state = folderGridState,
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBarTop + 96.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                ) {
                    // Render any Subfolders under this directory
                    if (directSubfolders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "subfolders_header") {
                            SectionTitle("Subfolders")
                        }

                        gridItems(directSubfolders, key = { "sub_folder_${it.path}" }, contentType = { "folder_card" }) { subFolder ->
                            val normP = remember(subFolder.path) { normalizePath(subFolder.path) }
                            val subFolderVids = remember(normP, folderVideosMap, videos) {
                                val fromMap = folderVideosMap[normP] ?: folderVideosMap[subFolder.name.lowercase()].orEmpty()
                                if (fromMap.isNotEmpty()) fromMap
                                else videos.filter {
                                    val vn = normalizePath(it.folderPath)
                                    vn == normP || vn.startsWith("$normP/")
                                }
                            }
                            val isFolderSelected = subFolder.path in selectedFolderPaths

                            FolderCard(
                                folder = subFolder,
                                videos = subFolderVids,
                                isSelected = isFolderSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedFolderPaths = if (isFolderSelected) {
                                            selectedFolderPaths - subFolder.path
                                        } else {
                                            selectedFolderPaths + subFolder.path
                                        }
                                    } else {
                                        onSelectFolder(subFolder)
                                    }
                                },
                                onLongClick = {
                                    selectedFolderPaths = if (isFolderSelected) {
                                        selectedFolderPaths - subFolder.path
                                    } else {
                                        selectedFolderPaths + subFolder.path
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        if (directVideos.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "videos_header") {
                                SectionTitle(stringResource(R.string.videos))
                            }
                        }
                    }

                    if (directVideos.isEmpty() && directSubfolders.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyState(
                                    icon = Icons.Default.FolderOff,
                                    label = stringResource(R.string.empty_folder)
                                )
                            }
                        }
                    } else {
                        gridItems(directVideos, key = { "video_${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "video_row" }) { video ->
                            val isVideoSelected = video.id in selectedVideoIds
                            ListVideoRow(
                                video = video,
                                isSelected = isVideoSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedVideoIds = if (isVideoSelected) {
                                            selectedVideoIds - video.id
                                        } else {
                                            selectedVideoIds + video.id
                                        }
                                    } else {
                                        onPlay(video, directVideos)
                                    }
                                },
                                onLongClick = {
                                    selectedVideoIds = if (isVideoSelected) {
                                        selectedVideoIds - video.id
                                    } else {
                                        selectedVideoIds + video.id
                                    }
                                },
                                modifier = Modifier.animateItem(),
                                onMoreClick = if (!isSelectionMode) { { selectedVideoForMenu = video } } else null,
                                dropdownMenu = {
                                    if (!isSelectionMode && selectedVideoForMenu?.id == video.id) {
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
        }

        // Top Floating Header with Brand Logo/Title and Location Bar below it
        FoldersBrandHeader(
            currentPath = normCurrent,
            onNavigateToPath = selectPath,
            onBack = navigateUp,
            isScrolled = isScrolled,
            isSelectionMode = isSelectionMode,
            selectedCount = if (selectedFolder == null) selectedFolderPaths.size else selectedVideoIds.size,
            onSearch = onSearch,
            onRefresh = onRefresh,
            onMoreClick = { isOverflowMenuOpen = true },
            dropdownMenu = {
                if (isSelectionMode) {
                    if (selectedFolder == null) {
                        val selFolders = folders.filter { it.path in selectedFolderPaths }
                        val isFav = selFolders.size == 1 && favoriteFolders.any { it.path == selFolders.first().path }
                        val allFav = selFolders.isNotEmpty() && selFolders.all { f -> favoriteFolders.any { it.path == f.path } }
                        BatchFolderOptionsMenu(
                            expanded = isOverflowMenuOpen,
                            selectedFolders = selFolders,
                            favoriteFolderPaths = favoriteFolders.map { it.path }.toSet(),
                            isFavorite = isFav,
                            onDismiss = { isOverflowMenuOpen = false },
                            onToggleFavorites = {
                                onSetFoldersFavorite(selFolders, !allFav)
                                selectedFolderPaths = emptySet()
                            },
                            onRename = if (selFolders.size == 1) {
                                { renameFolderTarget = selFolders.first() }
                            } else null,
                            onProperties = {
                                if (selFolders.isNotEmpty()) {
                                    propertiesFolder = selFolders.first()
                                }
                            },
                            onDelete = {
                                batchDeleteTargetFolders = selFolders
                            }
                        )
                    } else {
                        val selVideos = folderVideos.filter { it.id in selectedVideoIds }
                        val allFav = selVideos.isNotEmpty() && selVideos.all { v -> favorites.any { it.id == v.id } || v.isFavorite }
                        BatchVideoOptionsMenu(
                            expanded = isOverflowMenuOpen,
                            selectedVideos = selVideos,
                            onDismiss = { isOverflowMenuOpen = false },
                            onPlaySelected = {
                                if (selVideos.isNotEmpty()) {
                                    onPlay(selVideos.first(), selVideos)
                                    selectedVideoIds = emptySet()
                                }
                            },
                            onAddToPlaylist = {
                                if (selVideos.isNotEmpty()) {
                                    playlistVideoTarget = selVideos.first()
                                }
                            },
                            onToggleFavorites = {
                                onSetVideosFavorite(selVideos, !allFav)
                                selectedVideoIds = emptySet()
                            },
                            onShare = {
                                val uris = ArrayList<android.net.Uri>()
                                selVideos.forEach { uris.add(android.net.Uri.parse(it.uri)) }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "video/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Videos"))
                                selectedVideoIds = emptySet()
                            },
                            onRename = if (selVideos.size == 1) {
                                { renameVideoTarget = selVideos.first() }
                            } else null,
                            onProperties = {
                                if (selVideos.size == 1) {
                                    propertiesVideo = selVideos.first()
                                } else {
                                    propertiesMultiVideos = selVideos
                                }
                            },
                            onDelete = {
                                batchDeleteTargetVideos = selVideos
                            }
                        )
                    }
                }
            },
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

    propertiesMultiVideos?.let { vids ->
        MultiVideoPropertiesDialog(
            videos = vids,
            onDismiss = { propertiesMultiVideos = null }
        )
    }

    propertiesFolder?.let { folder ->
        FolderPropertiesDialog(
            folders = listOf(folder),
            onDismiss = { propertiesFolder = null }
        )
    }

    renameVideoTarget?.let { video ->
        RenameVideoDialog(
            video = video,
            onRename = { 
                onRenameVideo(video, it)
                selectedVideoIds = emptySet()
            },
            onDismiss = { renameVideoTarget = null }
        )
    }

    renameFolderTarget?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onRename = { newName ->
                onRenameFolder(folder, newName)
                selectedFolderPaths = emptySet()
            },
            onDismiss = { renameFolderTarget = null }
        )
    }

    deleteVideoTarget?.let { video ->
        DeleteConfirmDialog(
            video = video,
            onDelete = { onDeleteVideo(video) },
            onDismiss = { deleteVideoTarget = null }
        )
    }

    deleteFolderTarget?.let { folder ->
        BatchDeleteConfirmationDialog(
            itemCount = 1,
            itemType = "folder",
            onConfirm = {
                onDeleteFolders(listOf(folder))
            },
            onDismiss = { deleteFolderTarget = null }
        )
    }

    batchDeleteTargetVideos?.let { vids ->
        BatchDeleteConfirmationDialog(
            itemCount = vids.size,
            itemType = if (vids.size == 1) "video" else "videos",
            onConfirm = {
                onDeleteVideos(vids)
                selectedVideoIds = emptySet()
            },
            onDismiss = { batchDeleteTargetVideos = null }
        )
    }

    batchDeleteTargetFolders?.let { flds ->
        BatchDeleteConfirmationDialog(
            itemCount = flds.size,
            itemType = if (flds.size == 1) "folder" else "folders",
            onConfirm = {
                onDeleteFolders(flds)
                selectedFolderPaths = emptySet()
            },
            onDismiss = { batchDeleteTargetFolders = null }
        )
    }

    playlistVideoTarget?.let { video ->
        AddToPlaylistDialog(
            video = video,
            playlists = playlists,
            onSelectPlaylist = { playlistId -> 
                if (selectedVideoIds.isNotEmpty()) {
                    val selVideos = folderVideos.filter { it.id in selectedVideoIds }
                    selVideos.forEach { onAddToPlaylist(playlistId, it) }
                    selectedVideoIds = emptySet()
                } else {
                    onAddToPlaylist(playlistId, video) 
                }
            },
            onCreatePlaylist = { title -> onCreatePlaylist(title) },
            onDismiss = { playlistVideoTarget = null }
        )
    }
}

