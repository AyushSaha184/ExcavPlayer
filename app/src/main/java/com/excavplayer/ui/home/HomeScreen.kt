package com.excavplayer.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.excavplayer.R
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes

data class VideoGroup(
    val id: String,
    val name: String,
    val path: String,
    val videos: List<Video>
)

@Composable
fun HomeScreen(
    continueWatching: List<Video>,
    folders: List<Folder>,
    videos: List<Video> = emptyList(),
    playlists: List<Playlist>,
    favorites: List<Video>,
    onPlay: (Video) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onToggleFavorite: (Video) -> Unit,
    onAddToPlaylist: (Long, Video) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenameVideo: (Video, String) -> Unit,
    onDeleteVideo: (Video) -> Unit,
    onRemoveFromContinueWatching: (Video) -> Unit = {}
) {
    val context = LocalContext.current
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = permissions.entries.any { it.value }
    }

    var selectedGroup by remember { mutableStateOf<VideoGroup?>(null) }
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    var playlistVideoTarget by remember { mutableStateOf<Video?>(null) }

    // Accurate Grouping: group videos by folder / series accurately
    val groups = remember(videos, folders) {
        if (folders.isNotEmpty()) {
            folders.mapNotNull { folder ->
                val folderVids = videos.filter { it.folderPath == folder.path || (it.folderName.isNotEmpty() && it.folderName == folder.name) }
                if (folderVids.isNotEmpty()) {
                    VideoGroup(
                        id = folder.path,
                        name = folder.name,
                        path = folder.path,
                        videos = folderVids
                    )
                } else null
            }
        } else {
            videos.groupBy { it.folderName.ifEmpty { "Videos" } }.map { (name, vids) ->
                VideoGroup(
                    id = name,
                    name = name,
                    path = vids.firstOrNull()?.folderPath.orEmpty(),
                    videos = vids
                )
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = selectedGroup != null) {
        selectedGroup = null
    }

    // If a group is selected, display the group's video list view
    if (selectedGroup != null) {
        val group = selectedGroup!!
        val groupVideos = remember(group, videos) {
            videos.filter { it.folderPath == group.path || it.folderName == group.name }.ifEmpty { group.videos }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Group Header with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedGroup = null },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ExcavPalette.SurfaceCard)
                        .border(1.dp, ExcavPalette.Line, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ExcavPalette.Text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        color = ExcavPalette.Text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${groupVideos.size} ${if (groupVideos.size == 1) "video" else "videos"} • ${formatFileSize(groupVideos.sumOf { it.sizeBytes })}",
                        color = ExcavPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                }
            }

            if (groupVideos.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.VideoLibrary,
                    label = "No videos in ${group.name}"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(groupVideos, key = { it.id }) { video ->
                        Box {
                            ListVideoRow(
                                video = video,
                                onClick = { onPlay(video) },
                                onMoreClick = { selectedVideoForMenu = video }
                            )

                            if (selectedVideoForMenu?.id == video.id) {
                                val isFav = favorites.any { it.id == video.id }
                                VideoOptionsMenu(
                                    expanded = true,
                                    video = video,
                                    isFavorite = isFav,
                                    onDismiss = { selectedVideoForMenu = null },
                                    onToggleFavorite = { onToggleFavorite(video) },
                                    onAddToPlaylist = { playlistVideoTarget = video },
                                    onRename = { renameVideoTarget = video },
                                    onProperties = { propertiesVideo = video },
                                    onDelete = { deleteVideoTarget = video }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    } else {
        // Main Home View
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Storage Permission Warning Banner if permission is not granted
            if (!hasStoragePermission) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ExcavShapes.Card)
                            .border(1.dp, ExcavPalette.Error.copy(alpha = 0.4f), ExcavShapes.Card),
                        color = ExcavPalette.SurfaceCard
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(ExcavPalette.ErrorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = ExcavPalette.Error,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "Storage Permission Required",
                                color = ExcavPalette.Text,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "ExcavPlayer requires access to your media files to display and play your video library.",
                                color = ExcavPalette.TextMuted,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(16.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                                        } else {
                                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        permissionLauncher.launch(permissions)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                                    shape = ExcavShapes.Pill
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = ExcavPalette.Ink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Grant Permission",
                                        color = ExcavPalette.Ink,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    },
                                    shape = ExcavShapes.Pill
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = ExcavPalette.Text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "App Settings",
                                        color = ExcavPalette.Text
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Continue Watching: Left-Swipable Horizontal Row (LazyRow)
            if (continueWatching.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.continue_watching))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(continueWatching, key = { "cw_${it.id}" }) { video ->
                                Box {
                                    ContinueWatchingRowCard(
                                        video = video,
                                        onClick = { onPlay(video) },
                                        onMoreClick = { selectedVideoForMenu = video }
                                    )

                                    if (selectedVideoForMenu?.id == video.id) {
                                        val isFav = favorites.any { it.id == video.id }
                                        VideoOptionsMenu(
                                            expanded = true,
                                            video = video,
                                            isFavorite = isFav,
                                            onDismiss = { selectedVideoForMenu = null },
                                            onToggleFavorite = { onToggleFavorite(video) },
                                            onAddToPlaylist = { playlistVideoTarget = video },
                                            onRename = { renameVideoTarget = video },
                                            onProperties = { propertiesVideo = video },
                                            onDelete = { deleteVideoTarget = video },
                                            onRemoveFromContinueWatching = { onRemoveFromContinueWatching(video) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Groups Section
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Groups")
            }

            if (groups.isEmpty() && continueWatching.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Default.FolderOff,
                        label = stringResource(R.string.empty_folders)
                    )
                }
            } else {
                items(groups, key = { "group_${it.id}" }) { group ->
                    GroupCard(
                        groupName = group.name,
                        videos = group.videos,
                        onClick = { selectedGroup = group }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // Modals & Dialogs
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
