package com.excavplayer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var playerOpen by rememberSaveable { mutableStateOf(false) }


    val library by vm.libraryState.collectAsState()
    val settings by vm.userSettings.collectAsState()
    val userMessage by vm.userMessage.collectAsState()

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

    if (playerOpen) {
        PlayerScreen(
            vm = vm,
            onClose = { playerOpen = false },
            onPickSubtitle = onPickSubtitle
        )
        return
    }

    BackHandler(enabled = searchOpen || (tab != MainTab.FOLDERS && library.selectedFolder != null) || tab != MainTab.HOME) {
        if (searchOpen) searchOpen = false
        else if (tab != MainTab.HOME) tab = MainTab.HOME
        else vm.closeFolder()
    }

    val hazeState = rememberHazeState()

    val homeGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val foldersGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val favoritesGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    Scaffold(
        containerColor = ExcavPalette.Ink
    ) { padding ->
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (searchOpen) {
                        SearchScreen(
                            query = library.searchQuery,
                            results = library.searchResults,
                            onQueryChange = vm::setSearchQuery,
                            onBack = { searchOpen = false },
                            onPlay = {
                                vm.play(it)
                                playerOpen = true
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
                                    onPlay = {
                                        vm.play(it)
                                        playerOpen = true
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
                                    playlists = library.playlists,
                                    favorites = library.favorites,
                                    onSelectFolder = { folder ->
                                        if (folder == null) vm.closeFolder() else vm.openFolder(folder)
                                    },
                                    onPlay = {
                                        vm.play(it, library.folderVideos)
                                        playerOpen = true
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
                                        playerOpen = true
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
                                        playerOpen = true
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

            // Sleek Floating Mini Player when audio is playing in the background
            val playerState by vm.player.state.collectAsState()
            val isBackgroundAudioActive = !playerOpen && playerState.currentVideo != null && (playerState.isBackgroundAudio || playerState.playback.isPlaying)

            if (isBackgroundAudioActive) {
                val currentVideo = playerState.currentVideo
                if (currentVideo != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = if (searchOpen) 16.dp else 84.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        GlassmorphicItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerOpen = true
                                },
                            cornerRadius = 32,
                            blurRadius = 15,
                            containerColor = Color(0x66141822)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
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

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = currentVideo.displayName,
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (playerState.playback.isPlaying) "Playing in background" else "Paused",
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (playerState.playback.isPlaying) {
                                        vm.player.pause()
                                    } else {
                                        vm.player.resume()
                                    }
                                },
                                modifier = Modifier.size(34.dp)
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
                                modifier = Modifier.size(34.dp)
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
            val updateState by vm.updateState.collectAsState()
            UpdateDialog(
                updateState = updateState,
                onDownload = { asset -> vm.downloadAndInstallUpdate(asset) },
                onInstall = { file -> vm.installApk(file) },
                onDismiss = { vm.dismissUpdate() }
            )
        }
    }
}
}


