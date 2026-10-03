@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.excavplayer.ui.player

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
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

private enum class PlayerSheet { SUBTITLES, AUDIO, SPEED, CHAPTERS }
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
    val state by vm.playerState.collectAsStateWithLifecycle()
    val queue by vm.queueState.collectAsStateWithLifecycle()
    val settings by vm.userSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
    }
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
    var gestureHudIsBoost by remember { mutableStateOf(false) }

    var doubleTapSeekSide by remember { mutableStateOf<DoubleTapSide?>(null) }
    var doubleTapSeekAccumulatedSeconds by remember { mutableIntStateOf(10) }

    var lastDoubleTapSeekTime by remember { mutableLongStateOf(0L) }
    var activeSurfaceView by remember { mutableStateOf<android.view.SurfaceView?>(null) }

    // Subtle background blur when an option sheet is opened
    val sheetBlurRadius by animateDpAsState(
        targetValue = if (activeSheet != null) 6.dp else 0.dp,
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
                            when {
                                offset.x < width * 0.35f -> {
                                    if (doubleTapSeekSide == DoubleTapSide.LEFT && now - lastDoubleTapSeekTime < 750) {
                                        doubleTapSeekAccumulatedSeconds += 10
                                    } else {
                                        doubleTapSeekAccumulatedSeconds = 10
                                    }
                                    lastDoubleTapSeekTime = now
                                    doubleTapSeekSide = DoubleTapSide.LEFT
                                    vm.player.seekBackward(10_000)
                                }
                                offset.x > width * 0.65f -> {
                                    if (doubleTapSeekSide == DoubleTapSide.RIGHT && now - lastDoubleTapSeekTime < 750) {
                                        doubleTapSeekAccumulatedSeconds += 10
                                    } else {
                                        doubleTapSeekAccumulatedSeconds = 10
                                    }
                                    lastDoubleTapSeekTime = now
                                    doubleTapSeekSide = DoubleTapSide.RIGHT
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
                    detectTapGestures(onTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) {
                            onUserInteraction()
                        }
                    })
                }
            }
            .pointerInput(isLocked, settings, zoomScale) {
                if (!isLocked && zoomScale <= 1.05f) {
                    var isDragEligible = false
                    var totalVerticalDrag = 0f
                    var currentEffectiveVol = 1.0f
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // Ignore touches in top 20% (status bar pull-down zone) and bottom 18% (nav bar zone)
                            val safeTop = size.height * 0.20f
                            val safeBottom = size.height * 0.82f
                            val safeLeft = size.width * 0.08f
                            val safeRight = size.width * 0.92f
                            isDragEligible = startOffset.y in safeTop..safeBottom && startOffset.x in safeLeft..safeRight
                            totalVerticalDrag = 0f
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
                            isDragEligible = false
                            gestureHudText = null
                            gestureHudIcon = null
                            gestureHudIsBoost = false
                        },
                        onDragCancel = {
                            isDragEligible = false
                            gestureHudText = null
                            gestureHudIcon = null
                            gestureHudIsBoost = false
                        },
                        onDrag = { change, dragAmount ->
                            if (!isDragEligible) return@detectDragGestures
                            totalVerticalDrag += Math.abs(dragAmount.y)
                            if (totalVerticalDrag < 12f) return@detectDragGestures

                            change.consume()
                            val isLeft = change.position.x < size.width / 2
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
                                            vm.player.setVolume(1.0f)
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
                                        vm.player.setVolume(currentEffectiveVol)
                                        val volPercent = (currentEffectiveVol * 100f).roundToInt()
                                        gestureHudText = "Volume $volPercent% (Boost)"
                                        gestureHudProgress = (currentEffectiveVol - 1.0f).coerceIn(0f, 1f)
                                        gestureHudIsBoost = true
                                        gestureHudIcon = Icons.AutoMirrored.Filled.VolumeUp
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
            state = state,
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
            state = state,
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
            val lockBtnSize = if (isVeryCompact) 32.dp else if (isCompact) 38.dp else 44.dp
            val lockIconSize = if (isVeryCompact) 16.dp else if (isCompact) 19.dp else 22.dp
            val lockPadH = if (isVeryCompact) 8.dp else 16.dp
            val lockPadV = if (isVeryCompact) 4.dp else 10.dp

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
                    LocalRippleConfiguration provides null
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
        } else {
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
                        onClose = {
                            if (!keepAudioOnBackground && !state.isBackgroundAudio) {
                                vm.player.stop()
                            }
                            onClose()
                        },
                        onOpenSheet = {
                            onUserInteraction()
                            activeSheet = it
                        },
                        onLock = {
                            onUserInteraction()
                            isLocked = true
                            vm.player.dispatch(PlayerCommand.SetScreenLocked(true))
                        },
                        onPip = {
                            onUserInteraction()
                            activity?.let {
                                pipHelper.enterPictureInPicture(it, state.currentVideo, state.playback.isPlaying)
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
                            onUserInteraction()
                            vm.playQueueItem(queue.currentIndex - 1)
                        },
                        hasNext = queue.currentIndex in 0 until (queue.items.size - 1),
                        onPlayNext = {
                            onUserInteraction()
                            vm.playQueueItem(queue.currentIndex + 1)
                        },
                        onInteraction = onUserInteraction
                    )
                }
            }
        }

        // Active Bottom Sheet with smooth entering and exiting animation
        AnimatedVisibility(
            visible = activeSheet != null,
            enter = slideInVertically(
                initialOffsetY = { (it * 0.40f).toInt() },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(
                targetOffsetY = { (it * 0.35f).toInt() },
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing))
        ) {
            activeSheet?.let { sheetType ->
                SheetContainer(
                    sheet = sheetType,
                    state = state,
                    vm = vm,
                    maxSheetHeight = maxHeight,
                    onDismiss = {
                        onUserInteraction()
                        activeSheet = null
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
        LocalRippleConfiguration provides null
    ) {
        val topBtnSize = when {
            isVeryCompact -> 32.dp
            isCompact -> 38.dp
            else -> 44.dp
        }
        val topIconSize = when {
            isVeryCompact -> 16.dp
            isCompact -> 19.dp
            else -> 22.dp
        }
        val titleFontSize = when {
            isVeryCompact -> 12.sp
            isCompact -> 14.sp
            else -> 16.sp
        }
        val topSpacing = when {
            isVeryCompact -> 6.dp
            isCompact -> 10.dp
            else -> 16.dp
        }
        val topBtnSpacing = when {
            isVeryCompact -> 4.dp
            isCompact -> 6.dp
            else -> 10.dp
        }

        val centerPlaySize = when {
            isVeryCompact -> 44.dp
            isCompact -> 56.dp
            else -> 68.dp
        }
        val centerPlayIconSize = when {
            isVeryCompact -> 24.dp
            isCompact -> 30.dp
            else -> 38.dp
        }
        val centerSkipSize = when {
            isVeryCompact -> 34.dp
            isCompact -> 42.dp
            else -> 52.dp
        }
        val centerSkipIconSize = when {
            isVeryCompact -> 18.dp
            isCompact -> 22.dp
            else -> 28.dp
        }
        val centerSpacing = when {
            isVeryCompact -> 14.dp
            isCompact -> 20.dp
            else -> 28.dp
        }

        val bottomPillBtnSize = when {
            isVeryCompact -> 28.dp
            isCompact -> 34.dp
            else -> 40.dp
        }
        val bottomPillIconSize = when {
            isVeryCompact -> 14.dp
            isCompact -> 18.dp
            else -> 22.dp
        }
        val bottomPillPaddingH = when {
            isVeryCompact -> 4.dp
            isCompact -> 6.dp
            else -> 8.dp
        }
        val bottomPillPaddingV = when {
            isVeryCompact -> 2.dp
            isCompact -> 3.dp
            else -> 4.dp
        }
        val bottomPillSpacing = when {
            isVeryCompact -> 2.dp
            else -> 4.dp
        }
        val speedTextSize = when {
            isVeryCompact -> 10.sp
            isCompact -> 11.5.sp
            else -> 13.sp
        }
        val speedPaddingH = when {
            isVeryCompact -> 6.dp
            isCompact -> 9.dp
            else -> 12.dp
        }
        val speedPaddingV = when {
            isVeryCompact -> 4.dp
            isCompact -> 6.dp
            else -> 8.dp
        }
        val timelineTextSize = when {
            isVeryCompact -> 10.5.sp
            isCompact -> 12.sp
            else -> 14.sp
        }
        val timelineSpacing = when {
            isVeryCompact -> 4.dp
            isCompact -> 8.dp
            else -> 14.dp
        }
        val outerPaddingH = when {
            isVeryCompact -> 8.dp
            isCompact -> 12.dp
            else -> 16.dp
        }
        val outerPaddingV = when {
            isVeryCompact -> 4.dp
            isCompact -> 8.dp
            else -> 10.dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = outerPaddingH, vertical = outerPaddingV)
        ) {
            // Top Bar: Back button, Marquee Title, Dialogue Booster, Subtitle (CC), Audio Track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isVeryCompact) 0.dp else 4.dp)
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

                // Dialogue Booster Button
                IconButton(
                    onClick = {
                        onInteraction()
                        onToggleDialogueBoost()
                    },
                    modifier = Modifier
                        .size(topBtnSize)
                        .playerGlass(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Dialogue Booster",
                        tint = if (isDialogueBoostActive) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(topIconSize)
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
                    enabled = hasPrevious,
                    onClick = {
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
                    enabled = hasNext,
                    onClick = {
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
                // Bottom Progress Bar and Clean Action Buttons (isolated timeline scrubber)
                PlayerTimelineSection(
                    positionMs = state.playback.currentPositionMs,
                    durationMs = state.playback.durationMs,
                    timeTextSize = timelineTextSize,
                    onSeek = {
                        onInteraction()
                        player.seekTo(it)
                    }
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

// -----------------------------------------------------------------------------------------
// Bottom Sheet Containers
// -----------------------------------------------------------------------------------------
@Composable
private fun SheetContainer(
    sheet: PlayerSheet,
    state: PlayerState,
    vm: ExcavViewModel,
    maxSheetHeight: androidx.compose.ui.unit.Dp = 420.dp,
    onDismiss: () -> Unit,
    onPickSubtitle: () -> Unit
) {
    val settings by vm.userSettings.collectAsStateWithLifecycle()

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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(containerPadding),
        contentAlignment = alignment
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
                        PlayerSheet.SUBTITLES -> 360.dp
                        PlayerSheet.CHAPTERS -> 380.dp
                        PlayerSheet.AUDIO -> 340.dp
                        PlayerSheet.SPEED -> 320.dp
                    }
                )
                .heightIn(max = (maxSheetHeight * 0.85f).coerceAtLeast(180.dp))
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
    state: PlayerState,
    isCompact: Boolean = false,
    onSeekTo: (Long) -> Unit,
    onHud: (String, ImageVector) -> Unit,
    modifier: Modifier = Modifier
) {
    val curPos = state.playback.currentPositionMs
    val activeChapter = remember(state.chapters, curPos) {
        state.chapters.find { curPos >= it.startTimeMs && curPos < it.endTimeMs }
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
    state: PlayerState,
    queue: com.excavplayer.player.queue.QueueState,
    isVeryCompact: Boolean = false,
    isCompact: Boolean = false,
    onPlayNext: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasNextInQueue = queue.currentIndex in 0 until (queue.items.size - 1)
    if (!hasNextInQueue) return

    val curPos = state.playback.currentPositionMs
    val duration = state.playback.durationMs
    val remainingMs = duration - curPos
    val isNearEnd = duration > 20_000 && remainingMs in 0L..45_000L
    val isAtEnd = state.playback.playbackStatus == PlaybackStatus.ENDED
    val activeChapter = remember(state.chapters, curPos) {
        state.chapters.find { curPos >= it.startTimeMs && curPos < it.endTimeMs }
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
        val nextVid = queue.items[queue.currentIndex + 1]
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

@Composable
fun PlayerTimelineSection(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    timeTextSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    modifier: Modifier = Modifier
) {
    val duration = durationMs.coerceAtLeast(0L)
    val position = positionMs.coerceIn(0L, duration.coerceAtLeast(1L))

    Column(modifier = modifier.fillMaxWidth()) {
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = timeTextSize)
            )
            Text(
                text = formatDuration(duration),
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = timeTextSize)
            )
        }

        Spacer(Modifier.height(4.dp))

        ExcavSleekSlider(
            value = position.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

