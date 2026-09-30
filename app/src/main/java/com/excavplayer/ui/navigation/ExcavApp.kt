package com.excavplayer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    BackHandler(enabled = searchOpen || library.selectedFolder != null) {
        if (searchOpen) searchOpen = false
        else vm.closeFolder()
    }

    Scaffold(
        containerColor = ExcavPalette.Ink
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
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
                    if (tab != MainTab.SETTINGS) {
                        BrandHeader(
                            onSearch = { searchOpen = true },
                            onRefresh = { vm.refreshLibrary() }
                        )
                    }

                    if (library.loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                    onDeleteVideo = vm::deleteVideo
                                )
                                MainTab.FOLDERS -> FoldersScreen(
                                    folders = library.folders,
                                    selectedFolder = library.selectedFolder,
                                    folderVideos = library.folderVideos,
                                    playlists = library.playlists,
                                    favorites = library.favorites,
                                    onSelectFolder = { folder ->
                                        if (folder == null) vm.closeFolder() else vm.openFolder(folder)
                                    },
                                    onPlay = {
                                        vm.play(it)
                                        playerOpen = true
                                    },
                                    onToggleFavorite = vm::toggleFavorite,
                                    onAddToPlaylist = vm::addVideoToPlaylist,
                                    onCreatePlaylist = vm::createPlaylist,
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo
                                )
                                MainTab.PLAYLISTS -> PlaylistsScreen(
                                    playlists = library.playlists,
                                    favorites = library.favorites,
                                    onCreate = vm::createPlaylist,
                                    onRename = vm::renamePlaylist,
                                    onDelete = vm::deletePlaylist,
                                    onPlay = {
                                        vm.play(it)
                                        playerOpen = true
                                    },
                                    onToggleFavorite = vm::toggleFavorite,
                                    onAddToPlaylist = vm::addVideoToPlaylist,
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo,
                                    onRemoveFromPlaylist = vm::removeVideoFromPlaylist,
                                    observePlaylistItems = vm::observePlaylistItems
                                )
                                MainTab.FAVORITES -> FavoritesScreen(
                                    videos = library.favorites,
                                    playlists = library.playlists,
                                    onPlay = {
                                        vm.play(it)
                                        playerOpen = true
                                    },
                                    onToggleFavorite = vm::toggleFavorite,
                                    onAddToPlaylist = vm::addVideoToPlaylist,
                                    onCreatePlaylist = vm::createPlaylist,
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = if (searchOpen) 16.dp else 78.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                playerOpen = true
                            },
                        color = ExcavPalette.SurfaceCard,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, ExcavPalette.Blue.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(ExcavPalette.Blue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "Background Audio",
                                    tint = ExcavPalette.Blue,
                                    modifier = Modifier.size(20.dp)
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
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (playerState.playback.isPlaying) "Playing in background" else "Paused",
                                    color = ExcavPalette.Blue,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
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
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (playerState.playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playerState.playback.isPlaying) "Pause" else "Play",
                                    tint = ExcavPalette.Text,
                                    modifier = Modifier.size(24.dp)
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Pure Floating Bottom Navbar (No solid background wall bar)
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

