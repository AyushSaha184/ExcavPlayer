package com.excavplayer.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.excavplayer.domain.model.Folder
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ExcavIconButton
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.FolderCard
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.excavBackground

@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel,
    onFolderClick: (Folder) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folders by viewModel.folders.collectAsState()
    val thumbnails by viewModel.folderThumbnails.collectAsState()
    var showTopMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .excavBackground()
    ) {
        // Top Bar
        ExcavTopBar(
            showBrandLogo = true,
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
                            text = { Text("Rescan Media", color = TextPrimary) },
                            onClick = {
                                showTopMenu = false
                                viewModel.refresh()
                            }
                        )
                    }
                }
            }
        )

        if (folders.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Folder,
                title = "No Folders Available",
                message = "No video folders found on your device storage.",
                actionButtonText = "Rescan Folders",
                onActionClick = { viewModel.refresh() }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    com.excavplayer.ui.components.ExcavSectionHeader(
                        title = "Folders",
                        fontSize = 18.sp
                    )
                }

                items(folders, key = { it.path }) { folder ->
                    val previewUri = thumbnails[folder.path]
                    FolderCard(
                        folder = folder,
                        previewThumbnailUri = previewUri,
                        onClick = { onFolderClick(folder) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
