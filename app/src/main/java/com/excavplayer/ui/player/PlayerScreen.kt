package com.excavplayer.ui.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import com.excavplayer.ui.components.darkUltraThinBlur
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.excavplayer.R
import com.excavplayer.domain.model.*
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.playback.PipHelper
import com.excavplayer.ui.ExcavViewModel
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing

private enum class PlayerSheet { SUBTITLES, AUDIO, SPEED, CHAPTERS }

@Composable
private fun Modifier.playerGlass(
    shape: Shape = CircleShape,
    fallbackBg: Color = Color(0xA6141822)
): Modifier = this.glassmorphicBlur(
    shape = shape,
    backgroundColor = fallbackBg,
    strokeColor = Color.White.copy(alpha = 0.20f),
    strokeWidth = 0.5.dp
)

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

    var lastDoubleTapSeekTime by remember { mutableLongStateOf(0L) }
    var activeSurfaceView by remember { mutableStateOf<android.view.SurfaceView?>(null) }

    // When returning to video player, reset background audio toggle state so button is OFF
    LaunchedEffect(Unit) {
        keepAudioOnBackground = false
        vm.player.stopBackgroundPlay()
    }

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
    }

    // Reset orientation & surface frame rate on disposal
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val surf = activeSurfaceView?.holder?.surface
                    if (surf != null && surf.isValid) {
                        surf.setFrameRate(0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Handle Back Navigation
    BackHandler {
        if (activeSheet != null) {
            activeSheet = null
        } else if (zoomScale > 1.05f) {
            zoomScale = 1f
            panOffset = Offset.Zero
        } else if (isLocked) {
            isLocked = false
        } else {
            if (!keepAudioOnBackground && !state.isBackgroundAudio) {
                vm.player.stop()
            }
            onClose()
        }
    }

    // Function to cycle screen orientation
    val cycleOrientation: () -> Unit = {
        orientationIndex = (orientationIndex + 1) % 4
        val newOrientation = when (orientationIndex) {
            0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            3 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        activity?.requestedOrientation = newOrientation
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

    val isPip = (activity?.isInPictureInPictureMode == true) || state.isInPictureInPicture

    if (isPip) {
        // Pure Video Surface in Picture-in-Picture mode with default android controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = vm.player.exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { playerView ->
                    playerView.player = vm.player.exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
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
                                    lastDoubleTapSeekTime = android.os.SystemClock.uptimeMillis()
                                    vm.player.seekBackward(10_000)
                                }
                                offset.x > width * 0.65f -> {
                                    lastDoubleTapSeekTime = android.os.SystemClock.uptimeMillis()
                                    vm.player.seekForward(10_000)
                                }
                                else -> {
                                    if (state.playback.isPlaying) {
                                        vm.player.pause()
                                    } else {
                                        vm.player.resume()
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
                    this.player = vm.player.exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.resizeMode = resizeMode
                    subtitleView?.apply {
                        setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_CANVAS)
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setBottomPaddingFraction(0.08f)
                    }
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
                playerView.subtitleView?.apply {
                    setBottomPaddingFraction(0.08f)
                }

                // Display Refresh Rate Optimization (Judder-Free Playback)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
                    activeSurfaceView = surfaceView
                    val surface = surfaceView?.holder?.surface
                    if (surface != null && surface.isValid) {
                        val fps = vm.player.exoPlayer.videoFormat?.frameRate ?: 0f
                        if (settings.matchDisplayRefreshRate && fps > 1.0f) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                surface.setFrameRate(
                                    fps,
                                    android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                                    android.view.Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                                )
                            } else {
                                surface.setFrameRate(
                                    fps,
                                    android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                                )
                            }
                        } else {
                            surface.setFrameRate(0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                        }
                    }
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

        // Gesture HUD Overlay - frosted glass box for volume and brightness gestures
        if (gestureHudText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .playerGlass(RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    gestureHudIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ExcavPalette.Blue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = gestureHudText.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    )
                    if (gestureHudProgress > 0f) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { gestureHudProgress },
                            modifier = Modifier
                                .width(120.dp)
                                .height(4.dp)
                                .clip(ExcavShapes.Pill),
                            color = ExcavPalette.Blue,
                            trackColor = Color.White.copy(alpha = 0.25f)
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
                            .border(1.5.dp, ExcavPalette.BlueGlow, ExcavShapes.Pill)
                            .clickable {
                                vm.player.seekTo(chapter.endTimeMs)
                                gestureHudText = skipLabel
                                gestureHudIcon = Icons.Default.FastForward
                            },
                        color = ExcavPalette.Ink.copy(alpha = 0.95f),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = ExcavPalette.Blue,
                                modifier = Modifier.size(20.dp)
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

        // Floating Next Episode Pill
        val hasNextInQueue = queue.currentIndex in 0 until (queue.items.size - 1)
        val remainingMs = state.playback.durationMs - state.playback.currentPositionMs
        val isNearEnd = state.playback.durationMs > 20_000 && remainingMs in 0L..45_000L
        val isAtEnd = state.playback.playbackStatus == PlaybackStatus.ENDED
        if (hasNextInQueue && (isNearEnd || isAtEnd || activeChapter?.type == ChapterType.OUTRO)) {
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
                        textAlign = TextAlign.Center,
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

        // Locked HUD Button (matches bottom-left lock button position)
        if (isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = {
                        isLocked = false
                        vm.player.dispatch(PlayerCommand.SetScreenLocked(false))
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.cd_unlock),
                        tint = ExcavPalette.Blue,
                        modifier = Modifier.size(22.dp)
                    )
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
                        if (!keepAudioOnBackground && !state.isBackgroundAudio) {
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
                    orientationIndex = orientationIndex,
                    onToggleBackgroundAudio = {
                        keepAudioOnBackground = true
                        vm.player.startBackgroundPlay()
                        vm.showMessage("Playing audio in background")
                        onClose()
                    },
                    isBackgroundAudioActive = state.isBackgroundAudio || keepAudioOnBackground,
                    onToggleDialogueBoost = {
                        val next = !settings.dialogueBoostEnabled
                        vm.setDialogueBoost(next)
                        gestureHudProgress = 0f
                        gestureHudText = if (next) "Dialogue Boost: On" else "Dialogue Boost: Off"
                        gestureHudIcon = Icons.Default.RecordVoiceOver
                    },
                    isDialogueBoostActive = settings.dialogueBoostEnabled,
                    onCycleResizeMode = cycleResizeMode,
                    hasPrevious = queue.currentIndex > 0,
                    onPlayPrevious = { vm.playQueueItem(queue.currentIndex - 1) },
                    hasNext = queue.currentIndex in 0 until (queue.items.size - 1),
                    onPlayNext = { vm.playQueueItem(queue.currentIndex + 1) }
                )
            }
        }

        // Active Bottom Sheet with smooth entering and exiting animation
        AnimatedVisibility(
            visible = activeSheet != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                   scaleOut(targetScale = 0.92f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
        ) {
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
}

@Composable
private fun PlayerControlsOverlay(
    state: PlayerState,
    player: PlayerManager,
    onClose: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    orientationIndex: Int,
    onCycleOrientation: () -> Unit,
    onToggleBackgroundAudio: () -> Unit,
    isBackgroundAudioActive: Boolean,
    onToggleDialogueBoost: () -> Unit,
    isDialogueBoostActive: Boolean,
    onCycleResizeMode: () -> Unit,
    hasPrevious: Boolean,
    onPlayPrevious: () -> Unit,
    hasNext: Boolean,
    onPlayNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Bar: Back button, Marquee Title, Dialogue Booster, Subtitle (CC), Audio Track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .playerGlass(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentVideo?.displayName.orEmpty().ifEmpty { "Playing Media" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            Spacer(Modifier.width(12.dp))

            // Dialogue Booster Button
            IconButton(
                onClick = onToggleDialogueBoost,
                modifier = Modifier
                    .size(44.dp)
                    .playerGlass(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Dialogue Booster",
                    tint = if (isDialogueBoostActive) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Subtitle Button (CC)
            val hasSubtitles = state.playback.selectedSubtitleTrackId != null
            IconButton(
                onClick = { onOpenSheet(PlayerSheet.SUBTITLES) },
                modifier = Modifier
                    .size(44.dp)
                    .playerGlass(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ClosedCaption,
                    contentDescription = stringResource(R.string.cd_subtitles),
                    tint = if (hasSubtitles) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Audio Track Button
            IconButton(
                onClick = { onOpenSheet(PlayerSheet.AUDIO) },
                modifier = Modifier
                    .size(44.dp)
                    .playerGlass(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = stringResource(R.string.audio_tracks),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Center Transport Controls - Frosted Glass Previous, Play/Pause, Next Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = hasPrevious,
                onClick = onPlayPrevious,
                modifier = Modifier
                    .size(50.dp)
                    .playerGlass(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Video",
                    tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(28.dp))

            IconButton(
                onClick = {
                    if (state.playback.isPlaying) player.pause() else player.resume()
                },
                modifier = Modifier
                    .size(64.dp)
                    .playerGlass(CircleShape)
            ) {
                Icon(
                    imageVector = if (state.playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.width(28.dp))

            IconButton(
                enabled = hasNext,
                onClick = onPlayNext,
                modifier = Modifier
                    .size(50.dp)
                    .playerGlass(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Video",
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Bottom Progress Bar and Clean Action Buttons
        val duration = state.playback.durationMs.coerceAtLeast(0L)
        val position = state.playback.currentPositionMs.coerceIn(0L, duration.coerceAtLeast(1L))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(position),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
                Text(
                    text = formatDuration(duration),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)
                )
            }

            Spacer(Modifier.height(4.dp))

            ExcavSleekSlider(
                value = position.toFloat(),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Bottom Floating Glass Pills:
            // Left: Screen Lock, Picture-in-Picture, Screen Orientation, Chapters
            // Right: Playback Speed, Background Play Audio, Fullscreen / Aspect Ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Pill
                Row(
                    modifier = Modifier
                        .playerGlass(RoundedCornerShape(32.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Screen Lock
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.cd_lock),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Picture in Picture
                    IconButton(
                        onClick = onPip,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture-in-Picture",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Screen Orientation Toggle with Animated Icon Transition
                    IconButton(
                        onClick = onCycleOrientation,
                        modifier = Modifier.size(40.dp)
                    ) {
                        AnimatedContent(
                            targetState = orientationIndex,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                 scaleIn(initialScale = 0.65f, animationSpec = tween(220, easing = FastOutSlowInEasing)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 0.65f, animationSpec = tween(160, easing = FastOutSlowInEasing))
                                )
                            },
                            label = "OrientationIconAnimation"
                        ) { targetIndex ->
                            val icon = when (targetIndex) {
                                0 -> Icons.Default.ScreenRotation
                                1 -> Icons.Default.StayCurrentLandscape
                                2 -> Icons.Default.StayCurrentPortrait
                                3 -> Icons.Default.StayCurrentLandscape
                                else -> Icons.Default.ScreenRotation
                            }
                            val desc = when (targetIndex) {
                                0 -> "Auto Rotate"
                                1 -> "Landscape"
                                2 -> "Portrait"
                                3 -> "Reverse Landscape"
                                else -> "Auto Rotate"
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = desc,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Chapters Button
                    val hasChapters = state.chapters.isNotEmpty()
                    IconButton(
                        enabled = hasChapters,
                        onClick = { onOpenSheet(PlayerSheet.CHAPTERS) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = "Chapters & Timeline",
                            tint = if (hasChapters) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Right Pill: Playback Speed, Background Play Audio, Fullscreen
                Row(
                    modifier = Modifier
                        .playerGlass(RoundedCornerShape(32.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playback Speed Pill Button
                    Box(
                        modifier = Modifier
                            .clickable { onOpenSheet(PlayerSheet.SPEED) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${state.playback.playbackSpeed}x",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }

                    // Background Play Audio Toggle
                    IconButton(
                        onClick = onToggleBackgroundAudio,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Background Play Audio",
                            tint = if (isBackgroundAudioActive) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Fullscreen / Aspect Ratio Cycle
                    IconButton(
                        onClick = onCycleResizeMode,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Fullscreen & Aspect Ratio",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Bottom Sheet Containers
// -----------------------------------------------------------------------------------------
@Composable
private fun SheetContainer(
    sheet: PlayerSheet,
    state: PlayerState,
    vm: ExcavViewModel,
    onDismiss: () -> Unit,
    onPickSubtitle: () -> Unit
) {
    val settings by vm.userSettings.collectAsState()

    // Alignment and padding matching trigger button locations:
    // Subtitles & Audio -> Top bar on right (Alignment.TopEnd)
    // Speed -> Bottom bar on right (Alignment.BottomEnd)
    // Chapters -> Bottom bar on left (Alignment.BottomStart)
    val alignment = when (sheet) {
        PlayerSheet.SUBTITLES, PlayerSheet.AUDIO -> Alignment.TopEnd
        PlayerSheet.SPEED -> Alignment.BottomEnd
        PlayerSheet.CHAPTERS -> Alignment.BottomStart
    }

    val containerPadding = when (sheet) {
        PlayerSheet.SUBTITLES, PlayerSheet.AUDIO -> PaddingValues(top = 56.dp, end = 16.dp, bottom = 16.dp, start = 16.dp)
        PlayerSheet.SPEED -> PaddingValues(bottom = 76.dp, end = 16.dp, top = 16.dp, start = 16.dp)
        PlayerSheet.CHAPTERS -> PaddingValues(bottom = 76.dp, start = 16.dp, top = 16.dp, end = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.60f))
            .clickable(onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(containerPadding),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = false) {}
                .widthIn(
                    min = 280.dp,
                    max = when (sheet) {
                        PlayerSheet.SUBTITLES -> 360.dp
                        PlayerSheet.CHAPTERS -> 380.dp
                        PlayerSheet.AUDIO -> 340.dp
                        PlayerSheet.SPEED -> 320.dp
                    }
                )
                .darkUltraThinBlur(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0xF20E121B),
                    strokeColor = Color.White.copy(alpha = 0.20f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                when (sheet) {
                    PlayerSheet.SUBTITLES -> SubtitleSheet(
                        state = state,
                        player = vm.player,
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
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Chapters & Timeline",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (chapters.isEmpty()) {
            Text(
                text = "No chapters detected in this media.",
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            chapters.forEach { chapter ->
                val isCurrent = currentPositionMs >= chapter.startTimeMs && currentPositionMs < chapter.endTimeMs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectChapter(chapter) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SleekRadioButton(
                        selected = isCurrent,
                        onClick = { onSelectChapter(chapter) }
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = chapter.title,
                            color = if (isCurrent) Color.White else ExcavPalette.Text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${formatDuration(chapter.startTimeMs)} - ${formatDuration(chapter.endTimeMs)}",
                            color = ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                        )
                    }
                    if (isCurrent) {
                        Text(
                            text = "Playing",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Subtitles Sheet (Track Selection, External Subtitles & Delay)
// -----------------------------------------------------------------------------------------
@Composable
private fun SubtitleSheet(
    state: PlayerState,
    player: PlayerManager,
    onPickSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Subtitles",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Off option
        SubtitleRadioRow(
            title = "Off",
            isSelected = state.playback.selectedSubtitleTrackId == null,
            onClick = { player.selectSubtitleTrack(null) }
        )

        // Embedded and Loaded Subtitles
        if (state.availableSubtitleTracks.isNotEmpty()) {
            state.availableSubtitleTracks.forEach { track ->
                val label = track.language?.let { "$it (Embedded)" } ?: track.label
                SubtitleRadioRow(
                    title = label,
                    isSelected = track.isSelected,
                    onClick = { player.selectSubtitleTrack(track.id) }
                )
            }
        } else {
            Text(
                text = "No embedded subtitles found",
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Action Card: Add External Subtitle File (.srt, .vtt, .ass)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ExcavPalette.Line, RoundedCornerShape(12.dp))
                .clickable(onClick = onPickSubtitle),
            color = ExcavPalette.SurfaceCard
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = "Add External Subtitle File",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ".srt, .vtt, .ass",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Section: Subtitle Delay
        Text(
            text = "Subtitle Delay",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        )
        Spacer(Modifier.height(6.dp))

        ExcavSleekSlider(
            value = state.subtitleDelayMs.toFloat(),
            onValueChange = { rawVal ->
                val snapped = if (kotlin.math.abs(rawVal) < 200f) 0L else rawVal.toLong()
                player.setSubtitleDelay(snapped)
            },
            valueRange = -5000f..5000f,
            showZeroMarker = true,
            startLabel = "-5000 ms",
            centerLabel = if (state.subtitleDelayMs > 0) "+${state.subtitleDelayMs} ms" else "${state.subtitleDelayMs} ms",
            endLabel = "+5000 ms"
        )

        Spacer(Modifier.height(12.dp))
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
            color = if (isSelected) Color.White else ExcavPalette.Text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Audio Tracks",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (state.availableAudioTracks.isNotEmpty()) {
            state.availableAudioTracks.forEach { track ->
                val label = track.language ?: track.label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { player.selectAudioTrack(track.id) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SleekRadioButton(
                        selected = track.isSelected,
                        onClick = { player.selectAudioTrack(track.id) }
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = label,
                        color = if (track.isSelected) Color.White else ExcavPalette.Text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (track.isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SleekRadioButton(selected = true, onClick = {})
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "Default Audio",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

// -----------------------------------------------------------------------------------------
// Playback Speed Sheet
// -----------------------------------------------------------------------------------------
@Composable
private fun SpeedSheet(
    state: PlayerState,
    player: PlayerManager,
    onDismiss: () -> Unit
) {
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Playback Speed",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        speedOptions.forEach { speed ->
            val isSelected = kotlin.math.abs(state.playback.playbackSpeed - speed) < 0.01f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        player.setPlaybackSpeed(speed)
                        onDismiss()
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
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
                    text = "${speed}x" + if (speed == 1.0f) " (Normal)" else "",
                    color = if (isSelected) Color.White else ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

