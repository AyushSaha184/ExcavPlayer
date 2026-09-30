package com.excavplayer.ui.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    }

    // Reset orientation on disposal
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    val isPip = activity?.isInPictureInPictureMode == true || state.isInPictureInPicture

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
                    setBottomPaddingFraction(1f - settings.subtitleVerticalPositionPercent.coerceIn(0.05f, 0.95f))
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

        // Draggable Subtitle Position Overlay (when subtitle sheet is open)
        if (activeSheet == PlayerSheet.SUBTITLES) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.subtitleVerticalPositionPercent) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newPercent = (settings.subtitleVerticalPositionPercent + (dragAmount.y / size.height)).coerceIn(0.10f, 0.92f)
                            vm.setSubtitlePosition(newPercent)
                        }
                    }
            ) {
                val lineY = maxHeight * settings.subtitleVerticalPositionPercent.coerceIn(0.10f, 0.92f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = lineY - 14.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ExcavPalette.Blue.copy(alpha = 0.7f),
                                        ExcavPalette.CyanGlow,
                                        ExcavPalette.Blue.copy(alpha = 0.7f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Surface(
                        shape = ExcavShapes.Pill,
                        color = ExcavPalette.Ink.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, ExcavPalette.BlueGlow)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = null,
                                tint = ExcavPalette.CyanGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Subtitle: ${(settings.subtitleVerticalPositionPercent * 100).toInt()}% (Drag to move)",
                                color = ExcavPalette.Text,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        }

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

        // Gesture HUD Overlay - sleek minimal bar, no bulky bubble background
        if (gestureHudText != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                gestureHudIcon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ExcavPalette.Blue,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = gestureHudText.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                )
                if (gestureHudProgress > 0f) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { gestureHudProgress },
                        modifier = Modifier
                            .width(100.dp)
                            .height(3.dp)
                            .clip(ExcavShapes.Pill),
                        color = ExcavPalette.Blue,
                        trackColor = Color(0x55FFFFFF)
                    )
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
        // Top Bar: Back button, Marquee Title (No resolution/size subtext), Subtitle (CC) & Audio Track Buttons ONLY
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

        // Center Transport Controls - Clean subtle Play/Pause Button (no bulky bubble)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    if (state.playback.isPlaying) player.pause() else player.resume()
                },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, ExcavPalette.Blue.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = if (state.playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
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
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                )
                Text(
                    text = formatDuration(duration),
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            }

            Spacer(Modifier.height(2.dp))

            ExcavSleekSlider(
                value = position.toFloat(),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth()
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
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Background Play Audio Toggle
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

                    // Fullscreen / Aspect Ratio Cycle
                    IconButton(
                        onClick = onCycleResizeMode,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Fullscreen & Aspect Ratio",
                            tint = ExcavPalette.Text,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .border(
                    1.dp,
                    ExcavPalette.Line,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ),
            color = ExcavPalette.SurfaceCard,
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
                        settings = settings,
                        onSetSize = vm::setSubtitleTextSize,
                        onSetColor = vm::setSubtitleTextColor,
                        onSetBg = vm::setSubtitleBackgroundStyle,
                        onSetPosition = vm::setSubtitlePosition,
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
        Text(
            text = "Chapters & Timeline",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (chapters.isEmpty()) {
            Text(
                text = "No chapters detected in this media.",
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chapters) { chapter ->
                    val isCurrent = currentPositionMs >= chapter.startTimeMs && currentPositionMs < chapter.endTimeMs
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isCurrent) ExcavPalette.BlueGlow else ExcavPalette.Line,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectChapter(chapter) },
                        color = if (isCurrent) ExcavPalette.Blue.copy(alpha = 0.15f) else ExcavPalette.InkElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (chapter.type) {
                                    ChapterType.INTRO -> Icons.Default.FastForward
                                    ChapterType.OUTRO -> Icons.Default.FastForward
                                    ChapterType.RECAP -> Icons.Default.Replay
                                    else -> Icons.Default.Bookmark
                                },
                                contentDescription = null,
                                tint = if (isCurrent) ExcavPalette.Blue else ExcavPalette.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = chapter.title,
                                    color = if (isCurrent) ExcavPalette.Blue else ExcavPalette.Text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
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
                                    color = ExcavPalette.Blue,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// -----------------------------------------------------------------------------------------
// Subtitles Sheet with Live Preview on Left Side and Subtitle Controls on Right Side
// -----------------------------------------------------------------------------------------
@Composable
private fun SubtitleSheet(
    state: PlayerState,
    player: PlayerManager,
    settings: UserSettings,
    onSetSize: (String) -> Unit,
    onSetColor: (String) -> Unit,
    onSetBg: (String) -> Unit,
    onSetPosition: (Float) -> Unit,
    onPickSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    val previewTextColor = when (settings.subtitleTextColor) {
        "Yellow" -> Color.Yellow
        "Cyan" -> ExcavPalette.CyanGlow
        "Green" -> Color.Green
        else -> Color.White
    }
    val previewBg = when (settings.subtitleBackgroundStyle) {
        "Translucent Box" -> Color.Black.copy(alpha = 0.75f)
        else -> Color.Transparent
    }
    val previewFontSize = when (settings.subtitleTextSize) {
        "Small" -> 13.sp
        "Large" -> 18.sp
        "Extra Large" -> 21.sp
        else -> 15.sp
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val isWide = maxWidth >= 520.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // =========================================================================
            // LEFT SIDE: Live Preview & Interactive Position Drag Area
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(if (isWide) 0.44f else 0.40f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Preview",
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = "${(settings.subtitleVerticalPositionPercent * 100).toInt()}%",
                        color = ExcavPalette.Blue,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Simulated Screen / Video Display with Draggable Subtitle Position
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isWide) 250.dp else 210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, ExcavPalette.Line, RoundedCornerShape(14.dp)),
                    color = Color(0xFF090A0E)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF141720),
                                        Color(0xFF0B0D12),
                                        Color(0xFF07080B)
                                    )
                                )
                            )
                            .pointerInput(settings.subtitleVerticalPositionPercent) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newPercent = (settings.subtitleVerticalPositionPercent + (dragAmount.y / size.height)).coerceIn(0.10f, 0.92f)
                                    onSetPosition(newPercent)
                                }
                            }
                    ) {
                        val subY = maxHeight * settings.subtitleVerticalPositionPercent.coerceIn(0.10f, 0.92f) - 16.dp

                        // Subtle guide line across preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = subY + 12.dp)
                                .height(1.dp)
                                .background(ExcavPalette.Blue.copy(alpha = 0.35f))
                        )

                        // Sample Subtitle Box (Draggable)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = subY)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = previewBg,
                                shape = RoundedCornerShape(4.dp),
                                border = if (settings.subtitleBackgroundStyle == "Outline") BorderStroke(1.dp, Color.Black.copy(alpha = 0.8f)) else null
                            ) {
                                Text(
                                    text = "Sample Subtitle",
                                    color = previewTextColor,
                                    fontSize = previewFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Top Drag Hint
                        Surface(
                            shape = ExcavShapes.Pill,
                            color = ExcavPalette.Ink.copy(alpha = 0.75f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UnfoldMore,
                                    contentDescription = null,
                                    tint = ExcavPalette.CyanGlow,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Drag to reposition",
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            }
                        }
                    }
                }

                // Reset Position Button
                OutlinedButton(
                    onClick = { onSetPosition(0.90f) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExcavPalette.TextMuted),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Text("Reset Position (90%)", fontSize = 11.sp)
                }
            }

            // =========================================================================
            // RIGHT SIDE: Subtitle Menu (Tracks, Appearance, Sliders)
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(if (isWide) 0.56f else 0.60f)
                    .heightIn(max = if (isWide) 380.dp else 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = "Enable Subtitles",
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
                    SubtitleRadioRow(
                        title = "en (Embedded)",
                        isSelected = state.playback.selectedSubtitleTrackId != null,
                        onClick = { player.selectSubtitleTrack("mock_en") }
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

                // Section: Subtitle Appearance
                Text(
                    text = "Appearance",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Spacer(Modifier.height(6.dp))

                // Text Size
                Text(
                    text = "Text Size",
                    color = ExcavPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Text Color
                Text(
                    text = "Text Color",
                    color = ExcavPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Background Style
                Text(
                    text = "Background Style",
                    color = ExcavPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Section: Subtitle Delay (Sleek slider)
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
                    onValueChange = { player.setSubtitleDelay(it.toLong()) },
                    valueRange = -5000f..5000f,
                    startLabel = "-5000 ms",
                    centerLabel = if (state.subtitleDelayMs > 0) "+${state.subtitleDelayMs} ms" else "${state.subtitleDelayMs} ms",
                    endLabel = "+5000 ms"
                )

                Spacer(Modifier.height(16.dp))
            }
        }
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
            color = if (isSelected) ExcavPalette.Blue else ExcavPalette.Text,
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Audio Tracks",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(bottom = 12.dp)
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
                        color = if (track.isSelected) ExcavPalette.Blue else ExcavPalette.Text,
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
                    color = ExcavPalette.Blue,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Playback Speed",
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(bottom = 12.dp)
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
                    color = if (isSelected) ExcavPalette.Blue else ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
