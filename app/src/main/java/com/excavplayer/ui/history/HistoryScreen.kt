package com.excavplayer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ExcavIconButton
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.HistoryCard
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.DialogShape
import com.excavplayer.ui.theme.FavoriteRed
import com.excavplayer.ui.theme.PillShape
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.excavBackground

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToSearch: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedHistory by viewModel.groupedHistory.collectAsState()
    val videosMap by viewModel.videosMap.collectAsState()

    var showTopMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .excavBackground()
    ) {
        ExcavTopBar(
            title = "Watch History",
            actions = {
                ExcavIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick = onNavigateToSearch,
                    touchTargetSize = 38.dp,
                    iconSize = 22.dp,
                    tint = TextPrimary
                )

                Box {
                    ExcavIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        onClick = { showTopMenu = true },
                        touchTargetSize = 38.dp,
                        iconSize = 20.dp,
                        tint = TextPrimary
                    )

                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false },
                        modifier = Modifier.background(SurfaceDarkElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear All History", color = FavoriteRed) },
                            onClick = {
                                showTopMenu = false
                                showClearConfirmDialog = true
                            }
                        )
                    }
                }
            }
        )

        if (groupedHistory.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = "No Watch History",
                message = "Videos you watch will show up here along with your playback progress."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                groupedHistory.forEach { section ->
                    item(key = "header_${section.title}") {
                        Spacer(modifier = Modifier.height(4.dp))
                        com.excavplayer.ui.components.ExcavSectionHeader(
                            title = section.title,
                            fontSize = 16.sp
                        )
                    }

                    items(section.entries, key = { it.videoId }) { entry ->
                        val video = videosMap[entry.videoId]
                        HistoryCard(
                            entry = entry,
                            video = video,
                            onClick = {
                                viewModel.resume(entry)
                                video?.let { onPlayVideo(it) }
                            },
                            onPlayFromStart = {
                                viewModel.playFromBeginning(entry)
                                video?.let { onPlayVideo(it) }
                            },
                            onRemoveFromHistory = {
                                viewModel.removeFromHistory(entry.videoId)
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                shape = DialogShape,
                containerColor = SurfaceDarkElevated,
                title = {
                    Text(
                        text = "Clear Watch History",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to clear your entire watch history? This action cannot be undone.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearHistory()
                            showClearConfirmDialog = false
                        },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FavoriteRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Clear All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearConfirmDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    }
}
