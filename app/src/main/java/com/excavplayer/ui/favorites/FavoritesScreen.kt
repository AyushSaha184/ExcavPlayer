package com.excavplayer.ui.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.R
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import dev.chrisbanes.haze.hazeSource

@Composable
fun FavoritesScreen(
    videos: List<Video>,
    playlists: List<Playlist>,
    onPlay: (Video) -> Unit,
    onToggleFavorite: (Video) -> Unit,
    onAddToPlaylist: (Long, Video) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenameVideo: (Video, String) -> Unit,
    onDeleteVideo: (Video) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    onSearch: () -> Unit = {},
    onRefresh: (() -> Unit)? = null
) {
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val hazeState = LocalHazeState.current
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "section_header") {
                SectionTitle(stringResource(R.string.favorites))
            }
            items(videos, key = { it.id }, contentType = { "favorite_card" }) { video ->
                    CompactFavoriteCard(
                        video = video,
                        onClick = { onPlay(video) },
                        modifier = Modifier.animateItem(),
                        onMoreClick = { selectedVideoForMenu = video },
                        dropdownMenu = {
                            if (selectedVideoForMenu?.id == video.id) {
                                VideoOptionsMenu(
                                    expanded = true,
                                    video = video,
                                    isFavorite = true,
                                    onDismiss = { selectedVideoForMenu = null },
                                    onToggleFavorite = { onToggleFavorite(video) },
                                    onAddToPlaylist = { playlistVideoTarget = video },
                                    onRename = { renameVideoTarget = video },
                                    onProperties = { propertiesVideo = video },
                                    onDelete = { deleteVideoTarget = video }
                                )
                            }
                        }
                    )
                }
            }

        BrandHeader(
            onSearch = onSearch,
            onRefresh = onRefresh,
            isScrolled = isScrolled,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    propertiesVideo?.let { video ->
        VideoPropertiesDialog(
            video = video,
            onDismiss = { propertiesVideo = null }
        )
    }

    renameVideoTarget?.let { video ->
        RenameVideoDialog(
            video = video,
            onRename = { onRenameVideo(video, it) },
            onDismiss = { renameVideoTarget = null }
        )
    }

    deleteVideoTarget?.let { video ->
        DeleteConfirmDialog(
            video = video,
            onDelete = { onDeleteVideo(video) },
            onDismiss = { deleteVideoTarget = null }
        )
    }

    playlistVideoTarget?.let { video ->
        AddToPlaylistDialog(
            video = video,
            playlists = playlists,
            onSelectPlaylist = { playlistId -> onAddToPlaylist(playlistId, video) },
            onCreatePlaylist = { title -> onCreatePlaylist(title) },
            onDismiss = { playlistVideoTarget = null }
        )
    }
}

@Composable
fun CompactFavoriteCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    dropdownMenu: (@Composable () -> Unit)? = null
) {
    val resLabel = remember(video.width, video.height, video.displayName) {
        val r = formatResolution(video.width, video.height)
        if (r != null) r
        else {
            val nameLower = video.displayName.lowercase()
            when {
                nameLower.contains("2160p") || nameLower.contains("4k") -> "4K"
                nameLower.contains("1440p") || nameLower.contains("2k") -> "2K"
                nameLower.contains("1080p") || nameLower.contains("fhd") -> "1080p"
                nameLower.contains("720p") || nameLower.contains("hd") -> "720p"
                nameLower.contains("480p") -> "480p"
                else -> "HD"
            }
        }
    }

    val formatExt = remember(video.displayName) {
        val ext = video.displayName.substringAfterLast('.', "").uppercase()
        if (ext.length in 2..4) ext else "VIDEO"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ExcavPalette.Line, RoundedCornerShape(12.dp))
            .tactilePress(onClick = onClick),
        color = ExcavPalette.SurfaceCard
    ) {
        Column {
            // 16:9 Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Thumbnail(video, Modifier.fillMaxSize())

                // Resolution Badge (Top Start)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ExcavPalette.Ink.copy(alpha = 0.85f),
                    border = BorderStroke(0.5.dp, ExcavPalette.Line),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = resLabel,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }

                // Duration Badge (Bottom End)
                if (video.durationMs > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ExcavPalette.Ink.copy(alpha = 0.85f),
                        border = BorderStroke(0.5.dp, ExcavPalette.Line),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            color = ExcavPalette.Text,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Watch Progress Bar (if watched)
                val progress = if (video.durationMs > 0 && video.resumePositionMs != null) {
                    video.resumePositionMs.toFloat() / video.durationMs.toFloat()
                } else 0f
                if (progress > 0.02f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = ExcavPalette.Blue,
                        trackColor = Color.Transparent
                    )
                }
            }

            // Info Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        color = ExcavPalette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatFileSize(video.sizeBytes),
                            color = ExcavPalette.TextMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "•",
                            color = ExcavPalette.Line,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = resLabel,
                            color = ExcavPalette.Blue,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ExcavPalette.InkElevated,
                            border = BorderStroke(0.5.dp, ExcavPalette.Line)
                        ) {
                            Text(
                                text = formatExt,
                                color = ExcavPalette.TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                }

                if (onMoreClick != null || dropdownMenu != null) {
                    Box {
                        IconButton(
                            onClick = { onMoreClick?.invoke() },
                            modifier = Modifier.size(24.dp)
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

