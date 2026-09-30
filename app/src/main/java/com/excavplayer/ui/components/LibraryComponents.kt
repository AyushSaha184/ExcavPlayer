package com.excavplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
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
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.R
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.media.thumbnail.ThumbnailLoader
import com.excavplayer.ui.UserMessage
import com.excavplayer.ui.theme.*
import dagger.hilt.android.EntryPointAccessors

@Composable
fun BrandHeader(onSearch: () -> Unit, onRefresh: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(ExcavPalette.Blue, ExcavPalette.BlueDeep)
                    )
                )
                .border(1.dp, ExcavPalette.BlueGlow, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_logo),
                tint = ExcavPalette.Ink,
                modifier = Modifier.size(22.dp)
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
                color = ExcavPalette.Blue,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.weight(1f))
        // Search button first, then Refresh button with gap and translucent background
        IconButton(
            onClick = onSearch,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xDD181B22))
                .border(1.dp, ExcavPalette.Line.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = ExcavPalette.Text,
                modifier = Modifier.size(20.dp)
            )
        }
        if (onRefresh != null) {
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xDD181B22))
                    .border(1.dp, ExcavPalette.Line.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    tint = ExcavPalette.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BottomNav(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ExcavShapes.Pill)
                .border(1.dp, ExcavPalette.Line.copy(alpha = 0.5f), ExcavShapes.Pill),
            color = Color(0xDD181B22),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = selected == tab
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ExcavShapes.Pill)
                            .background(if (isSelected) ExcavPalette.Blue.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelect(tab) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) ExcavPalette.Blue else ExcavPalette.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                color = if (isSelected) ExcavPalette.Blue else ExcavPalette.TextSecondary,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
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
    FAVORITES("Favorites", Icons.Default.FavoriteBorder),
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
    onMoreClick: (() -> Unit)? = null
) {
    val resLabel = formatResolution(video.width, video.height)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .clickable(onClick = onClick),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                if (onMoreClick != null) {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more),
                            tint = ExcavPalette.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
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
    onMoreClick: (() -> Unit)? = null
) {
    val resLabel = formatResolution(video.width, video.height)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .clickable(onClick = onClick),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatFileSize(video.sizeBytes)}",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp)
                    )
                }
                if (onMoreClick != null) {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more),
                            tint = ExcavPalette.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupThumbnail(videos: List<Video>, modifier: Modifier = Modifier) {
    val loader = rememberThumbnailLoader()
    var bitmap by remember(videos) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videos) {
        for (v in videos) {
            val b = loader.loadThumbnail(v.uri)
            if (b != null) {
                bitmap = b
                break
            }
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
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = ExcavPalette.Yellow.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun GroupCard(
    groupName: String,
    videos: List<Video>,
    onClick: () -> Unit
) {
    val totalSize = videos.sumOf { it.sizeBytes }
    val totalCount = videos.size

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .clickable(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                GroupThumbnail(videos, Modifier.fillMaxSize())

                // Count Badge
                Text(
                    text = "$totalCount ${if (totalCount == 1) "Video" else "Videos"}",
                    color = ExcavPalette.Text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(ExcavPalette.Ink.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, ExcavPalette.Line, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ExcavPalette.Yellow.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = ExcavPalette.Yellow,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        color = ExcavPalette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatFileSize(totalSize),
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ExcavPalette.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingRowCard(
    video: Video,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val progress = ((video.resumePositionMs ?: 0L).toFloat() / video.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    val watched = video.resumePositionMs ?: 0L
    val remaining = (video.durationMs - watched).coerceAtLeast(0L)

    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .clickable(onClick = onClick),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatDuration(watched)} / ${video.formattedDuration}",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                }
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                        tint = ExcavPalette.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderCard(folder: Folder, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
            .clickable(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ExcavPalette.Yellow.copy(alpha = 0.14f))
                    .border(1.dp, ExcavPalette.Yellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = stringResource(R.string.cd_folder),
                    tint = ExcavPalette.Yellow,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = folder.name,
                color = ExcavPalette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${folder.videoCount} ${if (folder.videoCount == 1) stringResource(R.string.video) else stringResource(R.string.videos)} • ${formatFileSize(folder.totalSizeBytes)}",
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
            )
        }
    }
}

@Composable
fun FolderRow(folder: Folder, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Row)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Row)
            .clickable(onClick = onClick),
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
                    .background(ExcavPalette.Yellow.copy(alpha = 0.12f))
                    .border(1.dp, ExcavPalette.Yellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = stringResource(R.string.cd_folder),
                    tint = ExcavPalette.Yellow,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = ExcavPalette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${folder.videoCount} ${if (folder.videoCount == 1) stringResource(R.string.video) else stringResource(R.string.videos)} • ${formatFileSize(folder.totalSizeBytes)}",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_open),
                tint = ExcavPalette.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PlaylistRow(playlist: Playlist, onOverflow: () -> Unit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Row)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Row)
            .clickable(onClick = onClick),
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
                    .background(ExcavPalette.Blue.copy(alpha = 0.12f))
                    .border(1.dp, ExcavPalette.Blue.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.cd_playlist),
                    tint = ExcavPalette.Blue,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    color = ExcavPalette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${playlist.itemCount} ${if (playlist.itemCount == 1) stringResource(R.string.video) else stringResource(R.string.videos)}",
                    color = ExcavPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onOverflow) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.cd_more),
                    tint = ExcavPalette.TextMuted
                )
            }
        }
    }
}

@Composable
fun ListVideoRow(
    video: Video,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    val resLabel = formatResolution(video.width, video.height)
    val sizeStr = formatFileSize(video.sizeBytes)
    val formatStr = video.fileFormat
    val resumePos = video.resumePositionMs ?: 0L

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Row)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Row)
            .clickable(onClick = onClick),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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

            if (onMoreClick != null) {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                        tint = ExcavPalette.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BreadcrumbBar(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    onBack: () -> Unit
) {
    val parts = remember(currentPath) {
        val trimmed = currentPath.trimEnd('/')
        val list = mutableListOf<Pair<String, String>>()

        if (trimmed.isEmpty() || trimmed == "/storage/emulated/0") {
            list.add(Pair("Internal Storage", "/storage/emulated/0"))
        } else if (trimmed.startsWith("/storage/emulated/0")) {
            list.add(Pair("Internal Storage", "/storage/emulated/0"))
            val sub = trimmed.removePrefix("/storage/emulated/0").trimStart('/')
            val segments = sub.split('/').filter { it.isNotEmpty() }
            var accumulated = "/storage/emulated/0"
            for (seg in segments) {
                accumulated += "/$seg"
                list.add(Pair(seg, accumulated))
            }
        } else if (trimmed.startsWith("/storage/")) {
            val sub = trimmed.removePrefix("/storage/").trimStart('/')
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
                list.add(Pair("Storage", trimmed))
            }
        } else {
            val segments = trimmed.split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            for (seg in segments) {
                accumulated += "/$seg"
                list.add(Pair(seg, accumulated))
            }
            if (list.isEmpty()) {
                list.add(Pair("Internal Storage", "/storage/emulated/0"))
            }
        }
        list
    }

    val canGoBack = parts.size > 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            enabled = canGoBack,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ExcavPalette.SurfaceCard)
                .border(1.dp, if (canGoBack) ExcavPalette.Line else ExcavPalette.Line.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (canGoBack) ExcavPalette.Text else ExcavPalette.TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
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
    }
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
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(ExcavPalette.SurfaceCard)
            .border(1.dp, ExcavPalette.Line, RoundedCornerShape(12.dp))
    ) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites", color = ExcavPalette.Text) },
            leadingIcon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) ExcavPalette.Pink else ExcavPalette.Text
                )
            },
            onClick = {
                onToggleFavorite()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist", color = ExcavPalette.Text) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = ExcavPalette.Blue) },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "File Properties",
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
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
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                shape = ExcavShapes.Pill
            ) {
                Text("Close", color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ExcavPalette.SurfaceCard,
        shape = ExcavShapes.Card
    )
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
    var name by remember(video.displayName) { mutableStateOf(video.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Video",
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
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
                    unfocusedTextColor = ExcavPalette.Text
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted)
            }
        },
        containerColor = ExcavPalette.SurfaceCard,
        shape = ExcavShapes.Card
    )
}

@Composable
fun DeleteConfirmDialog(
    video: Video,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ExcavPalette.Error, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                text = "Delete Video?",
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${video.displayName}\"? This file will be removed from your library.",
                color = ExcavPalette.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted)
            }
        },
        containerColor = ExcavPalette.SurfaceCard,
        shape = ExcavShapes.Card
    )
}

@Composable
fun AddToPlaylistDialog(
    video: Video,
    playlists: List<Playlist>,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isCreatingNew) "Create & Add" else "Add to Playlist",
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
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
                            unfocusedTextColor = ExcavPalette.Text
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
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playlists.size) { index ->
                                val playlist = playlists[index]
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, ExcavPalette.Line, RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectPlaylist(playlist.id)
                                            onDismiss()
                                        },
                                    color = ExcavPalette.InkElevated
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted)
            }
        },
        containerColor = ExcavPalette.SurfaceCard,
        shape = ExcavShapes.Card
    )
}

@Composable
fun Thumbnail(video: Video, modifier: Modifier) {
    val loader = rememberThumbnailLoader()
    var bitmap by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(video.uri) {
        bitmap = loader.loadThumbnail(video.uri)
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
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
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
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.dp else 1.5.dp,
                color = if (selected) ExcavPalette.CyanGlow else ExcavPalette.Line,
                shape = CircleShape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
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
            checkedThumbColor = Color.White,
            checkedTrackColor = ExcavPalette.Blue,
            checkedBorderColor = ExcavPalette.Blue,
            uncheckedThumbColor = ExcavPalette.TextMuted,
            uncheckedTrackColor = ExcavPalette.InkElevated,
            uncheckedBorderColor = ExcavPalette.Line
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
    endLabel: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF00B0FF),
                inactiveTrackColor = Color(0xFF232A3B)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.5.dp, Color(0xFF00B0FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00B0FF))
                    )
                }
            },
            track = { sliderState ->
                val fraction = if (valueRange.endInclusive > valueRange.start) {
                    ((sliderState.value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF232A3B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                                )
                            )
                    )
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

@Composable
private fun rememberThumbnailLoader(): ThumbnailLoader {
    val context = LocalContext.current
    return remember { EntryPointAccessors.fromApplication(context.applicationContext, ThumbnailEntryPoint::class.java).thumbnailLoader() }
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
