package com.excavplayer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.lerp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.excavplayer.ui.components.backdrop.backdrops.LayerBackdrop
import com.excavplayer.ui.components.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.excavplayer.R
import com.excavplayer.ui.ExcavViewModel
import com.excavplayer.ui.components.*
import com.excavplayer.domain.derivation.LibraryDerivation
import com.excavplayer.domain.model.Folder
import com.excavplayer.ui.folders.*
import com.excavplayer.ui.home.HomeScreen
import com.excavplayer.ui.player.PlayerScreen
import com.excavplayer.ui.search.SearchScreen
import com.excavplayer.ui.settings.SettingsScreen
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes
import com.excavplayer.ui.theme.ExcavMotion
import com.excavplayer.ui.theme.ExcavEaseDrawer
import com.excavplayer.ui.theme.ExcavEaseOut
import com.excavplayer.ui.theme.excavTween
import com.excavplayer.ui.theme.rememberSystemReducedMotion

@Composable
fun ExcavApp(
    vm: ExcavViewModel,
    onPickSubtitle: () -> Unit
) {
    val reducedMotion = rememberSystemReducedMotion()
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val playerOpen by vm.isPlayerOpen.collectAsStateWithLifecycle()

    val library by vm.libraryState.collectAsStateWithLifecycle()
    val settings by vm.userSettings.collectAsStateWithLifecycle()
    val userMessage by vm.userMessage.collectAsStateWithLifecycle()

    var restoredFolder by rememberSaveable { mutableStateOf(false) }

    // Restore last opened folder on initial load if present
    LaunchedEffect(library.folders, settings.lastOpenedFolder) {
        if (!restoredFolder && library.folders.isNotEmpty()) {
            val lastPath = settings.lastOpenedFolder
            if (lastPath != null && library.selectedFolder == null) {
                if (LibraryDerivation.isExcludedFolderPath(lastPath)) {
                    vm.setLastOpenedFolder(null)
                } else {
                    val matched = library.folders.find { it.path == lastPath }
                    if (matched != null) {
                        vm.openFolder(matched)
                    } else {
                        val name = lastPath.trimEnd('/').substringAfterLast('/')
                        vm.openFolder(Folder(name = name, path = lastPath, videoCount = 0, totalSizeBytes = 0L))
                    }
                }
            }
            restoredFolder = true
        }
    }

    BackHandler(enabled = !playerOpen && (searchOpen || (tab != MainTab.FOLDERS && library.selectedFolder != null) || tab != MainTab.HOME)) {
        if (searchOpen) searchOpen = false
        else if (tab != MainTab.HOME) tab = MainTab.HOME
        else vm.closeFolder()
    }

    val hazeState = rememberHazeState()

    val homeGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val foldersGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    Scaffold(
        containerColor = ExcavPalette.Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val appBackdrop = LocalAppBackdrop.current as? LayerBackdrop
                val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported() && appBackdrop != null
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .then(if (useGlass) Modifier.layerBackdrop(appBackdrop!!) else Modifier)
                ) {
                    if (library.loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                RoseThreeLoader(size = 32.dp)
                            }
                        } else {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    if (reducedMotion) {
                                        fadeIn(tween(120)).togetherWith(fadeOut(tween(120)))
                                    } else if (targetState.ordinal > initialState.ordinal) {
                                        (slideInHorizontally(excavTween(ExcavMotion.Ui, ExcavEaseDrawer)) { it / 4 } +
                                            fadeIn(excavTween(ExcavMotion.Ui)))
                                            .togetherWith(slideOutHorizontally(excavTween(ExcavMotion.Press, ExcavEaseDrawer)) { -it / 4 } +
                                                fadeOut(excavTween(ExcavMotion.Press)))
                                    } else {
                                        (slideInHorizontally(excavTween(ExcavMotion.Ui, ExcavEaseDrawer)) { -it / 4 } +
                                            fadeIn(excavTween(ExcavMotion.Ui)))
                                            .togetherWith(slideOutHorizontally(excavTween(ExcavMotion.Press, ExcavEaseDrawer)) { it / 4 } +
                                                fadeOut(excavTween(ExcavMotion.Press)))
                                    }
                                },
                                label = "tab_content"
                            ) { currentTab ->
                                when (currentTab) {
                                MainTab.HOME -> HomeScreen(
                                    continueWatching = if (settings.continueWatchingEnabled) library.continueWatching else emptyList(),
                                    folders = library.folders,
                                    videos = library.videos,
                                    pagedVideos = vm.pagedVideos,
                                    groups = library.groups,
                                    onPlay = { vid, list ->
                                        vm.play(vid, list)
                                    },
                                    onOpenFolder = { folder ->
                                        vm.openFolder(folder)
                                        tab = MainTab.FOLDERS
                                    },
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo,
                                    onDeleteVideos = { vm.deleteVideos(it) },
                                    onRemoveFromContinueWatching = { vm.dismissFromContinueWatching(it.id) },
                                    gridState = homeGridState,
                                    onSearch = { searchOpen = true },
                                    onRefresh = { vm.refreshLibrary() }
                                )
                                MainTab.FOLDERS -> FoldersScreen(
                                    folders = library.folders,
                                    selectedFolder = library.selectedFolder,
                                    folderVideos = library.folderVideos,
                                    videos = library.videos,
                                    folderVideosMap = library.folderVideosMap,
                                    onSelectFolder = { folder ->
                                        if (folder == null) vm.closeFolder() else vm.openFolder(folder)
                                    },
                                    onPlay = { vid, list ->
                                        vm.play(vid, list)
                                    },
                                    onRenameVideo = vm::renameVideo,
                                    onDeleteVideo = vm::deleteVideo,
                                    onDeleteVideos = { vm.deleteVideos(it) },
                                    onDeleteFolders = { vm.deleteFolders(it) },
                                    onRenameFolder = { folder, newName -> vm.renameFolder(folder, newName) },
                                    gridState = foldersGridState,
                                    onSearch = { searchOpen = true },
                                    onRefresh = { vm.refreshLibrary() }
                                )
                                MainTab.SETTINGS -> SettingsScreen(
                                    vm = vm,
                                    onBack = { tab = MainTab.HOME }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = searchOpen,
                        enter = if (reducedMotion) fadeIn(tween(140)) else (slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(180))),
                        exit = if (reducedMotion) fadeOut(tween(120)) else (slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(160))),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SearchScreen(
                            query = library.searchQuery,
                            results = library.searchResults,
                            onQueryChange = vm::setSearchQuery,
                            onBack = { searchOpen = false },
                            onPlay = {
                                vm.play(it)
                            },
                            onRenameVideo = vm::renameVideo,
                            onDeleteVideo = vm::deleteVideo
                        )
                    }
                }

                // Coordinated Floating Bottom Dock (Navbar + Dynamic Background Play Pill)
                AnimatedVisibility(
                    visible = !searchOpen,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val isScrolledDown by remember {
                        derivedStateOf {
                            when (tab) {
                                MainTab.HOME -> homeGridState.firstVisibleItemIndex > 0 || homeGridState.firstVisibleItemScrollOffset > 30
                                MainTab.FOLDERS -> foldersGridState.firstVisibleItemIndex > 0 || foldersGridState.firstVisibleItemScrollOffset > 30
                                else -> false
                            }
                        }
                    }

                    BottomDock(
                        vm = vm,
                        tab = tab,
                        onSelectTab = { tab = it },
                        onOpenSearch = { searchOpen = true },
                        playerOpen = playerOpen,
                        isScrolledDown = isScrolledDown
                    )
                }

            // Top Floating Message / Error Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                UserMessageHost(
                    message = userMessage,
                    onDismiss = { vm.clearUserMessage() }
                )
            }

            // In-App Update Dialog (Release Notes, Download Progress, and Installation)
            val updateState by vm.updateState.collectAsStateWithLifecycle()
            UpdateDialog(
                updateState = updateState,
                onDownload = { asset -> vm.downloadAndInstallUpdate(asset) },
                onInstall = { file -> vm.installApk(file) },
                onDismiss = { vm.dismissUpdate() }
            )

            // Fullscreen Player with slide and fade in animation, and smooth slide and fade out
            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(220)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 3 },
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerScreen(
                    vm = vm,
                    onClose = { vm.setPlayerOpen(false) },
                    onPickSubtitle = onPickSubtitle
                )
            }

            // Fullscreen Blocking Refresh Overlay with RoseThreeLoader
            val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()

            if (isRefreshing) {
                BackHandler { /* Disallow back navigation while refreshing */ }
            }

            AnimatedVisibility(
                visible = isRefreshing,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    RoseThreeLoader(size = 48.dp)
                }
            }
        }
    }
}
}

@Composable
fun BottomDock(
    vm: ExcavViewModel,
    tab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onOpenSearch: () -> Unit,
    playerOpen: Boolean,
    isScrolledDown: Boolean,
    modifier: Modifier = Modifier
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val isBackgroundAudioActive = !playerOpen && playerState.currentVideo != null && (playerState.isBackgroundAudio || playerState.playback.isPlaying)
    var isDockManuallyExpanded by rememberSaveable { mutableStateOf(false) }

    // When full player opens or closes, reset manual expansion
    LaunchedEffect(playerOpen) {
        if (playerOpen) {
            isDockManuallyExpanded = false
        }
    }
    // When playback stops, reset manual expansion
    LaunchedEffect(isBackgroundAudioActive) {
        if (!isBackgroundAudioActive) {
            isDockManuallyExpanded = false
        }
    }

    // Reset manual expansion if user scrolls back to the very top
    LaunchedEffect(isScrolledDown) {
        if (!isScrolledDown) {
            isDockManuallyExpanded = false
        }
    }

    // Dock collapses when scrolled down unless manually expanded
    val isCollapsed = isBackgroundAudioActive && isScrolledDown && !isDockManuallyExpanded

    val dockSpring = remember {
        spring<Float>(
            dampingRatio = 0.72f,
            stiffness = 320f
        )
    }
    val dockDpSpring = remember {
        spring<androidx.compose.ui.unit.Dp>(
            dampingRatio = 0.72f,
            stiffness = 320f
        )
    }

    val audioActiveProgress by animateFloatAsState(
        targetValue = if (isBackgroundAudioActive) 1f else 0f,
        animationSpec = dockSpring,
        label = "dockAudioActive"
    )

    val collapseProgress by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = dockSpring,
        label = "dockCollapse"
    )

    val barHeight = 62.dp
    val circleButtonSize = 62.dp
    val spacing = 8.dp

    val totalDockHeight by animateDpAsState(
        targetValue = if (isBackgroundAudioActive && !isCollapsed) (barHeight * 2f + spacing) else barHeight,
        animationSpec = dockDpSpring,
        label = "dockTotalHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = 15.dp)))
            .padding(start = 19.dp, end = 19.dp, bottom = 6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalDockHeight)
        ) {
            val totalWidth = maxWidth
            val searchWidth = circleButtonSize
            val expandedNavbarWidth = (totalWidth - searchWidth - spacing).coerceAtLeast(circleButtonSize)
            val collapsedNavbarWidth = circleButtonSize
            val currentNavbarWidth = lerp(expandedNavbarWidth, collapsedNavbarWidth, collapseProgress)

            val collapsedPillWidth = (totalWidth - collapsedNavbarWidth - searchWidth - spacing * 2f).coerceAtLeast(0.dp)
            val currentPillWidth = lerp(expandedNavbarWidth, collapsedPillWidth, collapseProgress)

            val currentPillOffsetX = lerp(0.dp, collapsedNavbarWidth + spacing, collapseProgress)
            val bottomRowOffsetY = lerp(barHeight + spacing, 0.dp, collapseProgress)

            // 1. Background Play Pill (resting directly on top of navbar, slides down between Home and Search on scroll)
            if (audioActiveProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset(x = currentPillOffsetX, y = 0.dp)
                        .width(currentPillWidth)
                        .height(barHeight)
                        .graphicsLayer {
                            alpha = audioActiveProgress.coerceIn(0f, 1f)
                        }
                ) {
                    BackgroundPlayPill(
                        vm = vm,
                        onOpenFullPlayer = { vm.setPlayerOpen(true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 2. Navbar (Left / Center)
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = bottomRowOffsetY)
                    .width(currentNavbarWidth)
                    .height(barHeight)
            ) {
                val isCompact = collapseProgress > 0.6f
                BottomNav(
                    selected = tab,
                    onSelect = onSelectTab,
                    isCompact = isCompact,
                    onExpandRequest = {
                        onSelectTab(MainTab.HOME)
                        isDockManuallyExpanded = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Standalone Search Button (Right)
            val searchShape = remember { CircleShape }
            Box(
                modifier = Modifier
                    .offset(x = totalWidth - searchWidth, y = bottomRowOffsetY)
                    .size(circleButtonSize)
                    .liquidGlass(shape = searchShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = circleButtonSize / 2),
                        onClick = onOpenSearch
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun HorizontalSoundWaves(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberSystemReducedMotion()
    if (reducedMotion || !isPlaying) {
        Canvas(modifier = modifier) {
            val count = 4
            val barWidthPx = 2.dp.toPx()
            val barSpacingPx = 2.dp.toPx()
            val totalWidth = count * barWidthPx + (count - 1) * barSpacingPx
            val startX = (size.width - totalWidth) / 2f + barWidthPx / 2f
            val centerY = size.height / 2f

            val minH = 2.5.dp.toPx()
            val maxH = size.height.coerceAtMost(10.dp.toPx())
            val factors = listOf(0.65f, 0.85f, 0.55f, 0.70f)
            val activity = if (isPlaying) 1f else 0.15f

            for (i in 0 until count) {
                val x = startX + i * (barWidthPx + barSpacingPx)
                val h = minH + (maxH - minH) * factors[i] * activity
                drawLine(
                    color = Color.White.copy(alpha = if (reducedMotion) 0.8f else 0.35f),
                    start = Offset(x, centerY - h / 2f),
                    end = Offset(x, centerY + h / 2f),
                    strokeWidth = barWidthPx,
                    cap = StrokeCap.Round
                )
            }
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "horizontalWaves")
    val t1 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hWaveT1"
    )
    val t2 by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hWaveT2"
    )
    val t3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hWaveT3"
    )
    val t4 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hWaveT4"
    )
    val playActivity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.15f,
        animationSpec = tween(300),
        label = "hWaveActivity"
    )

    Canvas(modifier = modifier) {
        val count = 4
        val barWidthPx = 2.dp.toPx()
        val barSpacingPx = 2.dp.toPx()
        val totalWidth = count * barWidthPx + (count - 1) * barSpacingPx
        val startX = (size.width - totalWidth) / 2f + barWidthPx / 2f
        val centerY = size.height / 2f

        val minH = 2.5.dp.toPx()
        val maxH = size.height.coerceAtMost(10.dp.toPx())
        val factors = listOf(t1, t2, t3, t4)

        for (i in 0 until count) {
            val x = startX + i * (barWidthPx + barSpacingPx)
            val h = minH + (maxH - minH) * factors[i] * playActivity
            drawLine(
                color = Color.White,
                start = Offset(x, centerY - h / 2f),
                end = Offset(x, centerY + h / 2f),
                strokeWidth = barWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun CircularAudioVisualizer(
    isPlaying: Boolean,
    progress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberSystemReducedMotion()
    if (reducedMotion || !isPlaying) {
        Box(
            modifier = modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f - 3.dp.toPx()

                // 1. Minimal circular hairline track
                drawCircle(
                    color = Color.White.copy(alpha = 0.20f),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // 2. White circular progress indicator around the circumference showing elapsed time
                if (progress > 0.005f) {
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Central minimal 4-bar soundwave
                val barWidthPx = 2.5.dp.toPx()
                val barSpacingPx = 2.5.dp.toPx()
                val totalBarsWidth = 4 * barWidthPx + 3 * barSpacingPx
                val startX = center.x - totalBarsWidth / 2f + barWidthPx / 2f

                val restingHeightPx = 3.dp.toPx()
                val maxHeight1 = 10.dp.toPx()
                val maxHeight2 = 16.dp.toPx()
                val maxHeight3 = 13.dp.toPx()
                val maxHeight4 = 9.dp.toPx()

                val factors = listOf(0.60f, 0.80f, 0.45f, 0.70f)
                val activity = if (isPlaying) 1f else 0f

                val heights = listOf(
                    restingHeightPx + (maxHeight1 - restingHeightPx) * factors[0] * activity,
                    restingHeightPx + (maxHeight2 - restingHeightPx) * factors[1] * activity,
                    restingHeightPx + (maxHeight3 - restingHeightPx) * factors[2] * activity,
                    restingHeightPx + (maxHeight4 - restingHeightPx) * factors[3] * activity
                )

                for (i in 0 until 4) {
                    val x = startX + i * (barWidthPx + barSpacingPx)
                    val h = heights[i]
                    drawLine(
                        color = Color.White,
                        start = Offset(x, center.y - h / 2f),
                        end = Offset(x, center.y + h / 2f),
                        strokeWidth = barWidthPx,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "minimalVisualizer")

    val t1 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visT1"
    )

    val t2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visT2"
    )

    val t3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(580, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visT3"
    )

    val t4 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visT4"
    )

    val playActivity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(300),
        label = "visPlayActivity"
    )

    Box(
        modifier = modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 3.dp.toPx()

            // 1. Minimal circular hairline track
            drawCircle(
                color = Color.White.copy(alpha = 0.20f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 2. White circular progress indicator around the circumference showing elapsed time
            if (progress > 0.005f) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Central minimal 4-bar soundwave
            val barWidthPx = 2.5.dp.toPx()
            val barSpacingPx = 2.5.dp.toPx()
            val totalBarsWidth = 4 * barWidthPx + 3 * barSpacingPx
            val startX = center.x - totalBarsWidth / 2f + barWidthPx / 2f

            val restingHeightPx = 3.dp.toPx()
            val maxHeight1 = 10.dp.toPx()
            val maxHeight2 = 16.dp.toPx()
            val maxHeight3 = 13.dp.toPx()
            val maxHeight4 = 9.dp.toPx()

            val heights = listOf(
                restingHeightPx + (maxHeight1 - restingHeightPx) * t1 * playActivity,
                restingHeightPx + (maxHeight2 - restingHeightPx) * t2 * playActivity,
                restingHeightPx + (maxHeight3 - restingHeightPx) * t3 * playActivity,
                restingHeightPx + (maxHeight4 - restingHeightPx) * t4 * playActivity
            )

            for (i in 0 until 4) {
                val x = startX + i * (barWidthPx + barSpacingPx)
                val h = heights[i]
                drawLine(
                    color = Color.White,
                    start = Offset(x, center.y - h / 2f),
                    end = Offset(x, center.y + h / 2f),
                    strokeWidth = barWidthPx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun BackgroundPlayPill(
    vm: ExcavViewModel,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val playbackPos by vm.player.playbackPosition.collectAsStateWithLifecycle()
    val currentVideo = playerState.currentVideo ?: return
    val playback = playerState.playback
    val pillShape = remember { RoundedCornerShape(percent = 50) }

    Box(
        modifier = modifier
            .height(62.dp)
            .liquidGlass(shape = pillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenFullPlayer
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Name and Elapsed Time + Horizontal Waves
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentVideo.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatDuration(playbackPos.currentPositionMs)} / ${formatDuration(playbackPos.durationMs)}",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    HorizontalSoundWaves(
                        isPlaying = playback.isPlaying,
                        modifier = Modifier.size(width = 18.dp, height = 10.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Play / Pause Button
            IconButton(
                onClick = {
                    if (playback.isPlaying) {
                        vm.player.pause()
                    } else {
                        vm.player.resume()
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Close Button
            IconButton(
                onClick = {
                    vm.player.stop()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = ExcavPalette.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


