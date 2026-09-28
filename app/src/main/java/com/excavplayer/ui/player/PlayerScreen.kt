package com.excavplayer.ui.player

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.excavplayer.ui.components.VideoDetailsDialog
import com.excavplayer.ui.components.VideoOptionsSheet
import com.excavplayer.ui.player.sheets.AudioTracksBottomSheet
import com.excavplayer.ui.player.sheets.PlaybackSpeedBottomSheet
import com.excavplayer.ui.player.sheets.QueueBottomSheet
import com.excavplayer.ui.player.sheets.SubtitlesBottomSheet
import com.excavplayer.ui.theme.BackgroundDark

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val playerState by viewModel.playerState.collectAsState()
    val queueState by viewModel.queueState.collectAsState()
    val currentSheet by viewModel.currentSheet.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val isLocked by viewModel.isScreenLocked.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val showBrightnessIndicator by viewModel.showBrightnessIndicator.collectAsState()
    val showVolumeIndicator by viewModel.showVolumeIndicator.collectAsState()

    // Handle back button: if sheet open, close sheet; else navigate back
    BackHandler {
        if (currentSheet != PlayerSheet.NONE) {
            viewModel.closeSheet()
        } else {
            onNavigateBack()
        }
    }

    // Update screen brightness on window
    LaunchedEffect(brightness) {
        activity?.let {
            val lp = it.window.attributes
            lp.screenBrightness = brightness
            it.window.attributes = lp
        }
    }

    // Reset window brightness when leaving player
    DisposableEffect(Unit) {
        onDispose {
            activity?.let {
                val lp = it.window.attributes
                lp.screenBrightness = -1f // reset to system default
                it.window.attributes = lp
            }
        }
    }

    // External Subtitle Picker
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addExternalSubtitle(uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = {
                        viewModel.toggleControlsVisibility()
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                viewModel.seekRelative(-10000L)
                            } else {
                                viewModel.seekRelative(10000L)
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val screenWidth = size.width
                        val delta = -dragAmount / 800f
                        if (change.position.x < screenWidth / 2) {
                            viewModel.adjustBrightness(delta)
                        } else {
                            viewModel.adjustVolume(delta)
                        }
                    }
                }
            }
    ) {
        // Player Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = viewModel.playerManager.exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                }
            },
            update = { playerView ->
                playerView.player = viewModel.playerManager.exoPlayer
                playerView.resizeMode = when (resizeMode) {
                    ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    ResizeMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Side Vertical Gesture Sliders (Brightness on left, Volume on right)
        VerticalGestureSlider(
            value = brightness,
            icon = Icons.Default.BrightnessHigh,
            visible = showBrightnessIndicator,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        )

        VerticalGestureSlider(
            value = volume,
            icon = Icons.Default.VolumeUp,
            visible = showVolumeIndicator,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        )

        // Fullscreen Controls Overlay
        PlayerControlsOverlay(
            playerState = playerState,
            visible = controlsVisible && currentSheet == PlayerSheet.NONE,
            isLocked = isLocked,
            onToggleLock = { viewModel.toggleLock() },
            onBackClick = onNavigateBack,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onSeekBackward = { viewModel.seekRelative(-10000L) },
            onSeekForward = { viewModel.seekRelative(10000L) },
            onPreviousClick = { viewModel.playPrevious() },
            onNextClick = { viewModel.playNext() },
            onSeekTo = { viewModel.seekTo(it) },
            onPipClick = { activity?.let { viewModel.enterPip(it) } },
            onAspectRatioClick = { viewModel.cycleResizeMode() },
            onSpeedClick = { viewModel.openSheet(PlayerSheet.SPEED) },
            onSubtitlesClick = { viewModel.openSheet(PlayerSheet.SUBTITLES) },
            onAudioTracksClick = { viewModel.openSheet(PlayerSheet.AUDIO) },
            onQueueClick = { viewModel.openSheet(PlayerSheet.QUEUE) },
            onOptionsClick = { viewModel.openSheet(PlayerSheet.OPTIONS) }
        )

        // Bottom Sheets
        when (currentSheet) {
            PlayerSheet.SPEED -> {
                PlaybackSpeedBottomSheet(
                    currentSpeed = playerState.playback.playbackSpeed,
                    onSpeedSelected = { viewModel.setSpeed(it) },
                    onDismiss = { viewModel.closeSheet() }
                )
            }
            PlayerSheet.AUDIO -> {
                AudioTracksBottomSheet(
                    tracks = playerState.availableAudioTracks,
                    selectedTrackId = playerState.playback.selectedAudioTrackId,
                    onTrackSelected = { viewModel.selectAudioTrack(it) },
                    onDismiss = { viewModel.closeSheet() }
                )
            }
            PlayerSheet.SUBTITLES -> {
                SubtitlesBottomSheet(
                    tracks = playerState.availableSubtitleTracks,
                    selectedTrackId = playerState.playback.selectedSubtitleTrackId,
                    subtitlesEnabled = playerState.playback.selectedSubtitleTrackId != null,
                    subtitleDelayMs = playerState.subtitleDelayMs,
                    onToggleSubtitlesEnabled = { enabled ->
                        if (!enabled) {
                            viewModel.selectSubtitleTrack(null)
                        } else {
                            val first = playerState.availableSubtitleTracks.firstOrNull()?.id
                            viewModel.selectSubtitleTrack(first)
                        }
                    },
                    onTrackSelected = { viewModel.selectSubtitleTrack(it) },
                    onAddExternalSubtitleClick = {
                        subtitlePickerLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                    },
                    onSubtitleDelayChanged = { viewModel.setSubtitleDelay(it) },
                    onDismiss = { viewModel.closeSheet() }
                )
            }
            PlayerSheet.QUEUE -> {
                QueueBottomSheet(
                    queueState = queueState,
                    currentVideo = playerState.currentVideo,
                    isPlaying = playerState.playback.isPlaying,
                    onVideoSelected = { viewModel.playQueueIndex(it) },
                    onMoveItem = { from, to -> viewModel.playerManager.queue.moveItem(from, to) },
                    onRemoveItem = { viewModel.playerManager.queue.removeAt(it) },
                    onDismiss = { viewModel.closeSheet() }
                )
            }
            PlayerSheet.OPTIONS -> {
                playerState.currentVideo?.let { video ->
                    VideoOptionsSheet(
                        video = video,
                        isFavorite = video.isFavorite,
                        onDismiss = { viewModel.closeSheet() },
                        onPlay = { viewModel.togglePlayPause() },
                        onPlayNext = { viewModel.playerManager.queue.addVideo(video) },
                        onAddToPlaylist = { /* Added to playlist */ },
                        onToggleFavorite = { viewModel.toggleFavorite(video.id) },
                        onShowDetails = { viewModel.openSheet(PlayerSheet.DETAILS) },
                        onDelete = { viewModel.closeSheet() }
                    )
                }
            }
            PlayerSheet.DETAILS -> {
                playerState.currentVideo?.let { video ->
                    VideoDetailsDialog(
                        video = video,
                        onDismiss = { viewModel.closeSheet() }
                    )
                }
            }
            PlayerSheet.NONE -> {}
        }
    }
}
