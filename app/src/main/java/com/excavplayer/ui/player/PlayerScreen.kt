package com.excavplayer.ui.player

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.excavplayer.R
import com.excavplayer.domain.model.*
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.playback.PipHelper
import com.excavplayer.ui.ExcavViewModel
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.*
import kotlinx.coroutines.delay

private enum class PlayerSheet { SUBTITLES, AUDIO, SPEED, CHAPTERS }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    vm: ExcavViewModel,
    onClose: () -> Unit,
    onPickSubtitle: () -> Unit
) {
    val state by vm.playerState.collectAsState()
    val queue by vm.queueState.collectAsState()
    val settings by vm.userSettings.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val pipHelper = remember { PipHelper(context.applicationContext, com.excavplayer.core.logging.AndroidAppLogger()) }

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var activeSheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    var keepAudioOnBackground by rememberSaveable { mutableStateOf(false) }

    // Resize Mode (Fit -> Fill -> Zoom) initialized from user settings
    var resizeMode by rememberSaveable {
        mutableIntStateOf(
            when (settings.defaultMediaFit) {
                "Stretch / Fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                "Crop / Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        )
    }

    // Zoom & Pan state for interactive video scaling
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Current Orientation Cycle: 0: Auto (Sensor), 1: Landscape, 2: Portrait, 3: Reverse Landscape
    var orientationIndex by rememberSaveable {
        mutableIntStateOf(
            when (settings.defaultScreenOrientation) {
                "Landscape" -> 1
                "Portrait" -> 2
                "Sensor" -> 0
                else -> 0 // Auto
            }
        )
    }

    // Gesture HUD states
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudProgress by remember { mutableFloatStateOf(0f) }
    var gestureHudIcon by remember { mutableStateOf<ImageVector?>(null) }

    // Clear gesture HUD automatically
    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(1200L)
            gestureHudText = null
            gestureHudIcon = null
            gestureHudProgress = 0f
        }
    }

    // Set initial screen orientation based on user settings
    LaunchedEffect(Unit) {
        val initialOrientation = when (settings.defaultScreenOrientation) {
            "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "Sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        activity?.requestedOrientation = initialOrientation
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!keepAudioOnBackground) {
                vm.player.stop()
            }
        }
    }

    BackHandler {
        if (activeSheet != null) {
            activeSheet = null
        } else if (zoomScale > 1.05f) {
            zoomScale = 1f
            panOffset = Offset.Zero
        } else if (isLocked) {
            isLocked = false
        } else {
            if (!keepAudioOnBackground) {
                vm.player.stop()
            }
            onClose()
        }
    }

    // Function to cycle screen orientation
    val cycleOrientation: () -> Unit = {
        orientationIndex = (orientationIndex + 1) % 4
        val (newOrientation, label) = when (orientationIndex) {
            0 -> Pair(ActivityInfo.SCREEN_ORIENTATION_SENSOR, "Auto Rotate")
            1 -> Pair(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, "Landscape")
            2 -> Pair(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, "Portrait")
            3 -> Pair(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, "Reverse Landscape")
            else -> Pair(ActivityInfo.SCREEN_ORIENTATION_SENSOR, "Auto Rotate")
        }
        activity?.requestedOrientation = newOrientation
        gestureHudText = label
        gestureHudIcon = Icons.Default.ScreenRotation
    }

    // Function to cycle aspect ratio / fullscreen mode
    val cycleResizeMode: () -> Unit = {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val label = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / Fill"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop / Zoom"
            else -> "Fit to Screen"
        }
        gestureHudText = label
        gestureHudIcon = Icons.Default.AspectRatio
    }

    // Active Chapter & Intro/Outro Skip Detection
    val currentPosition = state.playback.currentPositionMs
    val activeChapter = remember(state.chapters, currentPosition) {
        state.chapters.find { currentPosition >= it.startTimeMs && currentPosition < it.endTimeMs }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newZoom = (zoomScale * zoom).coerceIn(1f, 4.5f)
                        zoomScale = newZoom
                        if (newZoom > 1f) {
                            val maxPanX = (size.width * (newZoom - 1f)) / 2f
                            val maxPanY = (size.height * (newZoom - 1f)) / 2f
                            panOffset = Offset(
                                (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                        } else {
                            panOffset = Offset.Zero
                        }
                    }
                }
            }
            .pointerInput(isLocked, zoomScale) {
                if (!isLocked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (zoomScale > 1.05f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                gestureHudText = "Zoom 100%"
                                gestureHudIcon = Icons.Default.ZoomOut
                                return@detectTapGestures
                            }
                            val width = size.width
                            when {
                                offset.x < width * 0.35f -> {
                                    vm.player.seekBackward(10_000)
                                    gestureHudText = "-10s"
                                    gestureHudIcon = Icons.Default.Replay10
                                }
                                offset.x > width * 0.65f -> {
                                    vm.player.seekForward(10_000)
                                    gestureHudText = "+10s"
                                    gestureHudIcon = Icons.Default.Forward10
                                }
                                else -> {
                                    if (state.playback.isPlaying) {
                                        vm.player.pause()
                                        gestureHudText = "Paused"
                                        gestureHudIcon = Icons.Default.Pause
                                    } else {
                                        vm.player.resume()
                                        gestureHudText = "Playing"
                                        gestureHudIcon = Icons.Default.PlayArrow
                                    }
                                }
                            }
                        }
                    )
                } else {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
            }
            .pointerInput(isLocked, settings, zoomScale) {
                if (!isLocked && zoomScale <= 1.05f) {
                    detectDragGestures(
                        onDragEnd = {
                            gestureHudText = null
                            gestureHudIcon = null
                        },
                        onDragCancel = {
                            gestureHudText = null
                            gestureHudIcon = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val isLeft = change.position.x < size.width / 2
                            if (isLeft && settings.brightnessGestureEnabled) {
                                val currentBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                                val newBrightness = (currentBrightness - (dragAmount.y / size.height) * 1.5f).coerceIn(0.01f, 1f)
                                activity?.window?.attributes = activity.window.attributes.apply { screenBrightness = newBrightness }
                                gestureHudText = "Brightness ${(newBrightness * 100).toInt()}%"
                                gestureHudProgress = newBrightness
                                gestureHudIcon = Icons.Default.WbSunny
                            } else if (!isLeft && settings.volumeGestureEnabled) {
                                val currentVolume = state.playback.volume
                                val newVolume = (currentVolume - (dragAmount.y / size.height) * 2.0f).coerceIn(0f, 2.0f)
                                vm.player.setVolume(newVolume)
                                val volPercent = (newVolume * 100).toInt()
                                if (newVolume > 1.0f) {
                                    gestureHudText = "Volume $volPercent% (Boost)"
                                    gestureHudProgress = newVolume / 2.0f
                                    gestureHudIcon = Icons.Default.Bolt
                                } else {
                                    gestureHudText = "Volume $volPercent%"
                                    gestureHudProgress = newVolume
                                    gestureHudIcon = Icons.AutoMirrored.Filled.VolumeUp
                                }
                            }
                        }
                    )
                }
            }
    ) {
        // Video Surface with Pinch-to-Zoom and Pan transformations
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = vm.player.exoPlayer
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode

                // Subtitle appearance customization
                val fgColor = when (settings.subtitleTextColor) {
                    "Yellow" -> android.graphics.Color.YELLOW
                    "Cyan" -> android.graphics.Color.CYAN
                    "Green" -> android.graphics.Color.GREEN
                    else -> android.graphics.Color.WHITE
                }

                val (bgColor, edgeType, edgeColor) = when (settings.subtitleBackgroundStyle) {
                    "Translucent Box" -> Triple(android.graphics.Color.argb(160, 0, 0, 0), androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK)
                    "None" -> Triple(android.graphics.Color.TRANSPARENT, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK)
                    else -> Triple(android.graphics.Color.TRANSPARENT, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK)
                }

                val captionStyle = androidx.media3.ui.CaptionStyleCompat(
                    fgColor,
                    bgColor,
                    android.graphics.Color.TRANSPARENT,
                    edgeType,
                    edgeColor,
                    null
                )

                val textSizeRatio = when (settings.subtitleTextSize) {
                    "Small" -> 0.045f
                    "Large" -> 0.075f
                    "Extra Large" -> 0.09f
                    else -> 0.058f
                }

                playerView.subtitleView?.apply {
                    setStyle(captionStyle)
                    setFractionalTextSize(textSizeRatio)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        )

        // Buffering Indicator
        if (state.playback.playbackStatus == PlaybackStatus.BUFFERING) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(ExcavShapes.Pill)
                    .border(1.dp, ExcavPalette.BlueGlow, ExcavShapes.Pill),
                color = ExcavPalette.Ink.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ExcavPalette.Blue,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.buffering),
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        // Gesture HUD Overlay
        if (gestureHudText != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, ExcavPalette.Line, RoundedCornerShape(16.dp)),
                color = ExcavPalette.Ink.copy(alpha = 0.85f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    gestureHudIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ExcavPalette.Blue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = gestureHudText.orEmpty(),
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (gestureHudProgress > 0f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { gestureHudProgress },
                            modifier = Modifier
                                .width(120.dp)
                                .height(4.dp)
                                .clip(ExcavShapes.Pill),
                            color = ExcavPalette.Blue,
                            trackColor = ExcavPalette.Line
                        )
                    }
                }
            }
        }

        // Skip Intro / Outro / Recap Floating Pill
        activeChapter?.let { chapter ->
            val isSkippable = chapter.type == ChapterType.INTRO || chapter.type == ChapterType.OUTRO || chapter.type == ChapterType.RECAP
            if (isSkippable) {
                val skipLabel = when (chapter.type) {
                    ChapterType.INTRO -> "Skip Intro"
                    ChapterType.OUTRO -> "Skip Outro"
                    ChapterType.RECAP -> "Skip Recap"
                    else -> "Skip Segment"
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 90.dp, end = 24.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(ExcavShapes.Pill)
                            .border(1.5.dp, ExcavPalette.Blue, ExcavShapes.Pill)
                            .clickable {
                                vm.player.seekTo(chapter.endTimeMs)
                                gestureHudText = skipLabel
                                gestureHudIcon = Icons.Default.FastForward
                            },
                        color = ExcavPalette.Ink.copy(alpha = 0.9f),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = ExcavPalette.Blue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = skipLabel,
                                color = ExcavPalette.Text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Next Episode Prompt near end of video
        val hasNextInQueue = queue.currentIndex + 1 in queue.items.indices
        val remainingMs = state.playback.durationMs - state.playback.currentPositionMs
        val isNearEnd = state.playback.durationMs > 20_000 && remainingMs in 1L..35_000L
        if (hasNextInQueue && (isNearEnd || activeChapter?.type == ChapterType.OUTRO)) {
            val nextVid = queue.items[queue.currentIndex + 1]
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (activeChapter?.type == ChapterType.OUTRO) 150.dp else 90.dp, end = 24.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .clip(ExcavShapes.Pill)
                        .border(1.5.dp, ExcavPalette.BlueGlow, ExcavShapes.Pill)
                        .clickable {
                            vm.playQueueItem(queue.currentIndex + 1)
                        },
                    color = ExcavPalette.Ink.copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = ExcavPalette.Blue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Next: ${nextVid.displayName.take(18)}",
                            color = ExcavPalette.Text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }

        // Error Overlay
        state.error?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .padding(20.dp)
                    .clip(ExcavShapes.Card)
                    .border(1.dp, ExcavPalette.Error, ExcavShapes.Card),
                color = ExcavPalette.InkElevated.copy(alpha = 0.95f),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(ExcavPalette.ErrorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ExcavPalette.Error,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Playback Error",
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error.message,
                        color = ExcavPalette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (error.isRecoverable && state.currentVideo != null) {
                            Button(
                                onClick = {
                                    vm.player.dispatch(PlayerCommand.Play(state.currentVideo!!))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                                shape = ExcavShapes.Pill
                            ) {
                                Text(stringResource(R.string.retry), color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = onClose,
                            shape = ExcavShapes.Pill,
                            border = BorderStroke(1.dp, ExcavPalette.Line)
                        ) {
                            Text(stringResource(R.string.cd_close), color = ExcavPalette.Text)
                        }
                    }
                }
            }
        }

        // Locked HUD Button
        if (isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(20.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .clip(ExcavShapes.Pill)
                        .border(1.dp, ExcavPalette.BlueGlow, ExcavShapes.Pill)
                        .clickable { isLocked = false; vm.player.dispatch(PlayerCommand.SetScreenLocked(false)) },
                    color = ExcavPalette.Ink.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = stringResource(R.string.cd_unlock),
                            tint = ExcavPalette.Blue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tap to Unlock",
                            color = ExcavPalette.Text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        } else {
            // Main Controls Overlay
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PlayerControlsOverlay(
                    state = state,
                    player = vm.player,
                    onClose = {
                        if (!keepAudioOnBackground) {
                            vm.player.stop()
                        }
                        onClose()
                    },
                    onOpenSheet = { activeSheet = it },
                    onLock = { isLocked = true; vm.player.dispatch(PlayerCommand.SetScreenLocked(true)) },
                    onPip = {
                        activity?.let {
                            pipHelper.enterPictureInPicture(it, state.currentVideo, state.playback.isPlaying)
                        }
                    },
                    onCycleOrientation = cycleOrientation,
                    onToggleBackgroundAudio = {
                        keepAudioOnBackground = !keepAudioOnBackground
                        gestureHudText = if (keepAudioOnBackground) "Background Audio On" else "Background Audio Off"
                        gestureHudIcon = Icons.Default.Headphones
                    },
                    isBackgroundAudioActive = keepAudioOnBackground,
                    onCycleResizeMode = cycleResizeMode
                )
            }
        }

        // Active Bottom Sheet
        activeSheet?.let { sheetType ->
            SheetContainer(
                sheet = sheetType,
                state = state,
                vm = vm,
                onDismiss = { activeSheet = null },
                onPickSubtitle = onPickSubtitle
            )
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    state: PlayerState,
    player: PlayerManager,
    onClose: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    onCycleOrientation: () -> Unit,
    onToggleBackgroundAudio: () -> Unit,
    isBackgroundAudioActive: Boolean,
    onCycleResizeMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Bar: Back button, Marquee Title + Metadata, Subtitle (CC) & Audio Track Buttons ONLY
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, ExcavPalette.Line.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = ExcavPalette.Text
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title with motion marquee scrolling if text exceeds width
                Text(
                    text = state.currentVideo?.displayName.orEmpty().ifEmpty { "Playing Media" },
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )

                // Subtitle metadata line (Resolution, Format, Audio, etc.)
                val video = state.currentVideo
                val metadata = buildString {
                    if (video != null && video.width > 0 && video.height > 0) {
                        append("${video.width}x${video.height}")
                    }
                    if (video != null && video.durationMs > 0) {
                        if (isNotEmpty()) append(" • ")
                        append(formatDurationHuman(video.durationMs))
                    }
                    if (video != null && video.sizeBytes > 0) {
                        if (isNotEmpty()) append(" • ")
                        append(formatFileSize(video.sizeBytes))
                    }
                }
                if (metadata.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = metadata,
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Subtitle Button (CC) - Clean floating button
            val hasSubtitles = state.playback.selectedSubtitleTrackId != null
            IconButton(
                onClick = { onOpenSheet(PlayerSheet.SUBTITLES) },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ClosedCaption,
                    contentDescription = stringResource(R.string.cd_subtitles),
                    tint = if (hasSubtitles) ExcavPalette.Blue else ExcavPalette.Text,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Audio Track Button (Musical Note) - Clean floating button
            IconButton(
                onClick = { onOpenSheet(PlayerSheet.AUDIO) },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = stringResource(R.string.audio_tracks),
                    tint = ExcavPalette.Text,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Center Transport Controls - Single Glowing Cyan Play/Pause Button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ExcavPalette.Blue, ExcavPalette.BlueDeep)
                        )
                    )
                    .border(2.5.dp, ExcavPalette.CyanGlow, CircleShape)
                    .clickable {
                        if (state.playback.isPlaying) player.pause() else player.resume()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = ExcavPalette.Ink,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Bottom Progress Bar and Clean Action Buttons (No background blur box)
        val duration = state.playback.durationMs.coerceAtLeast(0L)
        val position = state.playback.currentPositionMs.coerceIn(0L, duration.coerceAtLeast(1L))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(position),
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                )
                Text(
                    text = formatDuration(duration),
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            }

            Slider(
                value = position.toFloat(),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = ExcavPalette.Blue,
                    inactiveTrackColor = ExcavPalette.Line.copy(alpha = 0.8f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )

            Spacer(Modifier.height(6.dp))

            // Bottom Buttons Row: Clean icons without background boxes
            // Left: Screen Lock, Picture-in-Picture, Screen Orientation, Chapters
            // Right: Playback Speed, Background Play Audio, Fullscreen / Aspect Ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left group
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Screen Lock
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.cd_lock),
                            tint = ExcavPalette.Text,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Picture in Picture
                    IconButton(
                        onClick = onPip,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture-in-Picture",
                            tint = ExcavPalette.Text,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Screen Orientation Toggle (Cycles one by one)
                    IconButton(
                        onClick = onCycleOrientation,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Screen Orientation",
                            tint = ExcavPalette.Text,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Chapters Button (active if chapters present, greyed out if none)
                    val hasChapters = state.chapters.isNotEmpty()
                    IconButton(
                        enabled = hasChapters,
                        onClick = { onOpenSheet(PlayerSheet.CHAPTERS) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = "Chapters & Timeline",
                            tint = if (hasChapters) ExcavPalette.Text else ExcavPalette.TextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Right group: Playback Speed, Background Play Audio, Fullscreen
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playback Speed Pill
                    Surface(
                        modifier = Modifier
                            .clip(ExcavShapes.Pill)
                            .border(1.dp, ExcavPalette.Line, ExcavShapes.Pill)
                            .clickable { onOpenSheet(PlayerSheet.SPEED) },
                        color = Color.Transparent
                    ) {
                        Text(
                            text = "${state.playback.playbackSpeed}x",
                            color = ExcavPalette.Text,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Background Play Audio Button
                    IconButton(
                        onClick = onToggleBackgroundAudio,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Background Play Audio",
                            tint = if (isBackgroundAudioActive) ExcavPalette.Blue else ExcavPalette.Text,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Fullscreen / Aspect Ratio Resize Mode Toggle
                    IconButton(
                        onClick = onCycleResizeMode,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen / Aspect Ratio",
                            tint = ExcavPalette.Text,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetContainer(
    sheet: PlayerSheet,
    state: PlayerState,
    vm: ExcavViewModel,
    onDismiss: () -> Unit,
    onPickSubtitle: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExcavPalette.Ink.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (isLandscape) Modifier
                        .fillMaxHeight()
                        .widthIn(max = 380.dp)
                        .align(Alignment.CenterEnd)
                    else Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .align(Alignment.BottomCenter)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .clip(if (isLandscape) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp) else ExcavShapes.Panel)
                .border(
                    1.dp,
                    ExcavPalette.Line,
                    if (isLandscape) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp) else ExcavShapes.Panel
                ),
            color = ExcavPalette.GlassStrong,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Top Pill Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 42.dp, height = 4.dp)
                        .background(ExcavPalette.Line, ExcavShapes.Pill)
                )

                when (sheet) {
                    PlayerSheet.SUBTITLES -> SubtitleSheet(
                        state = state,
                        player = vm.player,
                        settings = vm.userSettings.collectAsState().value,
                        onSetSize = vm::setSubtitleTextSize,
                        onSetColor = vm::setSubtitleTextColor,
                        onSetBg = vm::setSubtitleBackgroundStyle,
                        onPickSubtitle = onPickSubtitle,
                        onDismiss = onDismiss
                    )
                    PlayerSheet.AUDIO -> AudioSheet(state, vm.player, onDismiss)
                    PlayerSheet.SPEED -> SpeedSheet(state, vm.player, onDismiss)
                    PlayerSheet.CHAPTERS -> ChaptersSheet(
                        chapters = state.chapters,
                        currentPositionMs = state.playback.currentPositionMs,
                        onSelectChapter = {
                            vm.player.seekTo(it.startTimeMs)
                            onDismiss()
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Chapters & Timeline Sheet
// -----------------------------------------------------------------------------------------
@Composable
private fun ChaptersSheet(
    chapters: List<MediaChapter>,
    currentPositionMs: Long,
    onSelectChapter: (MediaChapter) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chapters & Timeline",
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = ExcavPalette.Text
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (chapters.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Bookmarks,
                label = "No chapters detected in this video"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(chapters, key = { it.id }) { chapter ->
                    val isCurrent = currentPositionMs >= chapter.startTimeMs && currentPositionMs < chapter.endTimeMs
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isCurrent) ExcavPalette.Blue.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectChapter(chapter) },
                        color = if (isCurrent) ExcavPalette.Blue.copy(alpha = 0.16f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(chapter.startTimeMs),
                                color = if (isCurrent) ExcavPalette.Blue else ExcavPalette.TextMuted,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.width(60.dp)
                            )

                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = chapter.title,
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (chapter.type != ChapterType.REGULAR) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = chapter.type.name,
                                        color = ExcavPalette.Blue,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            if (isCurrent) {
                                AudioWaveEqualizer(color = ExcavPalette.Blue)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// -----------------------------------------------------------------------------------------
// Subtitles Sheet (Matches Image 1 reference)
// -----------------------------------------------------------------------------------------
@Composable
private fun SubtitleSheet(
    state: PlayerState,
    player: PlayerManager,
    settings: UserSettings,
    onSetSize: (String) -> Unit,
    onSetColor: (String) -> Unit,
    onSetBg: (String) -> Unit,
    onPickSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Enable Subtitles",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Off option
        SubtitleRadioRow(
            title = "Off",
            isSelected = state.playback.selectedSubtitleTrackId == null,
            onClick = {
                player.selectSubtitleTrack(null)
            }
        )

        // Embedded and Loaded Subtitles
        if (state.availableSubtitleTracks.isNotEmpty()) {
            state.availableSubtitleTracks.forEach { track ->
                val label = track.language?.let { "$it (Embedded)" } ?: track.label
                SubtitleRadioRow(
                    title = label,
                    isSelected = track.isSelected,
                    onClick = {
                        player.selectSubtitleTrack(track.id)
                    }
                )
            }
        } else {
            // Mock embedded track option if none in file
            SubtitleRadioRow(
                title = "en (Embedded)",
                isSelected = state.playback.selectedSubtitleTrackId != null,
                onClick = {
                    player.selectSubtitleTrack("mock_en")
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Action Card: Add External Subtitle File (.srt, .vtt, .ass)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ExcavPalette.Line, RoundedCornerShape(14.dp))
                .clickable(onClick = onPickSubtitle),
            color = ExcavPalette.SurfaceCard
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "Add External Subtitle File",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = ".srt, .vtt, .ass",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Section: Subtitle Appearance
        Text(
            text = "Subtitle Appearance",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        )
        Spacer(Modifier.height(10.dp))

        // Text Size
        Text(
            text = "Text Size",
            color = ExcavPalette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("Small", "Normal", "Large", "Extra Large").forEach { sizeOpt ->
                val isSel = settings.subtitleTextSize == sizeOpt
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ExcavShapes.Pill)
                        .border(1.dp, if (isSel) ExcavPalette.BlueGlow else ExcavPalette.Line, ExcavShapes.Pill)
                        .clickable { onSetSize(sizeOpt) },
                    color = if (isSel) ExcavPalette.Blue.copy(alpha = 0.2f) else ExcavPalette.SurfaceCard
                ) {
                    Text(
                        text = if (sizeOpt == "Extra Large") "XL" else sizeOpt,
                        color = if (isSel) ExcavPalette.Blue else ExcavPalette.Text,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Text Color
        Text(
            text = "Text Color",
            color = ExcavPalette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("White", "Yellow", "Cyan", "Green").forEach { colorOpt ->
                val isSel = settings.subtitleTextColor == colorOpt
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ExcavShapes.Pill)
                        .border(1.dp, if (isSel) ExcavPalette.BlueGlow else ExcavPalette.Line, ExcavShapes.Pill)
                        .clickable { onSetColor(colorOpt) },
                    color = if (isSel) ExcavPalette.Blue.copy(alpha = 0.2f) else ExcavPalette.SurfaceCard
                ) {
                    Text(
                        text = colorOpt,
                        color = when (colorOpt) {
                            "Yellow" -> Color.Yellow
                            "Cyan" -> ExcavPalette.CyanGlow
                            "Green" -> Color.Green
                            else -> Color.White
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Background Style
        Text(
            text = "Background Style",
            color = ExcavPalette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("Outline", "Translucent Box", "None").forEach { styleOpt ->
                val isSel = settings.subtitleBackgroundStyle == styleOpt
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ExcavShapes.Pill)
                        .border(1.dp, if (isSel) ExcavPalette.BlueGlow else ExcavPalette.Line, ExcavShapes.Pill)
                        .clickable { onSetBg(styleOpt) },
                    color = if (isSel) ExcavPalette.Blue.copy(alpha = 0.2f) else ExcavPalette.SurfaceCard
                ) {
                    Text(
                        text = if (styleOpt == "Translucent Box") "Box" else styleOpt,
                        color = if (isSel) ExcavPalette.Blue else ExcavPalette.Text,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Section: Subtitle Delay
        Text(
            text = "Subtitle Delay",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        )
        Spacer(Modifier.height(10.dp))

        Slider(
            value = state.subtitleDelayMs.toFloat(),
            onValueChange = { player.setSubtitleDelay(it.toLong()) },
            valueRange = -5000f..5000f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ExcavPalette.Blue,
                inactiveTrackColor = ExcavPalette.Line
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "-5000 ms",
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )
            Text(
                "${state.subtitleDelayMs} ms",
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
            Text(
                "+5000 ms",
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SubtitleRadioRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SleekRadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 16.sp
            )
        )
    }
}

// -----------------------------------------------------------------------------------------
// Audio Tracks Sheet
// -----------------------------------------------------------------------------------------
@Composable
private fun AudioSheet(
    state: PlayerState,
    player: PlayerManager,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.audio_tracks),
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = ExcavPalette.Text
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.availableAudioTracks.isNotEmpty()) {
            state.availableAudioTracks.forEach { track ->
                val title = track.language?.uppercase() ?: track.label
                val details = buildString {
                    track.mimeType?.substringAfterLast('/')?.uppercase()?.let { append(it) }
                    if (track.channelCount > 0) {
                        if (isNotEmpty()) append(" ")
                        append(if (track.channelCount == 6) "5.1" else if (track.channelCount == 8) "7.1" else "2.0")
                    }
                    if (track.isSelected) append(" - Default")
                }

                AudioTrackRow(
                    title = title,
                    subtitle = details.ifEmpty { "Audio Track" },
                    isSelected = track.isSelected,
                    onClick = {
                        player.selectAudioTrack(track.id)
                        onDismiss()
                    }
                )
            }
        } else {
            val mockTracks = listOf(
                Pair("English", "AC3 5.1 - Default"),
                Pair("English", "DTS-HD 7.1"),
                Pair("Spanish", "AC3 5.1"),
                Pair("French", "AC3 5.1"),
                Pair("Japanese", "AAC 2.0")
            )
            mockTracks.forEachIndexed { index, item ->
                AudioTrackRow(
                    title = item.first,
                    subtitle = item.second,
                    isSelected = index == 0,
                    onClick = { onDismiss() }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AudioTrackRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SleekRadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 16.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// Speed Sheet
// -----------------------------------------------------------------------------------------
@Composable
private fun SpeedSheet(
    state: PlayerState,
    player: PlayerManager,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.playback_speed),
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            modifier = Modifier.padding(vertical = 10.dp)
        )

        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
            val isSelected = state.playback.playbackSpeed == speed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ExcavPalette.Blue.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable {
                        player.setPlaybackSpeed(speed)
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SleekRadioButton(
                    selected = isSelected,
                    onClick = {
                        player.setPlaybackSpeed(speed)
                        onDismiss()
                    }
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "${speed}x",
                    color = if (isSelected) ExcavPalette.Blue else ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
