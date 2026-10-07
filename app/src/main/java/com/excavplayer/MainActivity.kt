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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.PowerManager
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.mapper.toDomain
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.library.VideoLibrary
import com.excavplayer.media.source.SafDataSource
import com.excavplayer.media.thumbnail.ThumbnailLoader
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.playback.PipHelper
import com.excavplayer.ui.DeleteRequestEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
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
    lateinit var seekPreviewLoader: com.excavplayer.media.thumbnail.SeekPreviewLoader

    @Inject
    lateinit var logger: AppLogger

    private var pendingDeleteIds: List<String> = emptyList()

    private val deleteMediaLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onDeleteConfirmed(pendingDeleteIds)
        }
        pendingDeleteIds = emptyList()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val state = playerManager.state.value
                val stopOnScreenOff = viewModel.userSettings.value.stopOnScreenOff
                if (!state.isBackgroundAudio && stopOnScreenOff) {
                    logger.i("MainActivity", "Screen turned off while background audio is disabled and stopOnScreenOff is enabled -> pausing playback")
                    playerManager.pause()
                } else {
                    logger.i("MainActivity", "Screen turned off (isBackgroundAudio=${state.isBackgroundAudio}, stopOnScreenOff=$stopOnScreenOff) -> continuing playback")
                }
            }
        }
    }

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

    private val viewModel: ExcavViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState != null) {
            pendingDeleteIds = savedInstanceState.getStringArrayList("KEY_PENDING_DELETE_IDS")?.toList() ?: emptyList()
        }

        try {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            logger.w("MainActivity", "Failed to register screenOffReceiver: ${e.message}")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteRequestEvents.collect { event ->
                    when (event) {
                        is DeleteRequestEvent.MediaStoreDelete -> {
                            pendingDeleteIds = event.videoIds
                            deleteMediaLauncher.launch(
                                IntentSenderRequest.Builder(event.intentSender).build()
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.isPlayerOpen,
                    playerManager.state
                ) { isPlayerOpen, playerState ->
                    Pair(isPlayerOpen, playerState)
                }.collect { (isPlayerOpen, playerState) ->
                    val shouldAutoPip = isPlayerOpen &&
                            playerState.currentVideo != null &&
                            playerState.playback.isPlaying
                    pipHelper.updateAutoPipParams(
                        activity = this@MainActivity,
                        video = playerState.currentVideo,
                        isPlaying = playerState.playback.isPlaying,
                        autoEnter = shouldAutoPip
                    )
                }
            }
        }

        setContent {
            ExcavTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.excavplayer.ui.components.LocalThumbnailLoader provides thumbnailLoader,
                    com.excavplayer.ui.components.LocalSeekPreviewLoader provides seekPreviewLoader
                ) {
                    ExcavApp(viewModel) {
                        subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream"))
                    }
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
                    val allVideos = videoLibrary.observeVideos().first()
                    val video = allVideos.find { it.id == entity.id } ?: entity.toDomain()
                    viewModel.play(video)
                    viewModel.setPlayerOpen(true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkHasStoragePermission()) {
            lifecycleScope.launch { videoLibrary.refresh() }
        }
    }

    private fun checkHasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) return true
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
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

    override fun onStop() {
        super.onStop()
        val state = playerManager.state.value
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenOff = powerManager?.isInteractive == false
        val isPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPictureInPictureMode || state.isInPictureInPicture
        } else {
            state.isInPictureInPicture
        }

        // If screen turned off (in normal or PiP mode), pause unless background audio is enabled
        if (isScreenOff) {
            if (!state.isBackgroundAudio) {
                playerManager.pause()
            }
        } else if (!isPip && !state.isBackgroundAudio) {
            // Screen is still on, app sent to background without PiP
            playerManager.pause()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pendingDeleteIds.isNotEmpty()) {
            outState.putStringArrayList("KEY_PENDING_DELETE_IDS", ArrayList(pendingDeleteIds))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
    }
}
