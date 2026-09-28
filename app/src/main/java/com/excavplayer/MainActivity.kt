package com.excavplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.library.VideoLibrary
import com.excavplayer.media.source.SafDataSource
import com.excavplayer.media.thumbnail.ThumbnailLoader
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.playback.PipHelper
import com.excavplayer.ui.components.LocalThumbnailLoader
import com.excavplayer.ui.favorites.FavoritesViewModel
import com.excavplayer.ui.folders.FoldersViewModel
import com.excavplayer.ui.history.HistoryViewModel
import com.excavplayer.ui.home.HomeViewModel
import com.excavplayer.ui.navigation.ExcavAppScaffold
import com.excavplayer.ui.navigation.ExcavViewModels
import com.excavplayer.ui.navigation.Screen
import com.excavplayer.ui.player.PlayerViewModel
import com.excavplayer.ui.playlists.PlaylistsViewModel
import com.excavplayer.ui.search.SearchViewModel
import com.excavplayer.ui.settings.SettingsViewModel
import com.excavplayer.ui.theme.ExcavPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var videoLibrary: VideoLibrary

    @Inject
    lateinit var pipHelper: PipHelper

    @Inject
    lateinit var safDataSource: SafDataSource

    @Inject
    lateinit var thumbnailLoader: ThumbnailLoader

    @Inject
    lateinit var logger: AppLogger

    private val homeViewModel: HomeViewModel by viewModels()
    private val foldersViewModel: FoldersViewModel by viewModels()
    private val playlistsViewModel: PlaylistsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val favoritesViewModel: FavoritesViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            logger.i("MainActivity", "Permissions granted, triggering media sync")
            lifecycleScope.launch { videoLibrary.refresh() }
        } else {
            logger.w("MainActivity", "Some permissions were denied: $permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModels = ExcavViewModels(
            homeViewModel = homeViewModel,
            foldersViewModel = foldersViewModel,
            playlistsViewModel = playlistsViewModel,
            historyViewModel = historyViewModel,
            favoritesViewModel = favoritesViewModel,
            settingsViewModel = settingsViewModel,
            searchViewModel = searchViewModel,
            playerViewModel = playerViewModel
        )

        setContent {
            ExcavPlayerTheme {
                CompositionLocalProvider(LocalThumbnailLoader provides thumbnailLoader) {
                    val navController = rememberNavController()
                    ExcavAppScaffold(
                        navController = navController,
                        viewModels = viewModels
                    )
                }
            }
        }

        checkAndRequestPermissions()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val dataUri = intent.data ?: return
            logger.i("MainActivity", "Handling external VIEW intent for: $dataUri")
            lifecycleScope.launch {
                val entity = safDataSource.registerDocumentUri(dataUri)
                if (entity != null) {
                    val video = videoLibrary.observeVideos().first().find { it.id == entity.id }
                    if (video != null) {
                        playerManager.play(video)
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            lifecycleScope.launch { videoLibrary.refresh() }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerManager.dispatch(PlayerCommand.SetInPictureInPicture(isInPictureInPictureMode))
    }
}
