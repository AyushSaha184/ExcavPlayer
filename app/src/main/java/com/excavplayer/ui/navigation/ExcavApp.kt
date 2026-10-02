package com.excavplayer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.excavplayer.R
import com.excavplayer.ui.ExcavViewModel
import com.excavplayer.ui.components.*
import com.excavplayer.ui.favorites.FavoritesScreen
import com.excavplayer.ui.folders.*
import com.excavplayer.ui.home.HomeScreen
import com.excavplayer.ui.player.PlayerScreen
import com.excavplayer.ui.playlists.PlaylistsScreen
import com.excavplayer.ui.search.SearchScreen
import com.excavplayer.ui.settings.SettingsScreen
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes

@Composable
fun ExcavApp(
    vm: ExcavViewModel,
    onPickSubtitle: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val playerOpen by vm.isPlayerOpen.collectAsStateWithLifecycle()

    val library by vm.libraryState.collectAsStateWithLifecycle()
    val settings by vm.userSettings.collectAsStateWithLifecycle()
    val userMessage by vm.userMessage.collectAsStateWithLifecycle()

    var restoredFolder by rememberSaveable { mutableStateOf(false) }

    // Restore last opened folder on initial load if present
    LaunchedEffect(library.folders, settings.lastOpenedFolder) {
        if (!restoredFolder && library.folders.isNotEmpty()) {
            val lastPath = settings.lastOpenedFolder
            if (lastPath != null && library.selectedFolder == null) {
                val matched = library.folders.find { it.path == lastPath }
                if (matched != null) {
                    vm.openFolder(matched)
                }
            }
            restoredFolder = true
        }
    }

    BackHandler(enabled = !playerOpen && (searchOpen || (tab != MainTab.FOLDERS && library.selectedFolder != null) || tab != MainTab.HOME)) {
        if (searchOpen) searchOpen = false
        else if (tab != MainTab.HOME) tab = MainTab.HOME
        else vm.closeFolder()
    }

    val hazeState = rememberHazeState()

    val homeGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val foldersGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val favoritesGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    Scaffold(
        containerColor = ExcavPalette.Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                ) {
                    if (searchOpen) {
                        SearchScreen(
                            query = library.searchQuery,
                            results = library.searchResults,
                            onQueryChange = vm::setSearchQuery,
                            onBack = { searchOpen = false },
                            onPlay = {
                                vm.play(it)
                            }
                        )
                    } else {
                        if (library.loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = ExcavPalette.Blue,
                                    strokeWidth = 2.5.dp
                                )
                            }
                        } else {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "tab_content"
                            ) { currentTab ->
                                when (currentTab) {
                                MainTab.HOME -> HomeScreen(
                                    continueWatching = if (settings.continueWatchingEnabled) library.continueWatching else emptyList(),
                                    folders = library.folders,
                                    videos = library.videos,
                                    playlists = library.playlists,
                                    favorites = library.favorites,
                                    groups = library.groups,
                                    onPlay = { vid, list ->
                                        vm.play(vid, list)
                                    },
                                    onOpenFolder = { folder ->
                                        vm.openFolder(folder)
                                        tab = MainTab.FOLDERS
                                    },
                                    onToggleFavorite = vm::toggleFavorite,
                                    onAddToPlaylist = vm::addVideoToPlaylist,
                                    onCreatePlaylist = vm::createPlaylist,
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo,
                                    onRemoveFromContinueWatching = { vm.dismissFromContinueWatching(it.id) },
                                    gridState = homeGridState,
                                    onSearch = { searchOpen = true },
                                    onRefresh = { vm.refreshLibrary() }
                                )
                                MainTab.FOLDERS -> FoldersScreen(
                                    folders = library.folders,
                                    selectedFolder = library.selectedFolder,
                                    folderVideos = library.folderVideos,
                                    videos = library.videos,
                                    folderVideosMap = library.folderVideosMap,
                                    playlists = library.playlists,
                                    favorites = library.favorites,
                                    onSelectFolder = { folder ->
                                        if (folder == null) vm.closeFolder() else vm.openFolder(folder)
                                    },
                                    onPlay = {
                                        vm.play(it, library.folderVideos)
                                    },
                                    onToggleFavorite = vm::toggleFavorite,
                                    onAddToPlaylist = vm::addVideoToPlaylist,
                                    onCreatePlaylist = vm::createPlaylist,
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo,
                                    gridState = foldersGridState,
                                    onSearch = { searchOpen = true },
                                    onRefresh = { vm.refreshLibrary() }
                                )
                                MainTab.PLAYLISTS -> PlaylistsScreen(
                                    playlists = library.playlists,
                                    favorites = library.favorites,
                                    onCreate = vm::createPlaylist,
                                    onRename = vm::renamePlaylist,
                                    onDelete = vm::deletePlaylist,
                                    onPlay = { vid, list ->
                                        vm.play(vid, list)
                                    },
                                    onToggleFavorite = vm::toggleFavorite,
                                    onAddToPlaylist = vm::addVideoToPlaylist,
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo,
                                    onRemoveFromPlaylist = vm::removeVideoFromPlaylist,
                                    observePlaylistItems = vm::observePlaylistItems,
                                    onSearch = { searchOpen = true },
                                    onRefresh = { vm.refreshLibrary() }
                                )
                                MainTab.FAVORITES -> FavoritesScreen(
                                    videos = library.favorites,
                                    playlists = library.playlists,
                                    onPlay = {
                                        vm.play(it, library.favorites)
                                    },
                                    onToggleFavorite = vm::toggleFavorite,
                                    onAddToPlaylist = vm::addVideoToPlaylist,
                                    onCreatePlaylist = vm::createPlaylist,
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo,
                                    gridState = favoritesGridState,
                                    onSearch = { searchOpen = true },
                                    onRefresh = { vm.refreshLibrary() }
                                )
                                MainTab.SETTINGS -> SettingsScreen(
                                    vm = vm,
                                    onBack = { tab = MainTab.HOME }
                                )
                            }
                        }
                    }
                }
            }

            // Sleek Floating Mini Player when audio is playing in the background (isolated to avoid root recompositions)
            MiniPlayerOverlay(
                vm = vm,
                playerOpen = playerOpen,
                searchOpen = searchOpen,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Pure Floating Glass Bottom Navbar
            if (!searchOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    BottomNav(
                        selected = tab,
                        onSelect = { tab = it }
                    )
                }
            }

            // Top Floating Message / Error Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                UserMessageHost(
                    message = userMessage,
                    onDismiss = { vm.clearUserMessage() }
                )
            }

            // In-App Update Dialog (Release Notes, Download Progress, and Installation)
            val updateState by vm.updateState.collectAsStateWithLifecycle()
            UpdateDialog(
                updateState = updateState,
                onDownload = { asset -> vm.downloadAndInstallUpdate(asset) },
                onInstall = { file -> vm.installApk(file) },
                onDismiss = { vm.dismissUpdate() }
            )

            // Fullscreen Player with slide and fade in animation, and immediate clean close
            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(220)),
                exit = androidx.compose.animation.ExitTransition.None,
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerScreen(
                    vm = vm,
                    onClose = { vm.setPlayerOpen(false) },
                    onPickSubtitle = onPickSubtitle
                )
            }
        }
    }
}
}

@Composable
fun MiniPlayerOverlay(
    vm: ExcavViewModel,
    playerOpen: Boolean,
    searchOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val isBackgroundAudioActive = !playerOpen && playerState.currentVideo != null && (playerState.isBackgroundAudio || playerState.playback.isPlaying)

    AnimatedVisibility(
        visible = isBackgroundAudioActive && playerState.currentVideo != null,
        enter = slideInVertically(
            initialOffsetY = { it * 2 },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it * 2 },
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        val currentVideo = playerState.currentVideo ?: return@AnimatedVisibility
        val density = androidx.compose.ui.platform.LocalDensity.current
        val coroutineScope = rememberCoroutineScope()
        val dragOffsetY = remember { androidx.compose.animation.core.Animatable(0f) }
        val thresholdPx = with(density) { 70.dp.toPx() }
        val maxDragPx = with(density) { 150.dp.toPx() }

        // Real-time dynamic drag progress (0f..1f)
        val dragProgress = (dragOffsetY.value / thresholdPx).coerceIn(0f, 1f)
        val pillAlpha = (1f - (dragProgress * 0.85f)).coerceIn(0.1f, 1f)
        val pillBlur = (dragProgress * 16f).dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = if (searchOpen) 16.dp else 84.dp)
                .offset { androidx.compose.ui.unit.IntOffset(0, dragOffsetY.value.toInt()) }
                .graphicsLayer {
                    alpha = pillAlpha
                }
                .blur(pillBlur)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dragOffsetY.value >= thresholdPx) {
                                    // Pulled down past threshold: animate down and close player
                                    dragOffsetY.animateTo(
                                        targetValue = maxDragPx,
                                        animationSpec = tween(120, easing = FastOutSlowInEasing)
                                    )
                                    vm.player.stop()
                                    dragOffsetY.snapTo(0f)
                                } else {
                                    // Pulled down but released before threshold: spring back to normal
                                    dragOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            // Update drag offset in real-time as finger moves up or down
                            val nextOffset = (dragOffsetY.value + dragAmount).coerceIn(0f, maxDragPx)
                            if (nextOffset > 0f || dragAmount > 0f) {
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetY.snapTo(nextOffset)
                                }
                            }
                        }
                    )
                }
        ) {
            GlassmorphicItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable {
                        if (dragOffsetY.value < 10f) {
                            vm.setPlayerOpen(true)
                        }
                    },
                cornerRadius = 32,
                blurRadius = 15,
                containerColor = Color(0x66141822)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF232A3B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Background Audio",
                            tint = ExcavPalette.Text,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = currentVideo.displayName,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (playerState.playback.isPlaying) {
                                vm.player.pause()
                            } else {
                                vm.player.resume()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.playback.isPlaying) "Pause" else "Play",
                            tint = ExcavPalette.Text,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            vm.player.stop()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            tint = ExcavPalette.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}


