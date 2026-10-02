package com.excavplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.excavplayer.R
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.media.thumbnail.ThumbnailLoader
import com.excavplayer.ui.UserMessage
import com.excavplayer.ui.theme.*
import dagger.hilt.android.EntryPointAccessors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

@Composable
fun Modifier.tactilePress(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    pressScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = if (isPressed) Spring.StiffnessMedium else Spring.StiffnessLow
        ),
        label = "tactilePressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = ExcavPalette.Blue.copy(alpha = 0.2f)),
            onClick = onClick
        )
}

@Composable
fun AnimatedDialogContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.90f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "dialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "dialogAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        content = content
    )
}

@Composable
fun BrandHeader(
    onSearch: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    isScrolled: Boolean = true,
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current
) {
    val context = LocalContext.current
    val iconBitmap = remember(context) {
        try {
            ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(
                width = 96,
                height = 96,
                config = Bitmap.Config.ARGB_8888
            )?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    ProgressiveHeaderContainer(
        modifier = modifier,
        isScrolled = isScrolled,
        hazeState = hazeState,
        fadeHeight = 24.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = stringResource(R.string.cd_logo),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.cd_logo),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Excav",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Player",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.weight(1f))
            GlassmorphicHeaderActions(
                onSearch = onSearch,
                onRefresh = onRefresh,
                hazeState = hazeState
            )
        }
    }
}

@Composable
fun FoldersBrandHeader(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    isScrolled: Boolean = true,
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current
) {
    val context = LocalContext.current
    val iconBitmap = remember(context) {
        try {
            ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(
                width = 96,
                height = 96,
                config = Bitmap.Config.ARGB_8888
            )?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    val normCurrent = remember(currentPath) {
        val trimmed = currentPath.trim().trimEnd('/')
        when {
            trimmed.isEmpty() || trimmed == "/storage/emulated/0" || trimmed == "/storage/emulated" || trimmed.equals("Internal Storage", ignoreCase = true) -> "/storage/emulated/0"
            trimmed.startsWith("/storage/emulated/0") -> trimmed
            trimmed.startsWith("/storage/") -> trimmed
            trimmed.startsWith("/") -> trimmed
            else -> "/storage/emulated/0/$trimmed"
        }
    }

    val parts = remember(normCurrent) {
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair("Internal Storage", "/storage/emulated/0"))

        if (normCurrent.startsWith("/storage/emulated/0")) {
            val sub = normCurrent.removePrefix("/storage/emulated/0").trimStart('/')
            if (sub.isNotEmpty()) {
                val segments = sub.split('/').filter { it.isNotEmpty() }
                var accumulated = "/storage/emulated/0"
                for (seg in segments) {
                    accumulated += "/$seg"
                    list.add(Pair(seg, accumulated))
                }
            }
        } else if (normCurrent.startsWith("/storage/")) {
            list.clear()
            val sub = normCurrent.removePrefix("/storage/").trimStart('/')
            val segments = sub.split('/').filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                val cardId = segments[0]
                list.add(Pair("SD Card", "/storage/$cardId"))
                var accumulated = "/storage/$cardId"
                for (i in 1 until segments.size) {
                    accumulated += "/${segments[i]}"
                    list.add(Pair(segments[i], accumulated))
                }
            } else {
                list.add(Pair("Storage", normCurrent))
            }
        } else {
            val segments = normCurrent.split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            for (seg in segments) {
                accumulated += "/$seg"
                list.add(Pair(seg, accumulated))
            }
        }
        list
    }

    val canGoBack = parts.size > 1

    ProgressiveHeaderContainer(
        modifier = modifier,
        isScrolled = isScrolled,
        hazeState = hazeState,
        fadeHeight = 20.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Brand Header (Excav Player logo, title, and actions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = stringResource(R.string.cd_logo),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.cd_logo),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Excav",
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Player",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.weight(1f))
                GlassmorphicHeaderActions(
                    onSearch = onSearch,
                    onRefresh = onRefresh,
                    hazeState = hazeState
                )
            }

            // Location Bar / Breadcrumb Trail directly below the brand header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoBack) {
                    GlassmorphicBackButton(
                        onClick = onBack,
                        enabled = true,
                        size = 32.dp,
                        hazeState = hazeState
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    GlassmorphicItem(
                        modifier = Modifier.size(32.dp),
                        cornerRadius = 16,
                        hazeState = hazeState
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = stringResource(R.string.folders),
                                tint = ExcavPalette.Blue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    parts.forEachIndexed { idx, part ->
                        val isLast = idx == parts.size - 1
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isLast) { onNavigateToPath(part.second) },
                            color = if (isLast) ExcavPalette.Blue.copy(alpha = 0.18f) else Color.Transparent
                        ) {
                            Text(
                                text = part.first,
                                color = if (isLast) ExcavPalette.Blue else ExcavPalette.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (!isLast) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = ExcavPalette.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNav(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = MainTab.entries
    val selectedIndex = entries.indexOf(selected).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        GlassmorphicItem(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            cornerRadius = 32,
            blurRadius = 15
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val tabWidth = maxWidth / entries.size
                val pillOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "bottomNavPillOffset"
                )

                // Sliding Glass Indicator Pill
                val pillShape = RoundedCornerShape(26.dp)
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .clip(pillShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.30f),
                            shape = pillShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    entries.forEach { tab ->
                        val isSelected = selected == tab

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = 0.6f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "tabIconScale"
                        )
                        val contentAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1.0f else 0.65f,
                            animationSpec = tween(150),
                            label = "tabContentAlpha"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp)
                                .clip(pillShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = Color.White.copy(alpha = contentAlpha),
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                color = Color.White.copy(alpha = contentAlpha),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    FOLDERS("Folders", Icons.Default.Folder),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    FAVORITES("Favs", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, label: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ExcavPalette.SurfaceCard)
                .border(1.dp, ExcavPalette.Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ExcavPalette.TextMuted,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = label,
            color = ExcavPalette.TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    dropdownMenu: (@Composable () -> Unit)? = null
) {
    val resLabel = formatResolution(video.width, video.height)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .tactilePress(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Thumbnail(video, Modifier.fillMaxSize())
                
                // Resolution Badge
                if (resLabel != null) {
                    Text(
                        text = resLabel,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(ExcavPalette.Ink.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, ExcavPalette.Line, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }

                if (video.durationMs > 0) {
                    Text(
                        text = video.formattedDuration,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(ExcavPalette.Ink.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, ExcavPalette.Line, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                val progress = ((video.resumePositionMs ?: 0L).toFloat() / video.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Brush.horizontalGradient(listOf(ExcavPalette.BlueDeep, ExcavPalette.Blue)))
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        color = ExcavPalette.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp,
                            lineBreak = LineBreak.Paragraph,
                            hyphens = Hyphens.None
                        )
                    )
                    Spacer(Modifier.height(3.dp))
                    val watched = video.resumePositionMs ?: 0L
                    val percent = (watched.toFloat() / video.durationMs.coerceAtLeast(1L) * 100).toInt().coerceIn(0, 100)
                    val sizeStr = formatFileSize(video.sizeBytes)
                    val resPrefix = if (resLabel != null) "$resLabel • " else ""
                    Text(
                        text = if (watched > 0) "${formatDuration(watched)} / ${video.formattedDuration} • $percent%" else "$resPrefix$sizeStr",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                }
                if (onMoreClick != null || dropdownMenu != null) {
                    Box {
                        IconButton(
                            onClick = { onMoreClick?.invoke() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more),
                                tint = ExcavPalette.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        dropdownMenu?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    dropdownMenu: (@Composable () -> Unit)? = null
) {
    val resLabel = formatResolution(video.width, video.height)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .tactilePress(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            ) {
                Thumbnail(video, Modifier.fillMaxSize())
                if (resLabel != null) {
                    Text(
                        text = resLabel,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(ExcavPalette.Ink.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, ExcavPalette.Line, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
                if (video.durationMs > 0) {
                    Text(
                        text = video.formattedDuration,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(ExcavPalette.Ink.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        color = ExcavPalette.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 17.sp,
                            lineBreak = LineBreak.Paragraph,
                            hyphens = Hyphens.None
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatFileSize(video.sizeBytes)}",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp)
                    )
                }
                if (onMoreClick != null || dropdownMenu != null) {
                    Box {
                        IconButton(
                            onClick = { onMoreClick?.invoke() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more),
                                tint = ExcavPalette.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        dropdownMenu?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
fun GroupThumbnail(videos: List<Video>, modifier: Modifier = Modifier) {
    val loader = rememberThumbnailLoader()
    val initialBitmap = remember(videos) {
        var found: Bitmap? = null
        for (v in videos.take(4)) {
            found = loader.getFromCache(v.uri)
            if (found != null) break
        }
        found
    }
    var bitmap by remember(videos) { mutableStateOf(initialBitmap) }
    LaunchedEffect(videos) {
        if (bitmap == null && videos.isNotEmpty()) {
            for (v in videos.take(4)) {
                val b = loader.loadThumbnail(v.uri)
                if (b != null) {
                    bitmap = b
                    break
                }
            }
        }
    }
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2B3242),
                        Color(0xFF1B202B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = Color(0xFFFFC44D).copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp)
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GroupCard(
    groupName: String,
    videos: List<Video>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current
) {
    val totalSize = videos.sumOf { it.sizeBytes }
    val totalCount = videos.size
    val cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 38.dp, bottomEnd = 38.dp)
    val thumbnailShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 14.dp, bottomEnd = 14.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, Color(0xFF282F3B), cardShape)
            .tactilePress(onClick = onClick),
        color = Color(0xFF13171F)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.60f)
                        .clip(thumbnailShape)
                ) {
                    GroupThumbnail(
                        videos = videos,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color(0x8013171F)
                                    )
                                )
                            )
                    )
                }

                // Translucent Pill placed on top of the lower line of the thumbnail, left side (bisected at middle)
                val pillShape = CircleShape
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp)
                        .offset(y = 12.dp)
                        .height(24.dp)
                        .clip(pillShape)
                        .background(Color(0xCC1A202C))
                        .border(0.75.dp, Color.White.copy(alpha = 0.22f), pillShape)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalCount ${if (totalCount == 1) "Video" else "Videos"}",
                        color = Color(0xFFF5F7FA),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Centered Title and Size
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = groupName,
                    color = Color(0xFFF5F7FA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.5.sp,
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.None
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (totalSize > 0) formatFileSize(totalSize) else "$totalCount ${if (totalCount == 1) "video" else "videos"}",
                    color = Color(0xFF8E97A6),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingRowCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    dropdownMenu: (@Composable () -> Unit)? = null
) {
    val progress = ((video.resumePositionMs ?: 0L).toFloat() / video.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    val watched = video.resumePositionMs ?: 0L
    val remaining = (video.durationMs - watched).coerceAtLeast(0L)

    Surface(
        modifier = modifier
            .width(220.dp)
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .tactilePress(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
            ) {
                Thumbnail(video, Modifier.fillMaxSize())

                // Remaining Time Badge
                if (remaining > 0) {
                    Text(
                        text = "${formatDuration(remaining)} left",
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(ExcavPalette.Ink.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, ExcavPalette.Line, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ExcavPalette.Ink.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ExcavPalette.Blue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Progress Bar at bottom of thumbnail
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Brush.horizontalGradient(listOf(ExcavPalette.BlueDeep, ExcavPalette.Blue)))
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        color = ExcavPalette.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp,
                            lineBreak = LineBreak.Paragraph,
                            hyphens = Hyphens.None
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatDuration(watched)} / ${video.formattedDuration}",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                }
                if (onMoreClick != null || dropdownMenu != null) {
                    Box {
                        IconButton(
                            onClick = { onMoreClick?.invoke() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more),
                                tint = ExcavPalette.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        dropdownMenu?.invoke()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FolderCard(
    folder: Folder,
    videos: List<Video> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current
) {
    val totalSize = if (folder.totalSizeBytes > 0) folder.totalSizeBytes else videos.sumOf { it.sizeBytes }
    val totalCount = if (folder.videoCount > 0) folder.videoCount else videos.size
    val cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 38.dp, bottomEnd = 38.dp)
    val thumbnailShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 14.dp, bottomEnd = 14.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, Color(0xFF282F3B), cardShape)
            .tactilePress(onClick = onClick),
        color = Color(0xFF13171F)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.60f)
                        .clip(thumbnailShape)
                ) {
                    GroupThumbnail(
                        videos = videos,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color(0x8013171F)
                                    )
                                )
                            )
                    )
                }

                // Translucent Pill placed on top of the lower line of the thumbnail, left side (bisected at middle)
                val pillShape = CircleShape
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp)
                        .offset(y = 12.dp)
                        .height(24.dp)
                        .clip(pillShape)
                        .background(Color(0xCC1A202C))
                        .border(0.75.dp, Color.White.copy(alpha = 0.22f), pillShape)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalCount ${if (totalCount == 1) "Video" else "Videos"}",
                        color = Color(0xFFF5F7FA),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Centered Title and Size
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = folder.name,
                    color = Color(0xFFF5F7FA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.5.sp,
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.None
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (totalSize > 0) formatFileSize(totalSize) else "$totalCount ${if (totalCount == 1) "video" else "videos"}",
                    color = Color(0xFF8E97A6),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }
    }
}

@Composable
fun FolderRow(
    folder: Folder,
    videos: List<Video> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Row)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Row)
            .tactilePress(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                GroupThumbnail(
                    videos = videos,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = ExcavPalette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.None
                    )
                )
                Spacer(Modifier.height(3.dp))
                val totalCount = if (folder.videoCount > 0) folder.videoCount else videos.size
                val totalSize = if (folder.totalSizeBytes > 0) folder.totalSizeBytes else videos.sumOf { it.sizeBytes }
                Text(
                    text = "$totalCount ${if (totalCount == 1) stringResource(R.string.video) else stringResource(R.string.videos)} • ${formatFileSize(totalSize)}",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist,
    onOverflow: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dropdownMenu: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Row)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Row)
            .tactilePress(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFC44D).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFFFFC44D).copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = stringResource(R.string.cd_playlist),
                    tint = Color(0xFFFFC44D),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    color = ExcavPalette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.None
                    )
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${playlist.itemCount} ${if (playlist.itemCount == 1) stringResource(R.string.video) else stringResource(R.string.videos)}",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Box {
                IconButton(onClick = onOverflow) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                        tint = ExcavPalette.TextMuted
                    )
                }
                dropdownMenu?.invoke()
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FolderGridCard(
    folder: Folder,
    videos: List<Video> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current
) {
    FolderCard(
        folder = folder,
        videos = videos,
        onClick = onClick,
        modifier = modifier,
        hazeState = hazeState
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    videos: List<Video> = emptyList(),
    onOverflow: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = LocalHazeState.current,
    dropdownMenu: (@Composable () -> Unit)? = null
) {
    val totalCount = playlist.itemCount
    val totalSize = videos.sumOf { it.sizeBytes }
    val cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 38.dp, bottomEnd = 38.dp)
    val thumbnailShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 14.dp, bottomEnd = 14.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, Color(0xFF282F3B), cardShape)
            .tactilePress(onClick = onClick),
        color = Color(0xFF13171F)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.60f)
                        .clip(thumbnailShape)
                ) {
                    GroupThumbnail(
                        videos = videos,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color(0x8013171F)
                                    )
                                )
                            )
                    )
                }

                // Translucent Pill placed on top of the lower line of the thumbnail, left side (bisected at middle)
                val pillShape = CircleShape
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp)
                        .offset(y = 12.dp)
                        .height(24.dp)
                        .clip(pillShape)
                        .background(Color(0xCC1A202C))
                        .border(0.75.dp, Color.White.copy(alpha = 0.22f), pillShape)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalCount ${if (totalCount == 1) "Video" else "Videos"}",
                        color = Color(0xFFF5F7FA),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                // Three-dot Overflow Button on Bottom-Right (Overlapping Edge, CircleShape, Translucent Pill Style)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp)
                        .offset(y = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC1A202C))
                            .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .clickable(onClick = onOverflow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more),
                            tint = Color(0xFFF5F7FA),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    dropdownMenu?.invoke()
                }
            }

            // Centered Title and Size Subtitle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playlist.title,
                    color = Color(0xFFF5F7FA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.5.sp,
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.None
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (totalSize > 0) formatFileSize(totalSize) else "$totalCount ${if (totalCount == 1) "video" else "videos"}",
                    color = Color(0xFF8E97A6),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }
    }
}

@Composable
fun ListVideoRow(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    dropdownMenu: (@Composable () -> Unit)? = null
) {
    val resLabel = formatResolution(video.width, video.height)
    val sizeStr = formatFileSize(video.sizeBytes)
    val formatStr = video.fileFormat
    val resumePos = video.resumePositionMs ?: 0L

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Row)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Row)
            .tactilePress(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 62.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Thumbnail(video, Modifier.fillMaxSize())
                if (video.durationMs > 0) {
                    Text(
                        text = video.formattedDuration,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(ExcavPalette.Ink.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.5.dp)
                    )
                }

                // Progress Bar at bottom of thumbnail
                if (resumePos > 0L && video.durationMs > 0L) {
                    val progress = (resumePos.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Brush.horizontalGradient(listOf(ExcavPalette.BlueDeep, ExcavPalette.Blue)))
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    color = ExcavPalette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.None
                    )
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Format Tag
                    Surface(
                        color = ExcavPalette.Blue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, ExcavPalette.Blue.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = formatStr,
                            color = ExcavPalette.Blue,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    if (resLabel != null) {
                        Surface(
                            color = ExcavPalette.InkElevated,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, ExcavPalette.Line)
                        ) {
                            Text(
                                text = resLabel,
                                color = ExcavPalette.TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = sizeStr,
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                }
            }

            if (onMoreClick != null || dropdownMenu != null) {
                Box {
                    IconButton(onClick = { onMoreClick?.invoke() }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more),
                            tint = ExcavPalette.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    dropdownMenu?.invoke()
                }
            }
        }
    }
}

@Composable
fun BreadcrumbBar(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    onBack: () -> Unit,
    isScrolled: Boolean = true,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null
) {
    val normCurrent = remember(currentPath) {
        val trimmed = currentPath.trim().trimEnd('/')
        when {
            trimmed.isEmpty() || trimmed == "/storage/emulated/0" || trimmed == "/storage/emulated" || trimmed.equals("Internal Storage", ignoreCase = true) -> "/storage/emulated/0"
            trimmed.startsWith("/storage/emulated/0") -> trimmed
            trimmed.startsWith("/storage/") -> trimmed
            trimmed.startsWith("/") -> trimmed
            else -> "/storage/emulated/0/$trimmed"
        }
    }

    val parts = remember(normCurrent) {
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair("Internal Storage", "/storage/emulated/0"))

        if (normCurrent.startsWith("/storage/emulated/0")) {
            val sub = normCurrent.removePrefix("/storage/emulated/0").trimStart('/')
            if (sub.isNotEmpty()) {
                val segments = sub.split('/').filter { it.isNotEmpty() }
                var accumulated = "/storage/emulated/0"
                for (seg in segments) {
                    accumulated += "/$seg"
                    list.add(Pair(seg, accumulated))
                }
            }
        } else if (normCurrent.startsWith("/storage/")) {
            list.clear()
            val sub = normCurrent.removePrefix("/storage/").trimStart('/')
            val segments = sub.split('/').filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                val cardId = segments[0]
                list.add(Pair("SD Card", "/storage/$cardId"))
                var accumulated = "/storage/$cardId"
                for (i in 1 until segments.size) {
                    accumulated += "/${segments[i]}"
                    list.add(Pair(segments[i], accumulated))
                }
            } else {
                list.add(Pair("Storage", normCurrent))
            }
        } else {
            val segments = normCurrent.split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            for (seg in segments) {
                accumulated += "/$seg"
                list.add(Pair(seg, accumulated))
            }
        }
        list
    }

    val canGoBack = parts.size > 1

    ProgressiveHeaderContainer(
        modifier = modifier,
        isScrolled = isScrolled,
        fadeHeight = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                GlassmorphicBackButton(
                    onClick = onBack,
                    enabled = true,
                    size = 36.dp
                )
            } else {
                GlassmorphicItem(
                    modifier = Modifier.size(36.dp),
                    cornerRadius = 18
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(R.string.folders),
                            tint = ExcavPalette.Blue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                parts.forEachIndexed { idx, part ->
                    val isLast = idx == parts.size - 1
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isLast) { onNavigateToPath(part.second) },
                        color = if (isLast) ExcavPalette.Blue.copy(alpha = 0.18f) else Color.Transparent
                    ) {
                        Text(
                            text = part.first,
                            color = if (isLast) ExcavPalette.Blue else ExcavPalette.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (!isLast) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = ExcavPalette.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (onSearch != null) {
                Spacer(Modifier.width(8.dp))
                GlassmorphicHeaderActions(
                    onSearch = onSearch,
                    onRefresh = onRefresh
                )
            }
        }
    }
}

@Composable
fun AnimatedFavoriteIcon(
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = if (isFavorite) Color(0xFFFF5277) else ExcavPalette.Text
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.20f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favoriteHeartScale"
    )
    val color by animateColorAsState(
        targetValue = tint,
        animationSpec = tween(180),
        label = "favoriteHeartColor"
    )

    Icon(
        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = if (isFavorite) "Favorite" else "Not Favorite",
        tint = color,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
fun VideoOptionsMenu(
    expanded: Boolean,
    video: Video,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromContinueWatching: (() -> Unit)? = null
) {
    val menuShape = RoundedCornerShape(14.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = 0.dp, y = 0.dp),
        shape = menuShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        modifier = Modifier.darkUltraThinBlur(
            shape = menuShape,
            backgroundColor = Color(0xF2101216),
            strokeColor = Color.White.copy(alpha = 0.16f)
        )
    ) {
        if (onRemoveFromContinueWatching != null) {
            DropdownMenuItem(
                text = { Text("Remove from Continue Watching", color = ExcavPalette.Text) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = ExcavPalette.TextMuted
                    )
                },
                onClick = {
                    onRemoveFromContinueWatching()
                    onDismiss()
                }
            )
            HorizontalDivider(thickness = 0.5.dp, color = ExcavPalette.Line.copy(alpha = 0.5f))
        }
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites", color = ExcavPalette.Text) },
            leadingIcon = {
                AnimatedFavoriteIcon(
                    isFavorite = isFavorite,
                    tint = if (isFavorite) Color(0xFFFF5277) else ExcavPalette.Text
                )
            },
            onClick = {
                onToggleFavorite()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist", color = ExcavPalette.Text) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = ExcavPalette.Text) },
            onClick = {
                onAddToPlaylist()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Rename", color = ExcavPalette.Text) },
            leadingIcon = { Icon(Icons.Default.Edit, null, tint = ExcavPalette.Text) },
            onClick = {
                onRename()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Properties", color = ExcavPalette.Text) },
            leadingIcon = { Icon(Icons.Default.Info, null, tint = ExcavPalette.Text) },
            onClick = {
                onProperties()
                onDismiss()
            }
        )
        HorizontalDivider(thickness = 0.5.dp, color = ExcavPalette.Line.copy(alpha = 0.5f))
        DropdownMenuItem(
            text = { Text("Delete", color = ExcavPalette.Error) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = ExcavPalette.Error) },
            onClick = {
                onDelete()
                onDismiss()
            }
        )
    }
}

@Composable
fun VideoPropertiesDialog(video: Video, onDismiss: () -> Unit) {
    val hazeState = LocalHazeState.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedDialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .darkUltraThinBlur(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0xF210131B),
                    strokeColor = Color.White.copy(alpha = 0.18f)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "File Properties",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PropertyItem(label = "Title", value = video.displayName)
                    PropertyItem(label = "Resolution", value = formatResolution(video.width, video.height) ?: "Standard")
                    PropertyItem(label = "Duration", value = video.formattedDuration)
                    PropertyItem(label = "File Size", value = formatFileSize(video.sizeBytes))
                    PropertyItem(label = "Format", value = video.fileFormat)
                    if (video.folderName.isNotBlank()) {
                        PropertyItem(label = "Folder", value = video.folderName)
                    }
                    if (video.relativePath.isNotBlank()) {
                        PropertyItem(label = "Location", value = video.relativePath)
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                        shape = ExcavShapes.Pill
                    ) {
                        Text("Close", color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = ExcavPalette.TextMuted,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = ExcavPalette.Text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
        )
    }
}

@Composable
fun RenameVideoDialog(
    video: Video,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val hazeState = LocalHazeState.current
    var name by remember(video.displayName) { mutableStateOf(video.displayName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedDialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .darkUltraThinBlur(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0xF210131B),
                    strokeColor = Color.White.copy(alpha = 0.18f)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Rename Video",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Video Name", color = ExcavPalette.TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExcavPalette.Blue,
                        unfocusedBorderColor = ExcavPalette.Line,
                        focusedTextColor = ExcavPalette.Text,
                        unfocusedTextColor = ExcavPalette.Text,
                        focusedContainerColor = Color(0x33141822),
                        unfocusedContainerColor = Color(0x33141822)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = name.isNotBlank() && name.trim() != video.displayName,
                        onClick = {
                            onRename(name.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                        shape = ExcavShapes.Pill
                    ) {
                        Text(stringResource(R.string.rename), color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    video: Video,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val hazeState = LocalHazeState.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedDialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .darkUltraThinBlur(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0xF210131B),
                    strokeColor = Color.White.copy(alpha = 0.18f)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ExcavPalette.Error.copy(alpha = 0.15f))
                            .border(1.dp, ExcavPalette.Error.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = ExcavPalette.Error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Delete Video?",
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Are you sure you want to delete \"${video.displayName}\"? This file will be removed from your library.",
                    color = ExcavPalette.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Error),
                        shape = ExcavShapes.Pill
                    ) {
                        Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    video: Video,
    playlists: List<Playlist>,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val hazeState = LocalHazeState.current
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedDialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .darkUltraThinBlur(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0xF210131B),
                    strokeColor = Color.White.copy(alpha = 0.18f)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isCreatingNew) "Create & Add" else "Add to Playlist",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    if (isCreatingNew) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Playlist Name", color = ExcavPalette.TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ExcavPalette.Blue,
                                unfocusedBorderColor = ExcavPalette.Line,
                                focusedTextColor = ExcavPalette.Text,
                                unfocusedTextColor = ExcavPalette.Text,
                                focusedContainerColor = Color(0x33141822),
                                unfocusedContainerColor = Color(0x33141822)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        if (playlists.isEmpty()) {
                            Text(
                                text = "No playlists found. Create a new playlist below.",
                                color = ExcavPalette.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(playlists.size) { index ->
                                    val playlist = playlists[index]
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .darkUltraThinBlur(
                                                shape = RoundedCornerShape(12.dp),
                                                backgroundColor = Color(0x6617191E),
                                                strokeColor = Color.White.copy(alpha = 0.08f)
                                            )
                                            .tactilePress {
                                                onSelectPlaylist(playlist.id)
                                                onDismiss()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = null,
                                                tint = ExcavPalette.Blue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = playlist.title,
                                                color = ExcavPalette.Text,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${playlist.itemCount}",
                                                color = ExcavPalette.TextMuted,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (isCreatingNew) {
                        Button(
                            enabled = newPlaylistName.isNotBlank(),
                            onClick = {
                                onCreatePlaylist(newPlaylistName.trim())
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                            shape = ExcavShapes.Pill
                        ) {
                            Text("Create", color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { isCreatingNew = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                            shape = ExcavShapes.Pill
                        ) {
                            Icon(Icons.Default.Add, null, tint = ExcavPalette.Ink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Playlist", color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Thumbnail(video: Video, modifier: Modifier) {
    val loader = rememberThumbnailLoader()
    var bitmap by remember(video.uri) { mutableStateOf(loader.getFromCache(video.uri)) }
    LaunchedEffect(video.uri) {
        if (bitmap == null) {
            bitmap = loader.loadThumbnail(video.uri)
        }
    }
    Box(
        modifier = modifier
            .background(ExcavPalette.SurfaceCardHighlight),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.thumbnail_description, video.displayName),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Icon(
            imageVector = Icons.Default.VideoFile,
            contentDescription = null,
            tint = ExcavPalette.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun AudioWaveEqualizer(modifier: Modifier = Modifier, color: Color = ExcavPalette.Blue) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "h4"
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight(h1).clip(ExcavShapes.Pill).background(color))
        Box(Modifier.width(3.dp).fillMaxHeight(h2).clip(ExcavShapes.Pill).background(color))
        Box(Modifier.width(3.dp).fillMaxHeight(h3).clip(ExcavShapes.Pill).background(color))
        Box(Modifier.width(3.dp).fillMaxHeight(h4).clip(ExcavShapes.Pill).background(color))
    }
}

@Composable
fun SleekRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "radioDotScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) ExcavPalette.CyanGlow else ExcavPalette.Line,
        animationSpec = tween(180),
        label = "radioBorderColor"
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.dp else 1.5.dp,
                color = borderColor,
                shape = CircleShape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (dotScale > 0.01f) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                        alpha = dotScale
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White, ExcavPalette.CyanGlow)
                        )
                    )
            )
        }
    }
}

@Composable
fun SleekSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White.copy(alpha = 0.85f),
            checkedTrackColor = ExcavPalette.GrayDark.copy(alpha = 0.5f),
            checkedBorderColor = ExcavPalette.GrayDark.copy(alpha = 0.4f),
            uncheckedThumbColor = ExcavPalette.TextMuted.copy(alpha = 0.6f),
            uncheckedTrackColor = ExcavPalette.InkElevated.copy(alpha = 0.6f),
            uncheckedBorderColor = ExcavPalette.Line.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun UserMessageHost(message: UserMessage?, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        if (message != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        if (message.isError) ExcavPalette.Error else ExcavPalette.BlueGlow,
                        RoundedCornerShape(14.dp)
                    ),
                color = if (message.isError) ExcavPalette.ErrorContainer else ExcavPalette.GlassStrong,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (message.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (message.isError) ExcavPalette.Error else ExcavPalette.Blue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = message.message,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = ExcavPalette.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcavSleekSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    centerLabel: String? = null,
    endLabel: String? = null,
    showZeroMarker: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ExcavPalette.Blue,
                inactiveTrackColor = Color(0xFF232A3B)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.5.dp, ExcavPalette.Blue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(ExcavPalette.Blue)
                    )
                }
            },
            track = { sliderState ->
                val fraction = if (valueRange.endInclusive > valueRange.start) {
                    ((sliderState.value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                } else 0f

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF232A3B))
                ) {
                    val fullWidth = maxWidth
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ExcavPalette.Silver, ExcavPalette.Blue)
                                )
                            )
                    )
                    if (showZeroMarker && valueRange.start < 0f && valueRange.endInclusive > 0f) {
                        val zeroFrac = (-valueRange.start) / (valueRange.endInclusive - valueRange.start)
                        Box(
                            modifier = Modifier
                                .offset(x = fullWidth * zeroFrac - 1.dp)
                                .width(2.5.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.9f))
                        )
                    }
                }
            }
        )

        if (startLabel != null || centerLabel != null || endLabel != null) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = startLabel.orEmpty(),
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
                Text(
                    text = centerLabel.orEmpty(),
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = endLabel.orEmpty(),
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            }
        }
    }
}

val LocalThumbnailLoader = staticCompositionLocalOf<ThumbnailLoader?> { null }

@Composable
fun rememberThumbnailLoader(): ThumbnailLoader {
    val local = LocalThumbnailLoader.current
    if (local != null) return local
    val context = LocalContext.current
    return remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(context.applicationContext, ThumbnailEntryPoint::class.java).thumbnailLoader()
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ThumbnailEntryPoint {
    fun thumbnailLoader(): ThumbnailLoader
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0kb"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    val kb = bytes.toDouble() / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2fgb", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1fmb", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0fkb", kb)
        else -> "${bytes}b"
    }
}

fun formatResolution(width: Int, height: Int): String? {
    val maxDim = maxOf(width, height)
    val minDim = minOf(width, height)
    return when {
        maxDim >= 3840 || minDim >= 2160 -> "4K"
        maxDim >= 2560 || minDim >= 1440 -> "2K"
        maxDim >= 1920 || minDim >= 1080 -> "1080p"
        maxDim >= 1280 || minDim >= 720 -> "720p"
        maxDim >= 854 || minDim >= 480 -> "480p"
        minDim > 0 -> "${minDim}p"
        else -> null
    }
}

fun formatBytes(bytes: Long): String = formatFileSize(bytes)

fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

fun formatDurationHuman(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun year(seconds: Long): String = if (seconds <= 0) "" else java.util.Calendar.getInstance().apply { timeInMillis = seconds * 1000 }.get(java.util.Calendar.YEAR).toString()
