package com.excavplayer.ui.folders

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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.excavplayer.domain.model.Folder
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

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedFolder,
            transitionSpec = {
                fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)))
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
                        FolderCard(
                            folder = folder,
                            videos = folderVids,
                            onClick = { onSelectFolder(folder) },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(40.dp))
                    }
                }
            } else {
                // Selected Folder View: Display Videos directly in this directory without subfolder grouping
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
                    if (folderVideos.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyState(
                                    icon = Icons.Default.FolderOff,
                                    label = stringResource(R.string.empty_videos)
                                )
                            }
                        }
                    } else {
                        gridItems(folderVideos, key = { "video_${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "video_row" }) { video ->
                            ListVideoRow(
                                video = video,
                                onClick = { onPlay(video) },
                                modifier = Modifier.animateItem(),
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
        }

        // Top Floating Header with Brand Logo/Title and Location Bar below it
        FoldersBrandHeader(
            currentPath = normCurrent,
            onNavigateToPath = selectPath,
            onBack = navigateUp,
            isScrolled = isScrolled,
            onSearch = onSearch,
            onRefresh = onRefresh,
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
