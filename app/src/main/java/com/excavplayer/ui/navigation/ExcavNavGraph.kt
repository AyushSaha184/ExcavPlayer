package com.excavplayer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.ExcavBottomNav
import com.excavplayer.ui.components.MiniPlayer
import com.excavplayer.ui.favorites.FavoritesScreen
import com.excavplayer.ui.favorites.FavoritesViewModel
import com.excavplayer.ui.folders.FolderDetailScreen
import com.excavplayer.ui.folders.FoldersScreen
import com.excavplayer.ui.folders.FoldersViewModel
import com.excavplayer.ui.history.HistoryScreen
import com.excavplayer.ui.history.HistoryViewModel
import com.excavplayer.ui.home.HomeScreen
import com.excavplayer.ui.home.HomeViewModel
import com.excavplayer.ui.player.PlayerScreen
import com.excavplayer.ui.player.PlayerViewModel
import com.excavplayer.ui.playlists.PlaylistDetailScreen
import com.excavplayer.ui.playlists.PlaylistsScreen
import com.excavplayer.ui.playlists.PlaylistsViewModel
import com.excavplayer.ui.search.SearchScreen
import com.excavplayer.ui.search.SearchViewModel
import com.excavplayer.ui.settings.SettingsScreen
import com.excavplayer.ui.settings.SettingsViewModel
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.excavBackground

class ExcavViewModels(
    val homeViewModel: HomeViewModel,
    val foldersViewModel: FoldersViewModel,
    val playlistsViewModel: PlaylistsViewModel,
    val historyViewModel: HistoryViewModel,
    val favoritesViewModel: FavoritesViewModel,
    val settingsViewModel: SettingsViewModel,
    val searchViewModel: SearchViewModel,
    val playerViewModel: PlayerViewModel
)

@Composable
fun ExcavAppScaffold(
    navController: NavHostController,
    viewModels: ExcavViewModels,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine current tab if on one of the 5 bottom navigation tabs
    val currentTab = when (currentRoute) {
        Screen.Home.route -> NavTab.HOME
        Screen.Folders.route -> NavTab.FOLDERS
        Screen.Playlists.route -> NavTab.PLAYLISTS
        Screen.Favorites.route -> NavTab.FAVORITES
        Screen.History.route -> NavTab.HISTORY
        else -> null
    }

    val isRootTab = currentTab != null
    val playerState by viewModels.playerViewModel.playerState.collectAsState()
    val hasActiveVideo = playerState.currentVideo != null
    val isPlayerScreen = currentRoute == Screen.Player.route

    Scaffold(
        modifier = modifier.fillMaxSize().excavBackground(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            if (isRootTab && !isPlayerScreen) {
                Column {
                    if (hasActiveVideo) {
                        MiniPlayer(
                            playerState = playerState,
                            onClick = {
                                navController.navigate(Screen.Player.route)
                            },
                            onPlayPauseClick = {
                                viewModels.playerViewModel.togglePlayPause()
                            },
                            onCloseClick = {
                                viewModels.playerViewModel.playerManager.stop()
                            }
                        )
                    }

                    ExcavBottomNav(
                        currentTab = currentTab,
                        onTabSelected = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModels.homeViewModel,
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onPlayVideo = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.Folders.route) {
                FoldersScreen(
                    viewModel = viewModels.foldersViewModel,
                    onFolderClick = { folder ->
                        val route = Screen.FolderDetail.createRoute(folder.path, folder.name)
                        navController.navigate(route)
                    },
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) }
                )
            }

            composable(
                route = Screen.FolderDetail.route,
                arguments = listOf(
                    navArgument("folderPath") { type = NavType.StringType },
                    navArgument("folderName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("folderPath") ?: ""
                val encodedName = backStackEntry.arguments?.getString("folderName") ?: ""
                val folderPath = android.net.Uri.decode(encodedPath)
                val folderName = android.net.Uri.decode(encodedName)

                FolderDetailScreen(
                    folderPath = folderPath,
                    folderName = folderName,
                    viewModel = viewModels.foldersViewModel,
                    onBackClick = { navController.popBackStack() },
                    onPlayVideo = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    viewModel = viewModels.playlistsViewModel,
                    onPlaylistClick = { playlist ->
                        val route = Screen.PlaylistDetail.createRoute(playlist.id, playlist.title)
                        navController.navigate(route)
                    },
                    onFavoritesClick = {
                        navController.navigate(Screen.Favorites.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) }
                )
            }

            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.LongType },
                    navArgument("playlistTitle") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                val encodedTitle = backStackEntry.arguments?.getString("playlistTitle") ?: ""
                val playlistTitle = android.net.Uri.decode(encodedTitle)

                PlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistTitle = playlistTitle,
                    viewModel = viewModels.playlistsViewModel,
                    onBackClick = { navController.popBackStack() },
                    onPlayVideo = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    viewModel = viewModels.favoritesViewModel,
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                    onPlayVideo = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModels.historyViewModel,
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                    onPlayVideo = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModels.settingsViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    viewModel = viewModels.searchViewModel,
                    onBackClick = { navController.popBackStack() },
                    onPlayVideo = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.Player.route) {
                PlayerScreen(
                    viewModel = viewModels.playerViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
