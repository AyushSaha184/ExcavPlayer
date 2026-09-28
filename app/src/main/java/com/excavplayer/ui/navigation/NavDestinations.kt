package com.excavplayer.ui.navigation

enum class NavTab(val title: String, val route: String) {
    HOME("Home", "tab_home"),
    FOLDERS("Folders", "tab_folders"),
    PLAYLISTS("Playlists", "tab_playlists"),
    FAVORITES("Favorites", "tab_favorites"),
    HISTORY("History", "tab_history")
}

sealed class Screen(val route: String) {
    data object Home : Screen(NavTab.HOME.route)
    data object Folders : Screen(NavTab.FOLDERS.route)
    data object Playlists : Screen(NavTab.PLAYLISTS.route)
    data object Favorites : Screen(NavTab.FAVORITES.route)
    data object History : Screen(NavTab.HISTORY.route)

    data object Settings : Screen("screen_settings")
    data object Search : Screen("screen_search")
    data object Player : Screen("screen_player")

    data object FolderDetail : Screen("folder_detail/{folderPath}/{folderName}") {
        fun createRoute(folderPath: String, folderName: String): String {
            val encodedPath = android.net.Uri.encode(folderPath)
            val encodedName = android.net.Uri.encode(folderName)
            return "folder_detail/$encodedPath/$encodedName"
        }
    }

    data object PlaylistDetail : Screen("playlist_detail/{playlistId}/{playlistTitle}") {
        fun createRoute(playlistId: Long, playlistTitle: String): String {
            val encodedTitle = android.net.Uri.encode(playlistTitle)
            return "playlist_detail/$playlistId/$encodedTitle"
        }
    }
}
