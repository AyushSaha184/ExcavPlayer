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
import androidx.compose.runtime.collectAsState
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
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.AddToPlaylistDialog
import com.excavplayer.ui.components.BatchDeleteConfirmationDialog
import com.excavplayer.ui.components.BatchFolderOptionsMenu
import com.excavplayer.ui.components.BatchVideoOptionsMenu
import com.excavplayer.ui.components.DeleteConfirmDialog
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
import com.excavplayer.ui.folders.logic.FolderStateManager
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

    // Dedicated state manager for hierarchical navigation and selection
    val stateManager = remember { FolderStateManager(selectedFolder?.path) }

    // Synchronize external changes to selectedFolder (e.g. from HomeScreen or restored settings)
    LaunchedEffect(selectedFolder?.path) {
        val target = selectedFolder?.path
        if (target != stateManager.currentPath.value) {
            stateManager.navigateToPath(target)
        }
    }

    val activePath by stateManager.currentPath.collectAsState()
    val selFolderPaths by stateManager.selectedFolderPaths.collectAsState()
    val selVideoIds by stateManager.selectedVideoIds.collectAsState()

    // Compute reactive UI state from active videos and tree hierarchy
    val folderState = remember(videos, activePath, selFolderPaths, selVideoIds) {
        stateManager.computeUiState(videos)
    }

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

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
        }
    }

    fun handleNavigateTo(targetPath: String?) {
        stateManager.navigateToPath(targetPath)
        val current = stateManager.currentPath.value
        if (current == null) {
            onSelectFolder(null)
        } else {
            val name = FolderTreeManager.formatDirectoryName(current)
            onSelectFolder(Folder(name = name, path = current, videoCount = 0, totalSizeBytes = 0L))
        }
    }

    fun handleNavigateUp() {
        val wentUp = stateManager.navigateUp()
        if (wentUp) {
            val current = stateManager.currentPath.value
            if (current == null) {
                onSelectFolder(null)
            } else {
                val name = FolderTreeManager.formatDirectoryName(current)
                onSelectFolder(Folder(name = name, path = current, videoCount = 0, totalSizeBytes = 0L))
            }
        }
    }

    BackHandler(enabled = folderState.isSelectionMode || folderState.canGoBack) {
        if (folderState.isSelectionMode) {
            stateManager.clearSelection()
        } else {
            handleNavigateUp()
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = folderState.currentPath,
            transitionSpec = {
                fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)))
            },
            label = "folderNavigationTransition",
            modifier = Modifier.fillMaxSize()
        ) { activeDir ->
            val hazeState = LocalHazeState.current

            if (activeDir == null) {
                // Root View: Display Top-Level Storage Folders and root direct videos
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

                    if (folderState.subfolders.isEmpty() && folderState.directVideos.isEmpty()) {
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
                        gridItems(folderState.subfolders, key = { "root_folder_${it.path}" }, contentType = { "folder_card" }) { folder ->
                            val folderVids = folderState.subfolderPreviewVideos[folder.path].orEmpty()
                            val isFolderSelected = folder.path in folderState.selectedFolderPaths

                            FolderCard(
                                folder = folder,
                                videos = folderVids,
                                isSelected = isFolderSelected,
                                isSelectionMode = folderState.isSelectionMode,
                                onClick = {
                                    if (folderState.isSelectionMode) {
                                        stateManager.toggleFolderSelection(folder.path)
                                    } else {
                                        stateManager.navigateInto(folder)
                                        onSelectFolder(folder)
                                    }
                                },
                                onLongClick = {
                                    stateManager.toggleFolderSelection(folder.path)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        if (folderState.directVideos.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "root_videos_header") {
                                SectionTitle(stringResource(R.string.videos))
                            }

                            gridItems(folderState.directVideos, key = { "root_vid_${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "video_row" }) { video ->
                                val isVideoSelected = video.id in folderState.selectedVideoIds
                                ListVideoRow(
                                    video = video,
                                    isSelected = isVideoSelected,
                                    isSelectionMode = folderState.isSelectionMode,
                                    onClick = {
                                        if (folderState.isSelectionMode) {
                                            stateManager.toggleVideoSelection(video.id)
                                        } else {
                                            onPlay(video, folderState.directVideos)
                                        }
                                    },
                                    onLongClick = {
                                        stateManager.toggleVideoSelection(video.id)
                                    },
                                    modifier = Modifier.animateItem(),
                                    onMoreClick = if (!folderState.isSelectionMode) { { selectedVideoForMenu = video } } else null,
                                    dropdownMenu = {
                                        if (!folderState.isSelectionMode && selectedVideoForMenu?.id == video.id) {
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

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(40.dp))
                    }
                }
            } else {
                // Nested Directory View: Display direct subfolders and direct videos in this directory
                val folderGridState = remember(activeDir) { LazyGridState() }

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
                    if (folderState.subfolders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "subfolders_header") {
                            SectionTitle("Subfolders")
                        }

                        gridItems(folderState.subfolders, key = { "sub_folder_${it.path}" }, contentType = { "folder_card" }) { subFolder ->
                            val subFolderVids = folderState.subfolderPreviewVideos[subFolder.path].orEmpty()
                            val isFolderSelected = subFolder.path in folderState.selectedFolderPaths

                            FolderCard(
                                folder = subFolder,
                                videos = subFolderVids,
                                isSelected = isFolderSelected,
                                isSelectionMode = folderState.isSelectionMode,
                                onClick = {
                                    if (folderState.isSelectionMode) {
                                        stateManager.toggleFolderSelection(subFolder.path)
                                    } else {
                                        stateManager.navigateInto(subFolder)
                                        onSelectFolder(subFolder)
                                    }
                                },
                                onLongClick = {
                                    stateManager.toggleFolderSelection(subFolder.path)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        if (folderState.directVideos.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "videos_header") {
                                SectionTitle(stringResource(R.string.videos))
                            }
                        }
                    }

                    if (folderState.isEmpty) {
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
                        gridItems(folderState.directVideos, key = { "video_${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "video_row" }) { video ->
                            val isVideoSelected = video.id in folderState.selectedVideoIds
                            ListVideoRow(
                                video = video,
                                isSelected = isVideoSelected,
                                isSelectionMode = folderState.isSelectionMode,
                                onClick = {
                                    if (folderState.isSelectionMode) {
                                        stateManager.toggleVideoSelection(video.id)
                                    } else {
                                        onPlay(video, folderState.directVideos)
                                    }
                                },
                                onLongClick = {
                                    stateManager.toggleVideoSelection(video.id)
                                },
                                modifier = Modifier.animateItem(),
                                onMoreClick = if (!folderState.isSelectionMode) { { selectedVideoForMenu = video } } else null,
                                dropdownMenu = {
                                    if (!folderState.isSelectionMode && selectedVideoForMenu?.id == video.id) {
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

        // Top Floating Header with Brand Logo/Title and Location Breadcrumb Bar
        FoldersBrandHeader(
            currentPath = folderState.normalizedPath,
            onNavigateToPath = { handleNavigateTo(it) },
            onBack = { handleNavigateUp() },
            isScrolled = isScrolled,
            isSelectionMode = folderState.isSelectionMode,
            selectedCount = folderState.selectedFolderPaths.size + folderState.selectedVideoIds.size,
            onSearch = onSearch,
            onRefresh = onRefresh,
            onMoreClick = { isOverflowMenuOpen = true },
            dropdownMenu = {
                if (folderState.isSelectionMode) {
                    val selFolders = folderState.subfolders.filter { it.path in folderState.selectedFolderPaths }
                    val selVideos = folderState.directVideos.filter { it.id in folderState.selectedVideoIds }

                    if (folderState.currentPath == null) {
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
                                stateManager.clearSelection()
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
                        val allFav = selVideos.isNotEmpty() && selVideos.all { v -> favorites.any { it.id == v.id } || v.isFavorite }
                        BatchVideoOptionsMenu(
                            expanded = isOverflowMenuOpen,
                            selectedVideos = selVideos,
                            onDismiss = { isOverflowMenuOpen = false },
                            onPlaySelected = {
                                if (selVideos.isNotEmpty()) {
                                    onPlay(selVideos.first(), selVideos)
                                    stateManager.clearSelection()
                                }
                            },
                            onAddToPlaylist = {
                                if (selVideos.isNotEmpty()) {
                                    playlistVideoTarget = selVideos.first()
                                }
                            },
                            onToggleFavorites = {
                                onSetVideosFavorite(selVideos, !allFav)
                                stateManager.clearSelection()
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
                                stateManager.clearSelection()
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
                stateManager.clearSelection()
            },
            onDismiss = { renameVideoTarget = null }
        )
    }

    renameFolderTarget?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onRename = { newName ->
                onRenameFolder(folder, newName)
                stateManager.clearSelection()
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
                val descendantVideos = stateManager.resolveDescendantVideos(listOf(folder.path))
                if (descendantVideos.isNotEmpty()) {
                    onDeleteVideos(descendantVideos)
                } else {
                    onDeleteFolders(listOf(folder))
                }
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
                stateManager.clearSelection()
            },
            onDismiss = { batchDeleteTargetVideos = null }
        )
    }

    batchDeleteTargetFolders?.let { flds ->
        BatchDeleteConfirmationDialog(
            itemCount = flds.size,
            itemType = if (flds.size == 1) "folder" else "folders",
            onConfirm = {
                val allVidsInFolders = stateManager.resolveDescendantVideos(flds.map { it.path })
                if (allVidsInFolders.isNotEmpty()) {
                    onDeleteVideos(allVidsInFolders)
                } else {
                    onDeleteFolders(flds)
                }
                stateManager.clearSelection()
            },
            onDismiss = { batchDeleteTargetFolders = null }
        )
    }

    playlistVideoTarget?.let { video ->
        AddToPlaylistDialog(
            video = video,
            playlists = playlists,
            onSelectPlaylist = { playlistId -> 
                if (folderState.selectedVideoIds.isNotEmpty()) {
                    val selVideos = folderState.directVideos.filter { it.id in folderState.selectedVideoIds }
                    selVideos.forEach { onAddToPlaylist(playlistId, it) }
                    stateManager.clearSelection()
                } else {
                    onAddToPlaylist(playlistId, video) 
                }
            },
            onCreatePlaylist = { title -> onCreatePlaylist(title) },
            onDismiss = { playlistVideoTarget = null }
        )
    }
}
