package com.excavplayer.ui.favorites

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.R
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette

@Composable
fun FavoritesScreen(
    videos: List<Video>,
    favoriteFolders: List<Folder> = emptyList(),
    folderVideosMap: Map<String, List<Video>> = emptyMap(),
    playlists: List<Playlist>,
    onPlay: (Video, List<Video>) -> Unit,
    onOpenFolder: (Folder) -> Unit = {},
    onToggleFavorite: (Video) -> Unit,
    onToggleFavoriteFolder: (Folder) -> Unit = {},
    onSetVideosFavorite: (List<Video>, Boolean) -> Unit = { _, _ -> },
    onSetFoldersFavorite: (List<Folder>, Boolean) -> Unit = { _, _ -> },
    onAddVideosToFavorites: (List<Video>) -> Unit = {},
    onAddFoldersToFavorites: (List<Folder>) -> Unit = {},
    onAddToPlaylist: (Long, Video) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenameVideo: (Video, String) -> Unit,
    onDeleteVideo: (Video) -> Unit,
    onDeleteVideos: (List<Video>) -> Unit = {},
    onDeleteFolders: (List<Folder>) -> Unit = {},
    onRenameFolder: (Folder, String) -> Unit = { _, _ -> },
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    onSearch: () -> Unit = {},
    onRefresh: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedVideoIds.isNotEmpty() || selectedFolderPaths.isNotEmpty()

    var isOverflowMenuOpen by remember { mutableStateOf(false) }

    // Dialog targets
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }
    var propertiesMultiVideos by remember { mutableStateOf<List<Video>?>(null) }
    var propertiesFolder by remember { mutableStateOf<Folder?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var renameFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var batchDeleteTargetVideos by remember { mutableStateOf<List<Video>?>(null) }
    var batchDeleteTargetFolders by remember { mutableStateOf<List<Folder>?>(null) }
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }

    BackHandler(enabled = isSelectionMode) {
        selectedVideoIds = emptySet()
        selectedFolderPaths = emptySet()
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBarTop + 64.dp, bottom = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Folders Section (if any favorite folders exist)
            if (favoriteFolders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "section_header_folders") {
                    SectionTitle("Favorite Folders")
                }

                items(favoriteFolders, key = { "fav_folder_${it.path}" }, contentType = { "folder_card" }) { folder ->
                    val isFolderSelected = folder.path in selectedFolderPaths
                    val folderVids = folderVideosMap[folder.path] ?: folderVideosMap[folder.name.lowercase()].orEmpty()

                    FolderCard(
                        folder = folder,
                        videos = folderVids,
                        isSelected = isFolderSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                selectedVideoIds = emptySet()
                                selectedFolderPaths = if (isFolderSelected) {
                                    selectedFolderPaths - folder.path
                                } else {
                                    selectedFolderPaths + folder.path
                                }
                            } else {
                                onOpenFolder(folder)
                            }
                        },
                        onLongClick = {
                            selectedVideoIds = emptySet()
                            selectedFolderPaths = if (isFolderSelected) {
                                selectedFolderPaths - folder.path
                            } else {
                                selectedFolderPaths + folder.path
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }, contentType = "section_divider") {
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Favorite Videos Section
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "section_header_videos") {
                SectionTitle(stringResource(R.string.favorites))
            }

            items(videos, key = { it.id }, contentType = { "favorite_card" }) { video ->
                val isVideoSelected = video.id in selectedVideoIds
                CompactFavoriteCard(
                    video = video,
                    isSelected = isVideoSelected,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) {
                            selectedFolderPaths = emptySet()
                            selectedVideoIds = if (isVideoSelected) {
                                selectedVideoIds - video.id
                            } else {
                                selectedVideoIds + video.id
                            }
                        } else {
                            onPlay(video, videos)
                        }
                    },
                    onLongClick = {
                        selectedFolderPaths = emptySet()
                        selectedVideoIds = if (isVideoSelected) {
                            selectedVideoIds - video.id
                        } else {
                            selectedVideoIds + video.id
                        }
                    },
                    modifier = Modifier.animateItem(),
                    onMoreClick = if (!isSelectionMode) { { selectedVideoForMenu = video } } else null,
                    dropdownMenu = {
                        if (!isSelectionMode && selectedVideoForMenu?.id == video.id) {
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
            isSelectionMode = isSelectionMode,
            selectedCount = if (selectedFolderPaths.isNotEmpty()) selectedFolderPaths.size else selectedVideoIds.size,
            onMoreClick = { isOverflowMenuOpen = true },
            dropdownMenu = {
                if (isSelectionMode) {
                    if (selectedFolderPaths.isNotEmpty()) {
                        val selFolders = favoriteFolders.filter { it.path in selectedFolderPaths }
                        BatchFolderOptionsMenu(
                            expanded = isOverflowMenuOpen,
                            selectedFolders = selFolders,
                            favoriteFolderPaths = selFolders.map { it.path }.toSet(),
                            isFavorite = true,
                            onDismiss = { isOverflowMenuOpen = false },
                            onToggleFavorites = {
                                onSetFoldersFavorite(selFolders, false)
                                selectedFolderPaths = emptySet()
                            },
                            onRename = if (selFolders.size == 1) {
                                { renameFolderTarget = selFolders.first() }
                            } else null,
                            onProperties = {
                                if (selFolders.isNotEmpty()) {
                                    propertiesFolder = selFolders.first()
                                }
                            },
                            onDelete = {
                                batchDeleteTargetFolders = selFolders
                            }
                        )
                    } else {
                        val selVideos = videos.filter { it.id in selectedVideoIds }
                        val allFav = selVideos.isNotEmpty() && selVideos.all { it.isFavorite }
                        BatchVideoOptionsMenu(
                            expanded = isOverflowMenuOpen,
                            selectedVideos = selVideos,
                            onDismiss = { isOverflowMenuOpen = false },
                            onPlaySelected = {
                                if (selVideos.isNotEmpty()) {
                                    onPlay(selVideos.first(), selVideos)
                                    selectedVideoIds = emptySet()
                                }
                            },
                            onAddToPlaylist = {
                                if (selVideos.isNotEmpty()) {
                                    playlistVideoTarget = selVideos.first()
                                }
                            },
                            onToggleFavorites = {
                                onSetVideosFavorite(selVideos, !allFav)
                                selectedVideoIds = emptySet()
                            },
                            onShare = {
                                val uris = ArrayList<android.net.Uri>()
                                selVideos.forEach { uris.add(android.net.Uri.parse(it.uri)) }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "video/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Videos"))
                                selectedVideoIds = emptySet()
                            },
                            onRename = if (selVideos.size == 1) {
                                { renameVideoTarget = selVideos.first() }
                            } else null,
                            onProperties = {
                                if (selVideos.size == 1) {
                                    propertiesVideo = selVideos.first()
                                } else {
                                    propertiesMultiVideos = selVideos
                                }
                            },
                            onDelete = {
                                batchDeleteTargetVideos = selVideos
                            }
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    // Single item modals
    propertiesVideo?.let { video ->
        VideoPropertiesDialog(
            video = video,
            onDismiss = { propertiesVideo = null }
        )
    }

    propertiesMultiVideos?.let { vids ->
        MultiVideoPropertiesDialog(
            videos = vids,
            onDismiss = { propertiesMultiVideos = null }
        )
    }

    propertiesFolder?.let { folder ->
        FolderPropertiesDialog(
            folders = listOf(folder),
            onDismiss = { propertiesFolder = null }
        )
    }

    renameVideoTarget?.let { video ->
        RenameVideoDialog(
            video = video,
            onRename = { 
                onRenameVideo(video, it)
                selectedVideoIds = emptySet()
            },
            onDismiss = { renameVideoTarget = null }
        )
    }

    renameFolderTarget?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onRename = { newName ->
                onRenameFolder(folder, newName)
                selectedFolderPaths = emptySet()
            },
            onDismiss = { renameFolderTarget = null }
        )
    }

    deleteVideoTarget?.let { video ->
        DeleteConfirmDialog(
            video = video,
            onDelete = { onDeleteVideo(video) },
            onDismiss = { deleteVideoTarget = null }
        )
    }

    deleteFolderTarget?.let { folder ->
        BatchDeleteConfirmationDialog(
            itemCount = 1,
            itemType = "folder",
            onConfirm = {
                onDeleteFolders(listOf(folder))
            },
            onDismiss = { deleteFolderTarget = null }
        )
    }

    batchDeleteTargetVideos?.let { vids ->
        BatchDeleteConfirmationDialog(
            itemCount = vids.size,
            itemType = if (vids.size == 1) "video" else "videos",
            onConfirm = {
                onDeleteVideos(vids)
                selectedVideoIds = emptySet()
            },
            onDismiss = { batchDeleteTargetVideos = null }
        )
    }

    batchDeleteTargetFolders?.let { flds ->
        BatchDeleteConfirmationDialog(
            itemCount = flds.size,
            itemType = if (flds.size == 1) "folder" else "folders",
            onConfirm = {
                onDeleteFolders(flds)
                selectedFolderPaths = emptySet()
            },
            onDismiss = { batchDeleteTargetFolders = null }
        )
    }

    playlistVideoTarget?.let { video ->
        AddToPlaylistDialog(
            video = video,
            playlists = playlists,
            onSelectPlaylist = { playlistId -> 
                if (selectedVideoIds.isNotEmpty()) {
                    val selVideos = videos.filter { it.id in selectedVideoIds }
                    selVideos.forEach { onAddToPlaylist(playlistId, it) }
                    selectedVideoIds = emptySet()
                } else {
                    onAddToPlaylist(playlistId, video) 
                }
            },
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
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
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

    val animatedBlur by animateDpAsState(
        targetValue = if (isSelectionMode && !isSelected) 8.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "selectionBlur"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelectionMode && !isSelected) 0.35f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "selectionAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
            }
            .blur(animatedBlur)
            .clip(RoundedCornerShape(12.dp))
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) ExcavPalette.LogoBlue else ExcavPalette.Line,
                RoundedCornerShape(12.dp)
            )
            .tactilePress(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) ExcavPalette.SurfaceCardHighlight else ExcavPalette.SurfaceCard
    ) {
        Column {
            // 16:9 Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Thumbnail(video, Modifier.fillMaxSize())

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(ExcavPalette.LogoBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = ExcavPalette.Ink,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp,
                            lineBreak = LineBreak.Paragraph,
                            hyphens = Hyphens.None
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

