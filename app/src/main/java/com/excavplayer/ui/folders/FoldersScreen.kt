package com.excavplayer.ui.folders

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.excavplayer.R
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.NaturalVideoComparator
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.AddToPlaylistDialog
import com.excavplayer.ui.components.BatchFolderOptionsMenu
import com.excavplayer.ui.components.BatchVideoOptionsMenu
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.FolderCard
import com.excavplayer.ui.components.FolderPropertiesDialog
import com.excavplayer.ui.components.FoldersBrandHeader
import com.excavplayer.ui.components.ListVideoRow
import com.excavplayer.ui.components.LocalHazeState
import com.excavplayer.ui.components.MultiVideoPropertiesDialog
import com.excavplayer.ui.components.RenameFolderDialog
import com.excavplayer.ui.components.RenameVideoDialog
import com.excavplayer.ui.components.SectionTitle
import com.excavplayer.ui.components.VideoOptionsMenu
import com.excavplayer.ui.components.VideoPropertiesDialog
import com.excavplayer.ui.folders.logic.FolderTreeManager
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
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }

    // Clear selection when navigating into/out of folder
    LaunchedEffect(selectedFolder) {
        selectedFolderPaths = emptySet()
        selectedVideoIds = emptySet()
    }

    // Build the hierarchical directory tree from all detected video files
    val tree = remember(videos) {
        FolderTreeManager.buildTree(videos)
    }

    // Active directory path normalized
    val normCurrent = remember(selectedFolder) {
        selectedFolder?.path?.let { FolderTreeManager.normalizePath(it) } ?: FolderTreeManager.INTERNAL_STORAGE_ROOT
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
        }
    }

    val canGoBack = selectedFolder != null

    val selectPath: (String) -> Unit = { rawTarget ->
        val targetPath = FolderTreeManager.normalizePath(rawTarget)
        if (targetPath == FolderTreeManager.INTERNAL_STORAGE_ROOT || targetPath.isEmpty() || targetPath == "/storage/emulated") {
            onSelectFolder(null)
        } else {
            val node = tree.nodesByPath[targetPath]
            if (node != null) {
                onSelectFolder(node.toFolder())
            } else {
                val match = folders.find { FolderTreeManager.normalizePath(it.path) == targetPath }
                if (match != null) {
                    onSelectFolder(match.copy(path = targetPath))
                } else {
                    onSelectFolder(
                        Folder(
                            name = FolderTreeManager.formatDirectoryName(targetPath),
                            path = targetPath,
                            videoCount = 0,
                            totalSizeBytes = 0L
                        )
                    )
                }
            }
        }
    }

    val navigateUp: () -> Unit = {
        if (canGoBack) {
            val parentPath = FolderTreeManager.getParentPath(normCurrent)
            if (parentPath == null || parentPath == FolderTreeManager.INTERNAL_STORAGE_ROOT) {
                onSelectFolder(null)
            } else {
                selectPath(parentPath)
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
                // Root View: Display top-level directories containing video files and any root videos
                val hazeState = LocalHazeState.current
                val rootSubfolders = remember(tree) { FolderTreeManager.getSubfolders(tree, null) }
                val rootDirectVideos = remember(tree) { FolderTreeManager.getDirectVideos(tree, null) }
                val rootPreviews = remember(tree) { FolderTreeManager.getSubfolderPreviews(tree, null) }

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
                    if (rootSubfolders.isEmpty() && rootDirectVideos.isEmpty()) {
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
                        if (rootSubfolders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "section_header") {
                                SectionTitle(stringResource(R.string.folders))
                            }

                            gridItems(rootSubfolders, key = { "root_folder_${it.path}" }, contentType = { "folder_card" }) { folder ->
                                val folderVids = rootPreviews[folder.path] ?: folderVideosMap[folder.path].orEmpty()
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
                        }

                        if (rootDirectVideos.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "videos_header") {
                                SectionTitle(stringResource(R.string.videos))
                            }

                            gridItems(rootDirectVideos, key = { "root_video_${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "video_row" }) { video ->
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
                                            onPlay(video, rootDirectVideos)
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
                                                onDelete = { onDeleteVideo(video) }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(40.dp))
                    }
                }
            } else {
                // Selected Folder View: Display direct subfolders and individual videos in this directory
                val hazeState = LocalHazeState.current
                val currentNorm = remember(currentFolder.path) { FolderTreeManager.normalizePath(currentFolder.path) }
                val directSubfolders = remember(tree, currentNorm) { FolderTreeManager.getSubfolders(tree, currentNorm) }
                val directVideos = remember(tree, currentNorm) { FolderTreeManager.getDirectVideos(tree, currentNorm) }
                val previews = remember(tree, currentNorm) { FolderTreeManager.getSubfolderPreviews(tree, currentNorm) }

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
                            val subFolderVids = previews[subFolder.path] ?: folderVideosMap[subFolder.path].orEmpty()
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
                                            onDelete = { onDeleteVideo(video) }
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
                        val selFolders = tree.rootVolumes.flatMap { it.subfolders.values }.map { it.toFolder() }.filter { it.path in selectedFolderPaths }.ifEmpty { folders.filter { it.path in selectedFolderPaths } }
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
                                onDeleteFolders(selFolders)
                                selectedFolderPaths = emptySet()
                            }
                        )
                    } else {
                        val selVideos = videos.filter { it.id in selectedVideoIds }
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
                                onDeleteVideos(selVideos)
                                selectedVideoIds = emptySet()
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

    playlistVideoTarget?.let { video ->
        AddToPlaylistDialog(
            video = video,
            playlists = playlists,
            onSelectPlaylist = { playlistId -> 
                if (selectedVideoIds.isNotEmpty()) {
                    val selVideos = videos.filter { it.id in selectedVideoIds }
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
