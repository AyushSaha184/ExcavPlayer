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
import com.excavplayer.ui.ExcavViewModel
import com.excavplayer.ui.navigation.ExcavApp
import com.excavplayer.ui.theme.ExcavTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.library.VideoLibrary
import com.excavplayer.media.source.SafDataSource
import com.excavplayer.media.thumbnail.ThumbnailLoader
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.playback.PipHelper
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

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        playerManager.dispatch(
            PlayerCommand.AddExternalSubtitle(
                uri = uri.toString(),
                label = uri.lastPathSegment.orEmpty(),
                mimeType = contentResolver.getType(uri) ?: "text/plain"
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel: ExcavViewModel by viewModels()
        setContent {
            ExcavTheme {
                ExcavApp(viewModel) {
                    subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream"))
                }
            }
        }

        checkAndRequestPermissions()
        handleIncomingIntent(intent)
        viewModel.checkForUpdates(isManualCheck = false)
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
