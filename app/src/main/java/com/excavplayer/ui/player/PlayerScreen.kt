@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.excavplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import com.excavplayer.subtitles.SubtitleLanguage
import com.excavplayer.subtitles.SubtitleSearchResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.excavplayer.domain.model.EqualizerState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.graphics.asImageBitmap
import com.excavplayer.ui.ExcavViewModel
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes

enum class ScrubSource { SLIDER, SWIPE }
data class ScrubState(val targetMs: Long, val source: ScrubSource)
private enum class DragClassifierMode { NONE, VERTICAL, HORIZONTAL_SEEK }
private enum class PlayerSheet { SUBTITLES, AUDIO, SPEED, CHAPTERS, MORE_OPTIONS, EQUALIZER, SUBTITLE_CUSTOMIZATION }
private enum class DoubleTapSide { LEFT, RIGHT }

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

@Composable
fun PlayerScreen(
    vm: ExcavViewModel,
    onClose: () -> Unit,
    onPickSubtitle: () -> Unit
) {
    val player = vm.player
    val state by vm.playerState.collectAsStateWithLifecycle()
    val queue by vm.queueState.collectAsStateWithLifecycle()
    val settings by vm.userSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
    }
    val pipHelper = remember { PipHelper(context.applicationContext, com.excavplayer.core.logging.AndroidAppLogger()) }

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var scrubState by remember { mutableStateOf<ScrubState?>(null) }
    var activeSheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    var wasPlayingBeforeSettings by remember { mutableStateOf(false) }
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
    var gestureHudIsBoost by remember { mutableStateOf(false) }

    var doubleTapSeekSide by remember { mutableStateOf<DoubleTapSide?>(null) }
    var doubleTapSeekAccumulatedSeconds by remember { mutableIntStateOf(10) }

    var lastDoubleTapSeekTime by remember { mutableLongStateOf(0L) }
    var activeSurfaceView by remember { mutableStateOf<android.view.SurfaceView?>(null) }

    // Subtle background blur when an option sheet is opened
    val sheetBlurRadius by animateDpAsState(
        targetValue = if (activeSheet != null) 2.dp else 0.dp,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "sheetBlurRadius"
    )

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
            gestureHudIsBoost = false
        }
    }

    // Auto-dismiss double tap seek ripple
    LaunchedEffect(doubleTapSeekSide, doubleTapSeekAccumulatedSeconds) {
        if (doubleTapSeekSide != null) {
            delay(650L)
            doubleTapSeekSide = null
        }
    }

    // Auto-fade player controls after 4s of inactivity while playing (resets whenever screen is touched)
    var isUserTouchingScreen by remember { mutableStateOf(false) }
    var resetControlsTimerKey by remember { mutableIntStateOf(0) }
    val onUserInteraction: () -> Unit = remember { { resetControlsTimerKey++ } }

    LaunchedEffect(controlsVisible, state.playback.isPlaying, activeSheet, isUserTouchingScreen, resetControlsTimerKey) {
        if (controlsVisible && state.playback.isPlaying && activeSheet == null && !isUserTouchingScreen) {
            delay(4000L)
            controlsVisible = false
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

    // Hide system status & navigation bars for immersive video playback, restore on disposal
    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else null

        insetsController?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
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

    val isPip = (activity?.isInPictureInPictureMode == true) || state.isInPictureInPicture

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        isUserTouchingScreen = anyPressed
                        resetControlsTimerKey++
                    }
                }
            }
            .pointerInput(isLocked, isPip, activeSheet) {
                if (!isLocked && !isPip && activeSheet == null) {
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
            .pointerInput(isLocked, zoomScale, isPip, activeSheet, settings.seekDurationSeconds) {
                if (!isLocked && !isPip && activeSheet == null) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) {
                                onUserInteraction()
                            }
                        },
                        onDoubleTap = { offset ->
                            onUserInteraction()
                            if (zoomScale > 1.05f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                gestureHudText = "Zoom 100%"
                                gestureHudIcon = Icons.Default.ZoomOut
                                return@detectTapGestures
                            }
                            val width = size.width
                            val now = android.os.SystemClock.uptimeMillis()
                            val seekStep = settings.seekDurationSeconds
                            val seekStepMs = settings.seekDurationMs
                            when {
                                offset.x < width * 0.35f -> {
                                    if (doubleTapSeekSide == DoubleTapSide.LEFT && now - lastDoubleTapSeekTime < 750) {
                                        doubleTapSeekAccumulatedSeconds += seekStep
                                    } else {
                                        doubleTapSeekAccumulatedSeconds = seekStep
                                    }
                                    lastDoubleTapSeekTime = now
                                    doubleTapSeekSide = DoubleTapSide.LEFT
                                    vm.player.seekBackward(seekStepMs)
                                }
                                offset.x > width * 0.65f -> {
                                    if (doubleTapSeekSide == DoubleTapSide.RIGHT && now - lastDoubleTapSeekTime < 750) {
                                        doubleTapSeekAccumulatedSeconds += seekStep
                                    } else {
                                        doubleTapSeekAccumulatedSeconds = seekStep
                                    }
                                    lastDoubleTapSeekTime = now
                                    doubleTapSeekSide = DoubleTapSide.RIGHT
                                    vm.player.seekForward(seekStepMs)
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
                } else if (!isPip && activeSheet != null) {
                    detectTapGestures(onTap = {
                        onUserInteraction()
                        activeSheet = null
                    })
                } else if (!isPip) {
                    detectTapGestures(onTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) {
                            onUserInteraction()
                        }
                    })
                }
            }
            .pointerInput(isLocked, settings, zoomScale, isPip, activeSheet) {
                if (!isLocked && !isPip && zoomScale <= 1.05f && activeSheet == null) {
                    var dragMode = DragClassifierMode.NONE
                    var isDragEligible = false
                    var startTouch = Offset.Zero
                    var originPositionMs = 0L
                    var currentDurationMs = 0L
                    var totalDragDelta = Offset.Zero
                    var currentEffectiveVol = 1.0f

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // Ignore touches in top 20% (status bar pull-down zone) and bottom 18% (nav bar zone)
                            val safeTop = size.height * 0.20f
                            val safeBottom = size.height * 0.82f
                            val safeLeft = size.width * 0.08f
                            val safeRight = size.width * 0.92f
                            isDragEligible = startOffset.y in safeTop..safeBottom && startOffset.x in safeLeft..safeRight
                            if (!isDragEligible) return@detectDragGestures

                            dragMode = DragClassifierMode.NONE
                            startTouch = startOffset
                            totalDragDelta = Offset.Zero
                            val pos = player.playbackPosition.value
                            originPositionMs = (scrubState?.targetMs ?: pos.currentPositionMs).coerceAtLeast(0L)
                            currentDurationMs = pos.durationMs.coerceAtLeast(0L)

                            audioManager?.let { am ->
                                val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val curDeviceVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                val playerVol = state.playback.volume
                                currentEffectiveVol = if (playerVol > 1.0f) {
                                    playerVol.coerceIn(1.0f, 2.0f)
                                } else {
                                    (curDeviceVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragMode == DragClassifierMode.HORIZONTAL_SEEK) {
                                scrubState?.let { s ->
                                    player.seekTo(s.targetMs)
                                }
                                scrubState = null
                            }
                            dragMode = DragClassifierMode.NONE
                            isDragEligible = false
                            gestureHudText = null
                            gestureHudIcon = null
                            gestureHudIsBoost = false
                            onUserInteraction()
                        },
                        onDragCancel = {
                            if (dragMode == DragClassifierMode.HORIZONTAL_SEEK) {
                                scrubState = null
                            }
                            dragMode = DragClassifierMode.NONE
                            isDragEligible = false
                            gestureHudText = null
                            gestureHudIcon = null
                            gestureHudIsBoost = false
                        },
                        onDrag = { change, dragAmount ->
                            if (!isDragEligible) return@detectDragGestures

                            totalDragDelta += dragAmount

                            // Classify once after touch-slop (~14dp)
                            if (dragMode == DragClassifierMode.NONE) {
                                val slopPx = 14.dp.toPx()
                                if (totalDragDelta.getDistance() >= slopPx) {
                                    if (Math.abs(totalDragDelta.x) > Math.abs(totalDragDelta.y)) {
                                        if (currentDurationMs > 0L) {
                                            dragMode = DragClassifierMode.HORIZONTAL_SEEK
                                            controlsVisible = true
                                        }
                                    } else {
                                        dragMode = DragClassifierMode.VERTICAL
                                    }
                                } else {
                                    return@detectDragGestures
                                }
                            }

                            change.consume()

                            if (dragMode == DragClassifierMode.HORIZONTAL_SEEK) {
                                controlsVisible = true
                                // Medium sensitivity:
                                val maxSwipeSpan = (currentDurationMs / 2.2f).coerceIn(60_000f, 180_000f)
                                val deltaX = change.position.x - startTouch.x
                                val targetMs = (originPositionMs + (deltaX / size.width) * maxSwipeSpan)
                                    .toLong()
                                    .coerceIn(0L, currentDurationMs)

                                scrubState = ScrubState(targetMs, ScrubSource.SWIPE)
                                // Do not set gestureHudText for seek so middle box does not show
                            } else if (dragMode == DragClassifierMode.VERTICAL) {
                                val isLeft = startTouch.x < size.width / 2
                                if (isLeft && settings.brightnessGestureEnabled) {
                                    val currentBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                                    val newBrightness = (currentBrightness - (dragAmount.y / size.height) * 1.5f).coerceIn(0.01f, 1f)
                                    activity?.window?.attributes = activity.window.attributes.apply { screenBrightness = newBrightness }
                                    gestureHudText = "Brightness ${(newBrightness * 100).toInt()}%"
                                    gestureHudProgress = newBrightness
                                    gestureHudIsBoost = false
                                    gestureHudIcon = Icons.Default.WbSunny
                                } else if (!isLeft && settings.volumeGestureEnabled) {
                                    audioManager?.let { am ->
                                        val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val delta = -(dragAmount.y / (size.height * 0.75f)) * 1.25f
                                        currentEffectiveVol = (currentEffectiveVol + delta).coerceIn(0f, 2.0f)

                                        if (currentEffectiveVol <= 1.0f) {
                                            if (state.playback.volume > 1.0f) {
                                                player.setVolume(1.0f)
                                            }
                                            val targetDeviceVol = (currentEffectiveVol * maxVol).roundToInt().coerceIn(0, maxVol)
                                            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetDeviceVol, 0)
                                            val volPercent = (currentEffectiveVol * 100f).roundToInt()
                                            gestureHudText = "Volume $volPercent%"
                                            gestureHudProgress = currentEffectiveVol
                                            gestureHudIsBoost = false
                                            gestureHudIcon = if (targetDeviceVol == 0) {
                                                Icons.AutoMirrored.Filled.VolumeOff
                                            } else {
                                                Icons.AutoMirrored.Filled.VolumeUp
                                            }
                                        } else {
                                            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol, 0)
                                            player.setVolume(currentEffectiveVol)
                                            val volPercent = (currentEffectiveVol * 100f).roundToInt()
                                            gestureHudText = "Volume $volPercent% (Boost)"
                                            gestureHudProgress = (currentEffectiveVol - 1.0f).coerceIn(0f, 1f)
                                            gestureHudIsBoost = true
                                            gestureHudIcon = Icons.AutoMirrored.Filled.VolumeUp
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
    ) {
        // Split-screen responsive scale indicators
        val isVeryCompact = maxHeight < 260.dp || maxWidth < 320.dp
        val isCompact = maxHeight < 360.dp || maxWidth < 440.dp
        val splitScale = (minOf(maxWidth / 640.dp, maxHeight / 360.dp)).coerceIn(0.55f, 1.0f)

        // Video Surface with Pinch-to-Zoom and Pan transformations
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = vm.player.exoPlayer
                    useController = false
                    keepScreenOn = state.playback.isPlaying
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.resizeMode = resizeMode
                    applySubtitleStyles(subtitleView, settings)
                }
            },
            update = { playerView ->
                val currentVidId = state.currentVideo?.id
                playerView.keepScreenOn = state.playback.isPlaying
                playerView.resizeMode = resizeMode
                applySubtitleStyles(playerView.subtitleView, settings)

                // Display Refresh Rate Optimization (Judder-Free Playback)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
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
                    } catch (_: Throwable) {
                        // Ignore surface setFrameRate failures on unsupported devices/displays
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

        // Double-Tap Quick Seek Visual Indicator (text + chevrons only, no background pill)
        val seekChevronSize = if (isVeryCompact) 18.dp else if (isCompact) 22.dp else 28.dp
        val seekFontSize = if (isVeryCompact) 10.sp else if (isCompact) 12.sp else 14.sp
        val seekPaddingH = if (isVeryCompact) 16.dp else if (isCompact) 28.dp else 44.dp

        AnimatedVisibility(
            visible = doubleTapSeekSide != null,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.82f, animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(if (doubleTapSeekSide == DoubleTapSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = seekPaddingH)
        ) {
            doubleTapSeekSide?.let { side ->
                val isLeft = side == DoubleTapSide.LEFT
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-6).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chevron = if (isLeft) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward
                        Icon(
                            imageVector = chevron,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(seekChevronSize)
                        )
                        Icon(
                            imageVector = chevron,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(seekChevronSize)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${if (isLeft) "-" else "+"}$doubleTapSeekAccumulatedSeconds sec",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = seekFontSize
                        )
                    )
                }
            }
        }

        // Gesture HUD Overlay - frosted glass box with spring animation for volume and brightness gestures
        val hudIconSize = if (isVeryCompact) 20.dp else if (isCompact) 26.dp else 32.dp
        val hudFontSize = if (isVeryCompact) 11.sp else if (isCompact) 13.sp else 15.sp
        val hudPadH = if (isVeryCompact) 14.dp else 24.dp
        val hudPadV = if (isVeryCompact) 10.dp else 20.dp
        val hudProgWidth = if (isVeryCompact) 75.dp else if (isCompact) 95.dp else 120.dp

        AnimatedVisibility(
            visible = gestureHudText != null,
            enter = scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(120)),
            exit = scaleOut(targetScale = 0.92f, animationSpec = tween(160)) + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val animatedHudProgress by animateFloatAsState(
                targetValue = gestureHudProgress,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "hudProgress"
            )
            Box(
                modifier = Modifier
                    .playerGlass(RoundedCornerShape(24.dp))
                    .padding(horizontal = hudPadH, vertical = hudPadV),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    gestureHudIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (gestureHudIsBoost) ExcavPalette.Yellow else ExcavPalette.Blue,
                            modifier = Modifier.size(hudIconSize)
                        )
                        Spacer(Modifier.height(if (isVeryCompact) 4.dp else 8.dp))
                    }
                    Text(
                        text = gestureHudText.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = hudFontSize)
                    )
                    if (gestureHudProgress > 0f) {
                        Spacer(Modifier.height(if (isVeryCompact) 6.dp else 10.dp))
                        LinearProgressIndicator(
                            progress = { animatedHudProgress },
                            modifier = Modifier
                                .width(hudProgWidth)
                                .height(4.dp)
                                .clip(ExcavShapes.Pill),
                            color = if (gestureHudIsBoost) ExcavPalette.Yellow else ExcavPalette.Blue,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }

        // Skip Intro / Outro / Recap Floating Pill (isolated state read)
        val pillBottomPad = when {
            isVeryCompact -> 82.dp
            isCompact -> 112.dp
            else -> 142.dp
        }
        val pillEndPad = when {
            isVeryCompact -> 10.dp
            isCompact -> 16.dp
            else -> 24.dp
        }

        FloatingSkipChapterPill(
            chapters = state.chapters,
            playbackPositionFlow = vm.player.playbackPosition,
            isCompact = isCompact || isVeryCompact,
            onSeekTo = { vm.player.seekTo(it) },
            onHud = { text, icon ->
                gestureHudText = text
                gestureHudIcon = icon
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = pillBottomPad, end = pillEndPad)
        )

        // Floating Next Episode Pill (isolated state read)
        FloatingNextEpisodePill(
            chapters = state.chapters,
            playbackStatus = state.playback.playbackStatus,
            playbackPositionFlow = vm.player.playbackPosition,
            queue = queue,
            isVeryCompact = isVeryCompact,
            isCompact = isCompact,
            onPlayNext = { index -> vm.playQueueItem(index) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )

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
                                    state.currentVideo?.let { vid ->
                                        vm.player.dispatch(PlayerCommand.Play(vid))
                                    }
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
        if (isLocked && !isPip) {
            val lockBtnSize = ((if (isVeryCompact) 28.dp else if (isCompact) 34.dp else 42.dp) * splitScale).coerceIn(22.dp, 42.dp)
            val lockIconSize = (lockBtnSize * 0.52f).coerceIn(12.dp, 22.dp)
            val lockPadH = ((if (isVeryCompact) 8.dp else 16.dp) * splitScale).coerceIn(4.dp, 16.dp)
            val lockPadV = ((if (isVeryCompact) 4.dp else 10.dp) * splitScale).coerceIn(2.dp, 10.dp)

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = lockPadH, vertical = lockPadV),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CompositionLocalProvider(
                    LocalRippleConfiguration provides null,
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                ) {
                    IconButton(
                        onClick = {
                            onUserInteraction()
                            isLocked = false
                            vm.player.dispatch(PlayerCommand.SetScreenLocked(false))
                        },
                        modifier = Modifier
                            .size(lockBtnSize)
                            .playerGlass(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = stringResource(R.string.cd_unlock),
                            tint = ExcavPalette.Blue,
                            modifier = Modifier.size(lockIconSize)
                        )
                    }
                }
            }
        } else if (!isPip) {
            // Main Controls Overlay
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(sheetBlurRadius)
                ) {
                    PlayerControlsOverlay(
                        state = state,
                        player = vm.player,
                        isVeryCompact = isVeryCompact,
                        isCompact = isCompact,
                        splitScale = splitScale,
                        isSheetOpen = activeSheet != null,
                        onClose = {
                            if (!keepAudioOnBackground && !state.isBackgroundAudio) {
                                vm.player.stop()
                            }
                            onClose()
                        },
                        onOpenSheet = { sheet ->
                            onUserInteraction()
                            if (sheet == PlayerSheet.EQUALIZER || sheet == PlayerSheet.SUBTITLE_CUSTOMIZATION) {
                                if (activeSheet != PlayerSheet.EQUALIZER && activeSheet != PlayerSheet.SUBTITLE_CUSTOMIZATION) {
                                    wasPlayingBeforeSettings = state.playback.isPlaying
                                    if (state.playback.isPlaying) {
                                        vm.player.pause()
                                    }
                                }
                            }
                            activeSheet = sheet
                        },
                        onLock = {
                            onUserInteraction()
                            isLocked = true
                            vm.player.dispatch(PlayerCommand.SetScreenLocked(true))
                        },
                        onPip = {
                            onUserInteraction()
                            val currentActivity = activity ?: context.findActivity()
                            if (currentActivity != null) {
                                controlsVisible = false
                                activeSheet = null
                                val entered = pipHelper.enterPictureInPicture(currentActivity, state.currentVideo, state.playback.isPlaying)
                                if (entered) {
                                    vm.player.dispatch(PlayerCommand.SetInPictureInPicture(true))
                                }
                            }
                        },
                        onCycleOrientation = {
                            onUserInteraction()
                            cycleOrientation()
                        },
                        orientationIndex = orientationIndex,
                        onToggleBackgroundAudio = {
                            onUserInteraction()
                            if (state.isBackgroundAudio || keepAudioOnBackground) {
                                keepAudioOnBackground = false
                                vm.player.stopBackgroundPlay()
                                vm.showMessage("Background audio disabled")
                            } else {
                                keepAudioOnBackground = true
                                vm.player.startBackgroundPlay()
                                vm.showMessage("Playing audio in background")
                                onClose()
                            }
                        },
                        isBackgroundAudioActive = state.isBackgroundAudio || keepAudioOnBackground,
                        onToggleDialogueBoost = {
                            onUserInteraction()
                            val next = !settings.dialogueBoostEnabled
                            vm.setDialogueBoost(next)
                            gestureHudProgress = 0f
                            gestureHudText = if (next) "Dialogue Boost: On" else "Dialogue Boost: Off"
                            gestureHudIcon = Icons.Default.RecordVoiceOver
                        },
                        isDialogueBoostActive = settings.dialogueBoostEnabled,
                        onCycleResizeMode = {
                            onUserInteraction()
                            cycleResizeMode()
                        },
                        hasPrevious = queue.currentIndex > 0,
                        onPlayPrevious = {
                            if (activeSheet != null) return@PlayerControlsOverlay
                            onUserInteraction()
                            vm.playQueueItem(queue.currentIndex - 1)
                        },
                        hasNext = queue.currentIndex in 0 until (queue.items.size - 1),
                        onPlayNext = {
                            if (activeSheet != null) return@PlayerControlsOverlay
                            onUserInteraction()
                            vm.playQueueItem(queue.currentIndex + 1)
                        },
                        scrubState = scrubState,
                        onScrubStart = {
                            if (activeSheet != null) return@PlayerControlsOverlay
                            onUserInteraction()
                            val cur = (scrubState?.targetMs ?: vm.player.playbackPosition.value.currentPositionMs).coerceAtLeast(0L)
                            scrubState = ScrubState(cur, ScrubSource.SLIDER)
                        },
                        onScrubChange = { targetMs ->
                            if (activeSheet != null) return@PlayerControlsOverlay
                            onUserInteraction()
                            scrubState = ScrubState(targetMs, ScrubSource.SLIDER)
                        },
                        onScrubEnd = {
                            if (activeSheet != null) return@PlayerControlsOverlay
                            onUserInteraction()
                            scrubState?.let { s ->
                                vm.player.seekTo(s.targetMs)
                            }
                            scrubState = null
                        },
                        onInteraction = onUserInteraction
                    )
                }
            }
        }

        // Active Bottom Sheet with anchor-originating box pop animation
        val currentSheet = activeSheet
        var lastSheet by remember { mutableStateOf<PlayerSheet?>(null) }
        if (currentSheet != null) {
            lastSheet = currentSheet
        }

        AnimatedVisibility(
            visible = activeSheet != null && !isPip,
            enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))
        ) {
            lastSheet?.let { sheetType ->
                SheetContainer(
                    sheet = sheetType,
                    state = state,
                    vm = vm,
                    isOpen = activeSheet != null,
                    maxSheetHeight = maxHeight,
                    onChangeSheet = { newSheet ->
                        onUserInteraction()
                        if (newSheet == PlayerSheet.EQUALIZER || newSheet == PlayerSheet.SUBTITLE_CUSTOMIZATION) {
                            if (activeSheet != PlayerSheet.EQUALIZER && activeSheet != PlayerSheet.SUBTITLE_CUSTOMIZATION) {
                                wasPlayingBeforeSettings = state.playback.isPlaying
                                if (state.playback.isPlaying) {
                                    vm.player.pause()
                                }
                            }
                        }
                        activeSheet = newSheet
                    },
                    onDismiss = {
                        onUserInteraction()
                        val closingSheet = activeSheet
                        activeSheet = null
                        if (closingSheet == PlayerSheet.EQUALIZER || closingSheet == PlayerSheet.SUBTITLE_CUSTOMIZATION) {
                            if (wasPlayingBeforeSettings) {
                                vm.player.resume()
                                wasPlayingBeforeSettings = false
                            }
                        }
                    },
                    onPickSubtitle = onPickSubtitle
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControlsOverlay(
    state: PlayerState,
    player: PlayerManager,
    isVeryCompact: Boolean = false,
    isCompact: Boolean = false,
    splitScale: Float = 1f,
    isSheetOpen: Boolean = false,
    scrubState: ScrubState? = null,
    onScrubStart: () -> Unit = {},
    onScrubChange: (Long) -> Unit = {},
    onScrubEnd: () -> Unit = {},
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
    onPlayNext: () -> Unit,
    onInteraction: () -> Unit = {}
) {
    CompositionLocalProvider(
        LocalRippleConfiguration provides null,
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val minDim = minOf(maxWidth, maxHeight)
            val maxDim = maxOf(maxWidth, maxHeight)
            // Dynamic scale factor derived continuously from screen width and height
            val dynamicScale = ((minDim / 380.dp) * 0.65f + (maxDim / 820.dp) * 0.35f).coerceIn(0.6f, 1.4f) * splitScale

            val topBtnSize = (40.dp * dynamicScale).coerceIn(24.dp, 54.dp)
            val topIconSize = topBtnSize * 0.52f
            val titleFontSize = (15f * dynamicScale).coerceIn(11f, 18f).sp
            val topSpacing = (12.dp * dynamicScale).coerceIn(4.dp, 20.dp)
            val topBtnSpacing = (8.dp * dynamicScale).coerceIn(3.dp, 14.dp)

            val centerPlaySize = (64.dp * dynamicScale).coerceIn(40.dp, 84.dp)
            val centerPlayIconSize = centerPlaySize * 0.55f
            val centerSkipSize = (48.dp * dynamicScale).coerceIn(30.dp, 64.dp)
            val centerSkipIconSize = centerSkipSize * 0.55f
            val centerSpacing = (24.dp * dynamicScale).coerceIn(10.dp, 36.dp)

            val bottomPillBtnSize = (36.dp * dynamicScale).coerceIn(24.dp, 48.dp)
            val bottomPillIconSize = bottomPillBtnSize * 0.55f
            val bottomPillPaddingH = (7.dp * dynamicScale).coerceIn(3.dp, 12.dp)
            val bottomPillPaddingV = (4.dp * dynamicScale).coerceIn(2.dp, 6.dp)
            val bottomPillSpacing = (3.dp * dynamicScale).coerceIn(1.dp, 6.dp)
            val speedTextSize = (12f * dynamicScale).coerceIn(9.5f, 15f).sp
            val speedPaddingH = (10.dp * dynamicScale).coerceIn(4.dp, 14.dp)
            val speedPaddingV = (4.dp * dynamicScale).coerceIn(2.dp, 8.dp)
            val timelineTextSize = (13f * dynamicScale).coerceIn(10f, 16f).sp
            val timelineSpacing = (10.dp * dynamicScale).coerceIn(4.dp, 16.dp)
            val outerPaddingH = (14.dp * dynamicScale).coerceIn(6.dp, 24.dp)
            val outerPaddingV = (10.dp * dynamicScale).coerceIn(4.dp, 18.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = outerPaddingH, vertical = outerPaddingV)
            ) {
                // Top Bar: Back button, Marquee Title, Dialogue Booster, Subtitle (CC), Audio Track
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (dynamicScale < 0.75f) 0.dp else 4.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                    modifier = Modifier
                        .size(topBtnSize)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize)
                    )
                }

                Spacer(Modifier.width(topSpacing))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentVideo?.displayName.orEmpty().ifEmpty { "Playing Media" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = titleFontSize
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }



                Spacer(Modifier.width(topBtnSpacing))

                // Subtitle Button (CC)
                val hasSubtitles = state.playback.selectedSubtitleTrackId != null
                IconButton(
                    onClick = {
                        onInteraction()
                        onOpenSheet(PlayerSheet.SUBTITLES)
                    },
                    modifier = Modifier
                        .size(topBtnSize)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = stringResource(R.string.cd_subtitles),
                        tint = if (hasSubtitles) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(topIconSize)
                    )
                }

                Spacer(Modifier.width(topBtnSpacing))

                // Audio Track Button
                IconButton(
                    onClick = {
                        onInteraction()
                        onOpenSheet(PlayerSheet.AUDIO)
                    },
                    modifier = Modifier
                        .size(topBtnSize)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = stringResource(R.string.audio_tracks),
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize)
                    )
                }

                Spacer(Modifier.width(topBtnSpacing))

                // More Options (Three Dot) Button
                IconButton(
                    onClick = {
                        onInteraction()
                        onOpenSheet(PlayerSheet.MORE_OPTIONS)
                    },
                    modifier = Modifier
                        .size(topBtnSize)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize)
                    )
                }
            }

            // Center Transport Controls - Vertically and Horizontally Centered in Screen (Separated Buttons)
            val playPauseScale by animateFloatAsState(
                targetValue = if (state.playback.isPlaying) 1f else 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "playPauseScale"
            )

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(centerSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = hasPrevious && !isSheetOpen,
                    onClick = {
                        if (isSheetOpen) return@IconButton
                        onInteraction()
                        onPlayPrevious()
                    },
                    modifier = Modifier
                        .size(centerSkipSize)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Video",
                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(centerSkipIconSize)
                    )
                }

                IconButton(
                    onClick = {
                        if (isSheetOpen) return@IconButton
                        onInteraction()
                        if (state.playback.isPlaying) player.pause() else player.resume()
                    },
                    modifier = Modifier
                        .size(centerPlaySize)
                        .playerGlass(CircleShape)
                        .graphicsLayer {
                            scaleX = playPauseScale
                            scaleY = playPauseScale
                        }
                ) {
                    Icon(
                        imageVector = if (state.playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(centerPlayIconSize)
                    )
                }

                IconButton(
                    enabled = hasNext && !isSheetOpen,
                    onClick = {
                        if (isSheetOpen) return@IconButton
                        onInteraction()
                        onPlayNext()
                    },
                    modifier = Modifier
                        .size(centerSkipSize)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Video",
                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(centerSkipIconSize)
                    )
                }
            }

            // Bottom Section: Progress Bar / Timeline and Bottom Floating Glass Pills
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // Seekbar Preview Thumbnail Storyboard Popup
                SeekbarPreviewPopup(
                    scrubState = scrubState,
                    video = state.currentVideo,
                    durationMs = player.playbackPosition.value.durationMs,
                    isVeryCompact = isVeryCompact,
                    isCompact = isCompact,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Bottom Progress Bar and Clean Action Buttons (isolated timeline scrubber)
                PlayerTimelineSection(
                    playbackPositionFlow = player.playbackPosition,
                    scrubState = scrubState,
                    onScrubStart = onScrubStart,
                    onScrubChange = onScrubChange,
                    onScrubEnd = onScrubEnd,
                    chapters = state.chapters,
                    timeTextSize = timelineTextSize,
                    onInteraction = onInteraction
                )

                Spacer(Modifier.height(timelineSpacing))

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
                            .padding(horizontal = bottomPillPaddingH, vertical = bottomPillPaddingV),
                        horizontalArrangement = Arrangement.spacedBy(bottomPillSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Screen Lock
                        IconButton(
                            onClick = {
                                onInteraction()
                                onLock()
                            },
                            modifier = Modifier.size(bottomPillBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.cd_lock),
                                tint = Color.White,
                                modifier = Modifier.size(bottomPillIconSize)
                            )
                        }

                        // Picture in Picture
                        IconButton(
                            onClick = {
                                onInteraction()
                                onPip()
                            },
                            modifier = Modifier.size(bottomPillBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture",
                                tint = Color.White,
                                modifier = Modifier.size(bottomPillIconSize)
                            )
                        }

                        // Screen Orientation Toggle with Animated Icon Transition
                        IconButton(
                            onClick = {
                                onInteraction()
                                onCycleOrientation()
                            },
                            modifier = Modifier.size(bottomPillBtnSize)
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
                                    4 -> Icons.Default.ScreenRotation
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
                                    modifier = Modifier.size(bottomPillIconSize)
                                )
                            }
                        }

                        // Chapters Button
                        val hasChapters = state.chapters.isNotEmpty()
                        IconButton(
                            enabled = hasChapters,
                            onClick = {
                                onInteraction()
                                onOpenSheet(PlayerSheet.CHAPTERS)
                            },
                            modifier = Modifier.size(bottomPillBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = "Chapters & Timeline",
                                tint = if (hasChapters) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(bottomPillIconSize)
                            )
                        }
                    }

                    // Right Pill: Playback Speed, Background Play Audio, Fullscreen
                    Row(
                        modifier = Modifier
                            .playerGlass(RoundedCornerShape(32.dp))
                            .padding(horizontal = bottomPillPaddingH, vertical = bottomPillPaddingV),
                        horizontalArrangement = Arrangement.spacedBy(bottomPillSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dialogue Booster Button (Left side of playback speed)
                        IconButton(
                            onClick = {
                                onInteraction()
                                onToggleDialogueBoost()
                            },
                            modifier = Modifier.size(bottomPillBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Dialogue Booster",
                                tint = if (isDialogueBoostActive) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(bottomPillIconSize)
                            )
                        }

                        // Playback Speed Pill Button
                        Box(
                            modifier = Modifier
                                .clickable {
                                    onInteraction()
                                    onOpenSheet(PlayerSheet.SPEED)
                                }
                                .padding(horizontal = speedPaddingH, vertical = speedPaddingV),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.playback.playbackSpeed}x",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = speedTextSize
                                )
                            )
                        }

                        // Background Play Audio Toggle
                        IconButton(
                            onClick = {
                                onInteraction()
                                onToggleBackgroundAudio()
                            },
                            modifier = Modifier.size(bottomPillBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Background Play Audio",
                                tint = if (isBackgroundAudioActive) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(bottomPillIconSize)
                            )
                        }

                        // Fullscreen / Aspect Ratio Cycle
                        IconButton(
                            onClick = {
                                onInteraction()
                                onCycleResizeMode()
                            },
                            modifier = Modifier.size(bottomPillBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Fullscreen & Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(bottomPillIconSize)
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
// Bottom Sheet Containers
// -----------------------------------------------------------------------------------------
private fun getSheetTransformOrigin(sheet: PlayerSheet?): TransformOrigin {
    return when (sheet) {
        PlayerSheet.MORE_OPTIONS,
        PlayerSheet.EQUALIZER,
        PlayerSheet.SUBTITLE_CUSTOMIZATION -> TransformOrigin(0.95f, 0.0f)
        PlayerSheet.AUDIO -> TransformOrigin(0.80f, 0.0f)
        PlayerSheet.SUBTITLES -> TransformOrigin(0.68f, 0.0f)
        PlayerSheet.SPEED -> TransformOrigin(0.82f, 1.0f)
        PlayerSheet.CHAPTERS -> TransformOrigin(0.15f, 1.0f)
        null -> TransformOrigin(0.5f, 0.5f)
    }
}

@Composable
private fun SheetContainer(
    sheet: PlayerSheet,
    state: PlayerState,
    vm: ExcavViewModel,
    isOpen: Boolean = true,
    maxSheetHeight: androidx.compose.ui.unit.Dp = 420.dp,
    onChangeSheet: (PlayerSheet) -> Unit = {},
    onDismiss: () -> Unit,
    onPickSubtitle: () -> Unit
) {
    val settings by vm.userSettings.collectAsStateWithLifecycle()

    // Alignment and padding matching trigger button locations:
    // Subtitles, Audio, More Options, Equalizer, Subtitle Customization -> Top bar on right (Alignment.TopEnd)
    // Speed -> Bottom bar on right (Alignment.BottomEnd)
    // Chapters -> Bottom bar on left (Alignment.BottomStart)
    val alignment = when (sheet) {
        PlayerSheet.SUBTITLES, PlayerSheet.AUDIO, PlayerSheet.MORE_OPTIONS,
        PlayerSheet.EQUALIZER, PlayerSheet.SUBTITLE_CUSTOMIZATION -> Alignment.TopEnd
        PlayerSheet.SPEED -> Alignment.BottomEnd
        PlayerSheet.CHAPTERS -> Alignment.BottomStart
    }

    val containerPadding = when (sheet) {
        PlayerSheet.SUBTITLES, PlayerSheet.AUDIO, PlayerSheet.MORE_OPTIONS,
        PlayerSheet.EQUALIZER, PlayerSheet.SUBTITLE_CUSTOMIZATION -> PaddingValues(top = 56.dp, end = 16.dp, bottom = 16.dp, start = 16.dp)
        PlayerSheet.SPEED -> PaddingValues(bottom = 76.dp, end = 16.dp, top = 16.dp, start = 16.dp)
        PlayerSheet.CHAPTERS -> PaddingValues(bottom = 76.dp, start = 16.dp, top = 16.dp, end = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(containerPadding),
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = scaleIn(
                initialScale = 0.35f,
                transformOrigin = getSheetTransformOrigin(sheet),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
            exit = scaleOut(
                targetScale = 0.35f,
                transformOrigin = getSheetTransformOrigin(sheet),
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))
        ) {
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = false
                    ) {}
                    .widthIn(
                        min = 280.dp,
                        max = when (sheet) {
                            PlayerSheet.SUBTITLES -> 400.dp
                            PlayerSheet.CHAPTERS -> 380.dp
                            PlayerSheet.AUDIO -> 340.dp
                            PlayerSheet.SPEED -> 320.dp
                            PlayerSheet.MORE_OPTIONS -> 300.dp
                            PlayerSheet.EQUALIZER -> 400.dp
                            PlayerSheet.SUBTITLE_CUSTOMIZATION -> 380.dp
                        }
                    )
                    .heightIn(max = (maxSheetHeight * 0.88f).coerceAtLeast(200.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .darkUltraThinBlur(
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0xF20E121B),
                        strokeColor = Color.White.copy(alpha = 0.20f)
                    )
            ) {
                AnimatedContent(
                    targetState = sheet,
                    transitionSpec = {
                        if (targetState == PlayerSheet.EQUALIZER || targetState == PlayerSheet.SUBTITLE_CUSTOMIZATION) {
                            (slideInHorizontally { it / 3 } + fadeIn(animationSpec = tween(180)))
                                .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(animationSpec = tween(140)))
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn(animationSpec = tween(180)))
                                .togetherWith(slideOutHorizontally { it / 3 } + fadeOut(animationSpec = tween(140)))
                        }
                    },
                    label = "sheetPageTransition"
                ) { currentSheet ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                    ) {
                        when (currentSheet) {
                            PlayerSheet.SUBTITLES -> SubtitleSheet(
                                state = state,
                                player = vm.player,
                                vm = vm,
                                onOpenCustomization = { onChangeSheet(PlayerSheet.SUBTITLE_CUSTOMIZATION) },
                                onPickSubtitle = onPickSubtitle,
                                onDismiss = onDismiss
                            )
                            PlayerSheet.AUDIO -> AudioSheet(state, vm.player, onDismiss)
                            PlayerSheet.SPEED -> SpeedSheet(state, vm.player, onDismiss)
                            PlayerSheet.CHAPTERS -> ChaptersSheet(
                                chapters = state.chapters,
                                playbackPositionFlow = vm.player.playbackPosition,
                                onSelectChapter = {
                                    vm.player.seekTo(it.startTimeMs)
                                    onDismiss()
                                },
                                onDismiss = onDismiss
                            )
                            PlayerSheet.MORE_OPTIONS -> MoreOptionsSheet(
                                onOpenEqualizer = { onChangeSheet(PlayerSheet.EQUALIZER) },
                                onOpenSubtitleCustomization = { onChangeSheet(PlayerSheet.SUBTITLE_CUSTOMIZATION) },
                                onDismiss = onDismiss
                            )
                            PlayerSheet.EQUALIZER -> EqualizerSheet(
                                equalizerState = state.equalizerState,
                                vm = vm,
                                onBack = {
                                    onChangeSheet(PlayerSheet.MORE_OPTIONS)
                                },
                                onDismiss = onDismiss
                            )
                            PlayerSheet.SUBTITLE_CUSTOMIZATION -> SubtitleCustomizationSheet(
                                settings = settings,
                                vm = vm,
                                onBack = {
                                    onChangeSheet(PlayerSheet.MORE_OPTIONS)
                                },
                                onDismiss = onDismiss
                            )
                        }
                    }
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
    playbackPositionFlow: StateFlow<PlaybackPosition>,
    onSelectChapter: (MediaChapter) -> Unit,
    onDismiss: () -> Unit
) {
    val posState by playbackPositionFlow.collectAsStateWithLifecycle()
    val currentPositionMs = posState.currentPositionMs

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
// Subtitles Sheet (Track Selection & Online Subtitles)
// -----------------------------------------------------------------------------------------
private enum class SubtitleTab { TRACKS, ONLINE }

@Composable
private fun SubtitlesPillTabBar(
    selectedTab: SubtitleTab,
    onTabSelected: (SubtitleTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { listOf(SubtitleTab.TRACKS to "Tracks", SubtitleTab.ONLINE to "Online Sub") }
    val selectedIndex = if (selectedTab == SubtitleTab.TRACKS) 0 else 1

    val targetOffsetFraction by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pillOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(3.dp)
    ) {
        Layout(
            content = {
                // Animated Highlight Pill Background
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ExcavPalette.Primary, ExcavPalette.Primary.copy(alpha = 0.85f))
                            )
                        )
                )
                // Tabs
                tabs.forEach { (tab, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(17.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selectedTab == tab) Color.White else ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val indicatorMeasurable = measurables[0]
            val tabMeasurables = measurables.drop(1)

            val tabPlaceables = tabMeasurables.map { it.measure(constraints) }
            val totalWidth = tabPlaceables.sumOf { it.width }
            val maxHeight = tabPlaceables.maxOfOrNull { it.height } ?: 0

            val w0 = tabPlaceables.getOrNull(0)?.width ?: 0
            val w1 = tabPlaceables.getOrNull(1)?.width ?: 0
            val animatedWidth = (w0 + (w1 - w0) * targetOffsetFraction).toInt()

            val indicatorPlaceable = indicatorMeasurable.measure(
                constraints.copy(
                    minWidth = animatedWidth,
                    maxWidth = animatedWidth,
                    minHeight = maxHeight,
                    maxHeight = maxHeight
                )
            )

            layout(totalWidth, maxHeight) {
                val animatedX = (0f + w0.toFloat() * targetOffsetFraction).toInt()
                indicatorPlaceable.placeRelative(animatedX, 0)

                var currentLeft = 0
                tabPlaceables.forEach { placeable ->
                    placeable.placeRelative(currentLeft, 0)
                    currentLeft += placeable.width
                }
            }
        }
    }
}

@Composable
private fun SubtitleSheet(
    state: PlayerState,
    player: PlayerManager,
    vm: ExcavViewModel,
    onOpenCustomization: () -> Unit = {},
    onPickSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(SubtitleTab.TRACKS) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
    ) {
        // Header with Title and Animated Pill Tab Bar beside each other
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Subtitles",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onOpenCustomization,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Subtitle Style",
                        tint = ExcavPalette.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            SubtitlesPillTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }

        // Animated Page Transition
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState == SubtitleTab.ONLINE) {
                    (slideInHorizontally { width -> width / 3 } + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally { width -> -width / 3 } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally { width -> width / 3 } + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "subtitleTabTransition"
        ) { tab ->
            when (tab) {
                SubtitleTab.TRACKS -> TracksTabContent(
                    state = state,
                    player = player,
                    vm = vm,
                    onPickSubtitle = onPickSubtitle
                )
                SubtitleTab.ONLINE -> OnlineSubTabContent(
                    state = state,
                    vm = vm
                )
            }
        }
    }
}

@Composable
private fun TracksTabContent(
    state: PlayerState,
    player: PlayerManager,
    vm: ExcavViewModel,
    onPickSubtitle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
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
        }

        // -------------------------------------------------------------------------
        // Subtitle Delay Control (Player Seekbar Style, Range: -5.0s .. +5.0s)
        // -------------------------------------------------------------------------
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = ExcavPalette.Line.copy(alpha = 0.4f), thickness = 0.6.dp)
        Spacer(Modifier.height(6.dp))

        var sliderValue by remember(state.subtitleDelayMs) {
            mutableFloatStateOf((state.subtitleDelayMs / 1000f).coerceIn(-5.0f, 5.0f))
        }
        var isSliding by remember { mutableStateOf(false) }
        var moveCount by remember { mutableIntStateOf(0) }
        var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val scope = rememberCoroutineScope()

        val currentDelaySec = remember(state.subtitleDelayMs, sliderValue, isSliding) {
            if (isSliding) {
                (sliderValue * 10).roundToInt() / 10.0
            } else {
                (state.subtitleDelayMs / 100.0).roundToInt() / 10.0
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Header Row: Label & Value / Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subtitle Delay",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val delayFormatted = if (currentDelaySec > 0) "+${"%.1f".format(currentDelaySec)}s"
                        else if (currentDelaySec < 0) "${"%.1f".format(currentDelaySec)}s"
                        else "0.0s"

                    Text(
                        text = delayFormatted,
                        color = if (currentDelaySec != 0.0) ExcavPalette.Primary else ExcavPalette.Text,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )

                    if (state.subtitleDelayMs != 0L || sliderValue != 0f) {
                        Text(
                            text = "Reset",
                            color = ExcavPalette.Primary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    debounceJob?.cancel()
                                    sliderValue = 0f
                                    vm.setSubtitleDelay(0L)
                                }
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Move the slider down a bit
            Spacer(Modifier.height(4.dp))

            // Player seekbar style Slider (-5.0s .. +5.0s)
            ExcavSleekSlider(
                value = sliderValue,
                onValueChange = { newVal ->
                    sliderValue = newVal
                    moveCount++
                    if (moveCount > 1) {
                        debounceJob?.cancel()
                        debounceJob = scope.launch {
                            delay(500L)
                            val roundedMs = ((newVal * 10).roundToInt() * 100L).coerceIn(-5000L, 5000L)
                            vm.setSubtitleDelay(roundedMs)
                        }
                    }
                },
                onValueChangeStarted = {
                    isSliding = true
                    moveCount = 0
                    debounceJob?.cancel()
                },
                onValueChangeFinished = {
                    if (moveCount <= 1) {
                        debounceJob?.cancel()
                        val roundedMs = ((sliderValue * 10).roundToInt() * 100L).coerceIn(-5000L, 5000L)
                        vm.setSubtitleDelay(roundedMs)
                    }
                    isSliding = false
                    moveCount = 0
                },
                valueRange = -5.0f..5.0f,
                showZeroMarker = false,
                modifier = Modifier.fillMaxWidth()
            )

            // Range markers (-5s, 0s, +5s)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "-5s",
                    color = ExcavPalette.TextMuted.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp)
                )
                Text(
                    text = "0s",
                    color = ExcavPalette.TextMuted.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp)
                )
                Text(
                    text = "+5s",
                    color = ExcavPalette.TextMuted.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Action Row: -0.1s, Add subtitles, +0.1s
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ExcavPalette.Line, RoundedCornerShape(10.dp))
                    .clickable {
                        debounceJob?.cancel()
                        val newMs = (state.subtitleDelayMs - 100L).coerceAtLeast(-5000L)
                        sliderValue = (newMs / 1000f).coerceIn(-5.0f, 5.0f)
                        vm.setSubtitleDelay(newMs)
                    },
                color = ExcavPalette.SurfaceCard
            ) {
                Text(
                    text = "-0.1s",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, ExcavPalette.Line, RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickSubtitle),
                color = ExcavPalette.SurfaceCard
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = ExcavPalette.Text,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Add subtitles",
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ExcavPalette.Line, RoundedCornerShape(10.dp))
                    .clickable {
                        debounceJob?.cancel()
                        val newMs = (state.subtitleDelayMs + 100L).coerceAtMost(5000L)
                        sliderValue = (newMs / 1000f).coerceIn(-5.0f, 5.0f)
                        vm.setSubtitleDelay(newMs)
                    },
                color = ExcavPalette.SurfaceCard
            ) {
                Text(
                    text = "+0.1s",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OnlineSubTabContent(
    state: PlayerState,
    vm: ExcavViewModel
) {
    val onlineState by vm.onlineSubtitleState.collectAsStateWithLifecycle()
    val videoTitle = remember(state.currentVideo) {
        val raw = state.currentVideo?.displayName.orEmpty()
        com.excavplayer.subtitles.cleanVideoTitle(raw).formatted
    }

    LaunchedEffect(videoTitle) {
        if (onlineState.query.isBlank() && videoTitle.isNotBlank()) {
            vm.updateSubtitleQuery(videoTitle)
            vm.searchOnlineSubtitles(videoTitle)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
    ) {
        // Search Input Bar (No leading icon, SVG close icon for clear, SVG search icon for action)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, ExcavPalette.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = onlineState.query,
                onValueChange = { vm.updateSubtitleQuery(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(ExcavPalette.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchOnlineSubtitles() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (onlineState.query.isEmpty()) {
                        Text(
                            text = "Search subtitles...",
                            color = ExcavPalette.TextMuted.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            )
            if (onlineState.query.isNotEmpty()) {
                IconButton(
                    onClick = { vm.updateSubtitleQuery("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = ExcavPalette.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(
                onClick = { vm.searchOnlineSubtitles() },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = ExcavPalette.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Content Area: Pure Loading Animation, Error, Results, or Empty
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            when {
                onlineState.isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = ExcavPalette.Primary,
                            strokeWidth = 2.5.dp
                        )
                    }
                }
                onlineState.errorMessage != null && onlineState.searchResults.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = onlineState.errorMessage.orEmpty(),
                            color = ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.searchOnlineSubtitles() },
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "Retry",
                                color = ExcavPalette.Primary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                onlineState.searchResults.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(onlineState.searchResults, key = { "${it.source}_${it.id}" }) { result ->
                            val isDownloading = result.id in onlineState.downloadingIds
                            val isDownloaded = result.id in onlineState.downloadedMap
                            val isApplied = onlineState.appliedSubtitleId == result.id

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isDownloading) {
                                        if (isDownloaded && !isApplied) {
                                            vm.applyDownloadedResult(result)
                                        } else if (!isDownloaded) {
                                            vm.downloadAndApplySubtitle(result, videoTitle)
                                        }
                                    },
                                color = if (isApplied) ExcavPalette.Primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
                                border = if (isApplied) BorderStroke(1.dp, ExcavPalette.Primary.copy(alpha = 0.4f)) else null,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = result.title.ifBlank { result.fileName },
                                            color = ExcavPalette.Text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Language badge
                                            Surface(
                                                color = ExcavPalette.Primary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = result.language.uppercase(),
                                                    color = ExcavPalette.Primary,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            // Source badge
                                            Surface(
                                                color = Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = result.source,
                                                    color = ExcavPalette.TextMuted,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            if (result.hearingImpaired) {
                                                Text(
                                                    text = "CC",
                                                    color = ExcavPalette.TextMuted,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // Action Area: Spinner -> "Applied" -> "Apply" -> Download Icon
                                    Box(
                                        modifier = Modifier.padding(end = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = ExcavPalette.Primary,
                                                strokeWidth = 2.dp
                                            )
                                        } else if (isApplied) {
                                            Surface(
                                                color = ExcavPalette.Primary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Applied",
                                                    color = ExcavPalette.Primary,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else if (isDownloaded) {
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, ExcavPalette.Primary, RoundedCornerShape(8.dp))
                                                    .clickable { vm.applyDownloadedResult(result) },
                                                color = ExcavPalette.Primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "Apply",
                                                    color = ExcavPalette.Primary,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { vm.downloadAndApplySubtitle(result, videoTitle) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Download",
                                                    tint = ExcavPalette.Primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search for online subtitles above",
                            color = ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    }
                }
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
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SleekRadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = if (isSelected) Color.White else ExcavPalette.Text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.5.sp
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
                val label = track.label.takeIf { it.isNotBlank() } ?: track.language ?: "Audio Track"
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

@Composable
fun FloatingSkipChapterPill(
    chapters: List<MediaChapter>,
    playbackPositionFlow: StateFlow<PlaybackPosition>,
    isCompact: Boolean = false,
    onSeekTo: (Long) -> Unit,
    onHud: (String, ImageVector) -> Unit,
    modifier: Modifier = Modifier
) {
    val posState by playbackPositionFlow.collectAsStateWithLifecycle()
    val curPos = posState.currentPositionMs
    val activeChapter = remember(chapters, curPos) {
        chapters.find { curPos >= it.startTimeMs && curPos < it.endTimeMs }
    }
    val isSkippableChapter = activeChapter?.let { it.type == ChapterType.INTRO || it.type == ChapterType.OUTRO || it.type == ChapterType.RECAP } == true

    AnimatedVisibility(
        visible = isSkippableChapter,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(200)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(180)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        activeChapter?.let { chapter ->
            val skipLabel = when (chapter.type) {
                ChapterType.INTRO -> "Skip Intro"
                ChapterType.OUTRO -> "Skip Outro"
                ChapterType.RECAP -> "Skip Recap"
                else -> "Skip Segment"
            }

            Surface(
                modifier = Modifier
                    .clip(ExcavShapes.Pill)
                    .border(1.5.dp, ExcavPalette.BlueGlow, ExcavShapes.Pill)
                    .clickable {
                        onSeekTo(chapter.endTimeMs)
                        onHud(skipLabel, Icons.Default.FastForward)
                    },
                color = ExcavPalette.Ink.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (isCompact) 10.dp else 18.dp,
                        vertical = if (isCompact) 6.dp else 10.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = ExcavPalette.Blue,
                        modifier = Modifier.size(if (isCompact) 15.dp else 20.dp)
                    )
                    Spacer(Modifier.width(if (isCompact) 5.dp else 8.dp))
                    Text(
                        text = skipLabel,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isCompact) 11.5.sp else 14.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingNextEpisodePill(
    chapters: List<MediaChapter>,
    playbackStatus: PlaybackStatus,
    playbackPositionFlow: StateFlow<PlaybackPosition>,
    queue: com.excavplayer.player.queue.QueueState,
    isVeryCompact: Boolean = false,
    isCompact: Boolean = false,
    onPlayNext: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasNextInQueue = queue.currentIndex in 0 until (queue.items.size - 1)
    if (!hasNextInQueue) return

    val posState by playbackPositionFlow.collectAsStateWithLifecycle()
    val curPos = posState.currentPositionMs
    val duration = posState.durationMs
    val remainingMs = duration - curPos
    val isNearEnd = duration > 20_000 && remainingMs in 0L..45_000L
    val isAtEnd = playbackStatus == PlaybackStatus.ENDED
    val activeChapter = remember(chapters, curPos) {
        chapters.find { curPos >= it.startTimeMs && curPos < it.endTimeMs }
    }
    val isOutro = activeChapter?.type == ChapterType.OUTRO
    val isVisible = isNearEnd || isAtEnd || isOutro

    val normalPad = when {
        isVeryCompact -> 82.dp
        isCompact -> 112.dp
        else -> 142.dp
    }
    val outroPad = when {
        isVeryCompact -> 118.dp
        isCompact -> 152.dp
        else -> 188.dp
    }
    val endPad = when {
        isVeryCompact -> 10.dp
        isCompact -> 16.dp
        else -> 24.dp
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(200)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(180)
        ) + fadeOut(tween(150)),
        modifier = modifier
            .padding(bottom = if (isOutro) outroPad else normalPad, end = endPad)
    ) {
        val nextVid = queue.items.getOrNull(queue.currentIndex + 1)
        if (nextVid != null) {
            Surface(
                modifier = Modifier
                    .clip(ExcavShapes.Pill)
                    .border(1.5.dp, ExcavPalette.BlueGlow, ExcavShapes.Pill)
                    .clickable {
                        onPlayNext(queue.currentIndex + 1)
                    },
                color = ExcavPalette.Ink.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (isVeryCompact) 8.dp else if (isCompact) 10.dp else 16.dp,
                        vertical = if (isVeryCompact) 5.dp else if (isCompact) 6.dp else 10.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = ExcavPalette.Blue,
                        modifier = Modifier.size(if (isVeryCompact) 14.dp else if (isCompact) 16.dp else 20.dp)
                    )
                    Spacer(Modifier.width(if (isVeryCompact) 4.dp else if (isCompact) 5.dp else 8.dp))
                    Text(
                        text = "Next: ${nextVid.displayName.take(if (isVeryCompact) 10 else if (isCompact) 14 else 18)}",
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isVeryCompact) 10.5.sp else if (isCompact) 11.5.sp else 13.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SeekbarPreviewPopup(
    scrubState: ScrubState?,
    video: Video?,
    durationMs: Long,
    isVeryCompact: Boolean = false,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val previewLoader = rememberSeekPreviewLoader()
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(video?.id, scrubState?.targetMs) {
        if (scrubState == null || video == null || durationMs <= 0L) {
            previewBitmap = null
            return@LaunchedEffect
        }
        val target = scrubState.targetMs
        val cached = previewLoader.getFromCache(video.id, target)
        if (cached != null) {
            previewBitmap = cached
        } else {
            val uri = android.net.Uri.parse(video.uri)
            val bmp = previewLoader.loadPreview(video.id, uri, target, durationMs)
            previewBitmap = bmp
            if (bmp != null) {
                previewLoader.prefetchAdjacent(video.id, uri, target, durationMs)
            }
        }
    }

    AnimatedVisibility(
        visible = scrubState != null && durationMs > 0L,
        enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.88f, animationSpec = tween(100)),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val totalWidth = maxWidth
            val popupWidth = if (isVeryCompact) 130.dp else if (isCompact) 155.dp else 180.dp
            val popupHeight = if (isVeryCompact) 75.dp else if (isCompact) 88.dp else 102.dp

            val scrubFrac = if (durationMs > 0L && scrubState != null) {
                (scrubState.targetMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            // Calculate x offset centered around the thumb, clamped to container edges
            val targetOffset = totalWidth * scrubFrac - (popupWidth / 2)
            val minOffset = 4.dp
            val maxOffset = (totalWidth - popupWidth - 4.dp).coerceAtLeast(minOffset)
            val clampedOffset = targetOffset.coerceIn(minOffset, maxOffset)

            Box(
                modifier = Modifier
                    .offset(x = clampedOffset)
                    .width(popupWidth)
                    .height(popupHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .background(Color(0xE6121622))
            ) {
                // Extracted Frame or Placeholder
                val bmp = previewBitmap
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Seek Preview",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF181E2C)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ExcavPalette.Blue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom time badge overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatDuration(scrubState?.targetMs ?: 0L),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isVeryCompact) 10.sp else 11.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerTimelineSection(
    playbackPositionFlow: StateFlow<PlaybackPosition>,
    scrubState: ScrubState? = null,
    onScrubStart: () -> Unit = {},
    onScrubChange: (Long) -> Unit = {},
    onScrubEnd: () -> Unit = {},
    chapters: List<MediaChapter> = emptyList(),
    timeTextSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val posState by playbackPositionFlow.collectAsStateWithLifecycle()
    val duration = posState.durationMs.coerceAtLeast(0L)
    val displayPos = (scrubState?.targetMs ?: posState.currentPositionMs).coerceIn(0L, duration.coerceAtLeast(1L))
    val bufferedFrac = if (duration > 0L) (posState.bufferedPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var isRemainingTime by rememberSaveable { mutableStateOf(false) }

    val chapterFractions = remember(chapters, duration) {
        if (duration <= 0L) emptyList()
        else {
            chapters
                .filter { it.startTimeMs >= 0L && it.endTimeMs > it.startTimeMs }
                .sortedBy { it.startTimeMs }
                .map { (it.startTimeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) }
                .distinctBy { (it * 100).toInt() } // Density guard: at least ~1% apart
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRemainingTime) "-${formatDuration((duration - displayPos).coerceAtLeast(0L))}" else formatDuration(displayPos),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = timeTextSize),
                modifier = Modifier.clickable {
                    isRemainingTime = !isRemainingTime
                    onInteraction()
                }
            )
            Text(
                text = formatDuration(duration),
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = timeTextSize)
            )
        }

        Spacer(Modifier.height(4.dp))

        ExcavSleekSlider(
            value = displayPos.toFloat(),
            onValueChange = { onScrubChange(it.toLong()) },
            onValueChangeStarted = onScrubStart,
            onValueChangeFinished = onScrubEnd,
            bufferedFraction = bufferedFrac,
            chapterFractions = chapterFractions,
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// -----------------------------------------------------------------------------------------
// Subtitle Style Application
// -----------------------------------------------------------------------------------------
private fun applySubtitleStyles(
    subtitleView: androidx.media3.ui.SubtitleView?,
    settings: UserSettings
) {
    subtitleView?.apply {
        setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_CANVAS)
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setBottomPaddingFraction(0.08f)

        val fgColor = when (settings.subtitleTextColor) {
            "Yellow" -> android.graphics.Color.parseColor("#FFE066")
            "Cyan" -> android.graphics.Color.parseColor("#00E5FF")
            "Green" -> android.graphics.Color.parseColor("#69F0AE")
            "Amber" -> android.graphics.Color.parseColor("#FFAB40")
            "Pink" -> android.graphics.Color.parseColor("#FF4081")
            else -> android.graphics.Color.WHITE
        }

        val (bgColor, edgeType, edgeColor) = when (settings.subtitleBackgroundStyle) {
            "Background" -> Triple(
                android.graphics.Color.BLACK,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.TRANSPARENT
            )
            "Both" -> Triple(
                android.graphics.Color.BLACK,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK
            )
            "None" -> Triple(
                android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.TRANSPARENT
            )
            else -> Triple(
                android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK
            )
        }

        val captionStyle = androidx.media3.ui.CaptionStyleCompat(
            fgColor,
            bgColor,
            android.graphics.Color.TRANSPARENT,
            edgeType,
            edgeColor,
            null
        )
        setStyle(captionStyle)

        val sizeFraction = when (settings.subtitleTextSize) {
            "Small" -> 0.040f
            "Large" -> 0.068f
            "Extra Large" -> 0.084f
            else -> 0.0533f
        }
        setFractionalTextSize(sizeFraction)
    }
}

// -----------------------------------------------------------------------------------------
// More Options Sheet (Three Dot Menu)
// -----------------------------------------------------------------------------------------
@Composable
private fun MoreOptionsSheet(
    onOpenEqualizer: () -> Unit,
    onOpenSubtitleCustomization: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        MoreOptionItem(
            title = "Volume Equalizer",
            onClick = onOpenEqualizer
        )

        MoreOptionItem(
            title = "Subtitle Customization",
            onClick = onOpenSubtitleCustomization
        )
    }
}

@Composable
private fun MoreOptionItem(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp
            )
        )
    }
}

// -----------------------------------------------------------------------------------------
// Equalizer Sheet & Presets
// -----------------------------------------------------------------------------------------
private enum class EqualizerPreset { CUSTOM, BALANCED, BASS_BOOST }

@Composable
private fun EqualizerPillTabBar(
    selectedPreset: EqualizerPreset,
    onPresetSelected: (EqualizerPreset) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tabs = remember {
        listOf(
            EqualizerPreset.CUSTOM to "Custom",
            EqualizerPreset.BALANCED to "Balanced",
            EqualizerPreset.BASS_BOOST to "Bass Boost"
        )
    }
    val selectedIndex = selectedPreset.ordinal

    val targetOffsetFraction by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "eqPillOffset"
    )

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(3.dp)
    ) {
        Layout(
            content = {
                // Animated Highlight Pill Background
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ExcavPalette.Primary, ExcavPalette.Primary.copy(alpha = 0.85f))
                            )
                        )
                )
                // Tabs
                tabs.forEach { (preset, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(17.dp))
                            .clickable(
                                enabled = enabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onPresetSelected(preset) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selectedPreset == preset) Color.White else ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedPreset == preset) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val indicatorMeasurable = measurables[0]
            val tabMeasurables = measurables.drop(1)
            val tabPlaceables = tabMeasurables.map { it.measure(constraints) }

            val totalWidth = tabPlaceables.sumOf { it.width }
            val maxHeight = tabPlaceables.maxOf { it.height }

            val currentIndex = targetOffsetFraction.toInt().coerceIn(0, tabPlaceables.size - 1)
            val nextIndex = (currentIndex + 1).coerceAtMost(tabPlaceables.size - 1)
            val fraction = targetOffsetFraction - currentIndex

            val currentWidth = tabPlaceables[currentIndex].width
            val nextWidth = tabPlaceables[nextIndex].width
            val indicatorWidth = (currentWidth + (nextWidth - currentWidth) * fraction).roundToInt()

            var currentXOffset = 0
            for (i in 0 until currentIndex) {
                currentXOffset += tabPlaceables[i].width
            }
            val targetXOffset = currentXOffset + (tabPlaceables[currentIndex].width * fraction).roundToInt()

            val indicatorPlaceable = indicatorMeasurable.measure(
                androidx.compose.ui.unit.Constraints.fixed(indicatorWidth, maxHeight)
            )

            layout(totalWidth, maxHeight) {
                indicatorPlaceable.placeRelative(targetXOffset, 0)
                var x = 0
                tabPlaceables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width
                }
            }
        }
    }
}

@Composable
private fun EqualizerSheet(
    equalizerState: EqualizerState,
    vm: ExcavViewModel,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedPreset = remember(equalizerState.preset, equalizerState.enabled) {
        if (!equalizerState.enabled) {
            EqualizerPreset.CUSTOM
        } else {
            when (equalizerState.preset) {
                "Balanced" -> EqualizerPreset.BALANCED
                "Bass Boost" -> EqualizerPreset.BASS_BOOST
                else -> EqualizerPreset.CUSTOM
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
    ) {
        // Top Row: Back Button, Title, and On/Off Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ExcavPalette.Text,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Equalizer",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }

            // Top-right SleekSwitch (off by default; when turned on, goes directly to Custom preset)
            SleekSwitch(
                checked = equalizerState.enabled,
                onCheckedChange = { isEnabled ->
                    if (isEnabled) {
                        vm.setEqualizerPreset("Custom")
                        vm.setEqualizerEnabled(true)
                    } else {
                        vm.setEqualizerEnabled(false)
                    }
                }
            )
        }

        // Animated Pill Tab Bar for 3 presets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            EqualizerPillTabBar(
                selectedPreset = selectedPreset,
                onPresetSelected = { preset ->
                    if (equalizerState.enabled) {
                        val presetStr = when (preset) {
                            EqualizerPreset.CUSTOM -> "Custom"
                            EqualizerPreset.BALANCED -> "Balanced"
                            EqualizerPreset.BASS_BOOST -> "Bass Boost"
                        }
                        vm.setEqualizerPreset(presetStr)
                    }
                },
                enabled = equalizerState.enabled
            )
        }

        // Animated horizontal page transitions matching Tracks & Online Subtitles
        AnimatedContent(
            targetState = selectedPreset,
            transitionSpec = {
                val targetIndex = targetState.ordinal
                val initialIndex = initialState.ordinal
                if (targetIndex > initialIndex) {
                    (slideInHorizontally { width -> width / 3 } + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally { width -> -width / 3 } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally { width -> width / 3 } + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "equalizerPresetTransition"
        ) { preset ->
            when (preset) {
                EqualizerPreset.CUSTOM -> CustomEqualizerTabContent(
                    isEnabled = equalizerState.enabled,
                    bandLevelsStr = equalizerState.bandLevels,
                    onUpdateBandLevels = { vm.setEqualizerBandLevels(it) }
                )
                EqualizerPreset.BALANCED -> BalancedEqualizerTabContent(
                    isEnabled = equalizerState.enabled
                )
                EqualizerPreset.BASS_BOOST -> BassBoostEqualizerTabContent(
                    isEnabled = equalizerState.enabled,
                    bassStrength = equalizerState.bassBoostStrength,
                    onUpdateBassStrength = { vm.setBassBoostStrength(it) }
                )
            }
        }
    }
}

@Composable
private fun CustomEqualizerTabContent(
    isEnabled: Boolean,
    bandLevelsStr: String,
    onUpdateBandLevels: (String) -> Unit
) {
    val bandLabels = remember { listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz") }
    val levels = remember(bandLevelsStr) {
        val parsed = bandLevelsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        List(5) { i -> parsed.getOrNull(i) ?: 0 }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        if (!isEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Equalizer is Off. Toggle switch above to activate.",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                )
            }
        }

        bandLabels.forEachIndexed { index, label ->
            val level = levels.getOrNull(index) ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = if (isEnabled) ExcavPalette.Text else ExcavPalette.TextMuted.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp
                    ),
                    modifier = Modifier.width(62.dp)
                )
                ExcavSleekSlider(
                    value = level.toFloat(),
                    onValueChange = { newVal ->
                        val updated = levels.toMutableList()
                        updated[index] = newVal.roundToInt()
                        onUpdateBandLevels(updated.joinToString(","))
                    },
                    valueRange = -10f..10f,
                    enabled = isEnabled,
                    showZeroMarker = false,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (level > 0) "+$level dB" else "$level dB",
                    color = if (isEnabled) (if (level != 0) ExcavPalette.Primary else ExcavPalette.TextMuted) else ExcavPalette.TextMuted.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Reset to Flat button (clean text without coiled/refresh icon)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = isEnabled) {
                        onUpdateBandLevels("0,0,0,0,0")
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset to Flat",
                    color = if (isEnabled) ExcavPalette.TextMuted else ExcavPalette.TextMuted.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun BalancedEqualizerTabContent(
    isEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        if (!isEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Equalizer is Off. Toggle switch above to activate.",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Balanced Acoustic Profile",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    )
                )

                Spacer(Modifier.height(14.dp))

                // Frequency response badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EqBadge(label = "Bass: Flat 0dB", active = isEnabled, modifier = Modifier.weight(1f))
                    EqBadge(label = "Mids: Clean 0dB", active = isEnabled, modifier = Modifier.weight(1f))
                    EqBadge(label = "Treble: +1dB", active = isEnabled, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BassBoostEqualizerTabContent(
    isEnabled: Boolean,
    bassStrength: Int,
    onUpdateBassStrength: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        if (!isEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Equalizer is Off. Toggle switch above to activate.",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Cinema Bass Boost",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    )
                )

                Spacer(Modifier.height(14.dp))

                // Hardware Bass Boost Strength Slider
                val strengthPercent = if (bassStrength == 0) 70 else (bassStrength / 10).coerceIn(0, 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass Intensity",
                        color = if (isEnabled) ExcavPalette.Text else ExcavPalette.TextMuted.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "$strengthPercent%",
                        color = if (isEnabled) ExcavPalette.Primary else ExcavPalette.TextMuted.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(Modifier.height(4.dp))

                ExcavSleekSlider(
                    value = strengthPercent.toFloat(),
                    onValueChange = { onUpdateBassStrength((it * 10).toInt()) },
                    valueRange = 0f..100f,
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EqBadge(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) ExcavPalette.Primary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (active) ExcavPalette.Primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                CircleShape
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) ExcavPalette.Primary else ExcavPalette.TextMuted,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
            textAlign = TextAlign.Center
        )
    }
}

// -----------------------------------------------------------------------------------------
// Subtitle Customization Sheet
// -----------------------------------------------------------------------------------------
@Composable
private fun SubtitleCustomizationSheet(
    settings: UserSettings,
    vm: ExcavViewModel,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = remember {
        listOf(
            "White" to Color(0xFFFFFFFF),
            "Yellow" to Color(0xFFFFE066),
            "Cyan" to Color(0xFF00E5FF),
            "Green" to Color(0xFF69F0AE),
            "Amber" to Color(0xFFFFAB40),
            "Pink" to Color(0xFFFF4081)
        )
    }
    val sizes = remember { listOf("Small", "Normal", "Large", "Extra Large") }
    val styles = remember {
        listOf(
            "Border" to "Border",
            "Background" to "Background",
            "None" to "None"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ExcavPalette.Text,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Subtitle Style",
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }

        // Live Subtitle Preview Box
        val previewFgColor = when (settings.subtitleTextColor) {
            "Yellow" -> Color(0xFFFFE066)
            "Cyan" -> Color(0xFF00E5FF)
            "Green" -> Color(0xFF69F0AE)
            "Amber" -> Color(0xFFFFAB40)
            "Pink" -> Color(0xFFFF4081)
            else -> Color.White
        }
        val previewFontSize = when (settings.subtitleTextSize) {
            "Small" -> 13.sp
            "Large" -> 19.sp
            "Extra Large" -> 22.sp
            else -> 16.sp
        }
        val hasBg = settings.subtitleBackgroundStyle == "Background" || settings.subtitleBackgroundStyle == "Both"
        val hasBorder = settings.subtitleBackgroundStyle == "Border" || settings.subtitleBackgroundStyle == "Both" || settings.subtitleBackgroundStyle == "Outline"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1B2333), Color(0xFF0E131F))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (hasBg) Modifier.background(Color.Black.copy(alpha = 0.95f)).padding(horizontal = 8.dp, vertical = 2.dp)
                        else Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
            ) {
                Text(
                    text = "ExcavPlayer Subtitle Preview",
                    color = previewFgColor,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = previewFontSize,
                        fontWeight = FontWeight.Bold,
                        shadow = if (hasBorder) androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset = androidx.compose.ui.geometry.Offset(1.5f, 1.5f),
                            blurRadius = 3f
                        ) else null
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Color Section
        Text(
            text = "SUBTITLE COLOR",
            color = ExcavPalette.TextMuted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { (colorName, colorVal) ->
                val isSelected = settings.subtitleTextColor.equals(colorName, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colorVal)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) ExcavPalette.Primary else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable { vm.setSubtitleTextColor(colorName) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (colorName == "White" || colorName == "Yellow" || colorName == "Cyan" || colorName == "Green") Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Text Size Section
        Text(
            text = "SUBTITLE SIZE",
            color = ExcavPalette.TextMuted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(Modifier.height(8.dp))
        val sizeIndex = sizes.indexOfFirst { it.equals(settings.subtitleTextSize, ignoreCase = true) }.coerceAtLeast(1)
        SegmentedAnimatedPillBar(
            options = sizes,
            selectedIndex = sizeIndex,
            onSelectIndex = { index ->
                vm.setSubtitleTextSize(sizes[index])
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // Border & Background Style Section
        Text(
            text = "BORDER & BACKGROUND",
            color = ExcavPalette.TextMuted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(Modifier.height(8.dp))
        val currentStyle = if (settings.subtitleBackgroundStyle.equals("Outline", ignoreCase = true) || settings.subtitleBackgroundStyle.equals("Both", ignoreCase = true)) "Border" else settings.subtitleBackgroundStyle
        val styleIndex = styles.indexOfFirst { it.first.equals(currentStyle, ignoreCase = true) }.coerceAtLeast(0)
        SegmentedAnimatedPillBar(
            options = styles.map { it.second },
            selectedIndex = styleIndex,
            onSelectIndex = { index ->
                vm.setSubtitleBackgroundStyle(styles[index].first)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// -----------------------------------------------------------------------------------------
// Segmented Animated Pill Bar (Bouncy Spring Highlight matching Tracks / Presets)
// -----------------------------------------------------------------------------------------
@Composable
private fun SegmentedAnimatedPillBar(
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetOffsetFraction by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "segmentedPillOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(3.dp)
    ) {
        Layout(
            content = {
                // Animated Highlight Pill Background with spring glide
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ExcavPalette.Primary, ExcavPalette.Primary.copy(alpha = 0.85f))
                            )
                        )
                )
                // Segment items
                options.forEachIndexed { index, label ->
                    val isSelected = selectedIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(17.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectIndex(index) }
                            .defaultMinSize(minHeight = 36.dp)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val indicatorMeasurable = measurables[0]
            val tabMeasurables = measurables.drop(1)
            val tabCount = tabMeasurables.size
            val tabWidth = constraints.maxWidth / tabCount

            val tabPlaceables = tabMeasurables.map {
                it.measure(
                    androidx.compose.ui.unit.Constraints(
                        minWidth = tabWidth,
                        maxWidth = tabWidth,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight
                    )
                )
            }
            val maxHeight = tabPlaceables.maxOfOrNull { it.height } ?: 0

            val indicatorPlaceable = indicatorMeasurable.measure(
                androidx.compose.ui.unit.Constraints.fixed(tabWidth, maxHeight)
            )

            val indicatorXOffset = (targetOffsetFraction * tabWidth).roundToInt()

            layout(constraints.maxWidth, maxHeight) {
                indicatorPlaceable.placeRelative(indicatorXOffset, 0)
                tabPlaceables.forEachIndexed { i, placeable ->
                    placeable.placeRelative(i * tabWidth, 0)
                }
            }
        }
    }
}

