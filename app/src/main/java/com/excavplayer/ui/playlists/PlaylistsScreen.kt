package com.excavplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.excavplayer.R
import com.excavplayer.domain.model.Playlist
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var isCreateDialogOpen by rememberSaveable { mutableStateOf(false) }
    var menuForPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamePlaylist by rememberSaveable { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        SectionTitle(stringResource(R.string.playlists)) {
            Button(
                onClick = { isCreateDialogOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                shape = ExcavShapes.Pill,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_playlist),
                    tint = ExcavPalette.Ink,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "New",
                    color = ExcavPalette.Ink,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = stringResource(R.string.empty_playlists)
            ) {
                Button(
                    onClick = { isCreateDialogOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                    shape = ExcavShapes.Pill
                ) {
                    Text(
                        text = stringResource(R.string.new_playlist),
                        color = ExcavPalette.Ink,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Box {
                        PlaylistRow(
                            playlist = playlist,
                            onOverflow = { menuForPlaylistId = playlist.id },
                            onClick = {}
                        )

                        DropdownMenu(
                            expanded = menuForPlaylistId == playlist.id,
                            onDismissRequest = { menuForPlaylistId = null },
                            modifier = Modifier
                                .background(ExcavPalette.SurfaceCard)
                                .border(1.dp, ExcavPalette.Line, ExcavShapes.Card)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename), color = ExcavPalette.Text) },
                                onClick = {
                                    renamePlaylist = playlist
                                    menuForPlaylistId = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = ExcavPalette.Error) },
                                onClick = {
                                    onDelete(playlist.id)
                                    menuForPlaylistId = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isCreateDialogOpen) {
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            confirmButtonLabel = stringResource(R.string.create),
            onConfirm = {
                onCreate(it)
                isCreateDialogOpen = false
            },
            onDismiss = { isCreateDialogOpen = false }
        )
    }

    renamePlaylist?.let { playlist ->
        PlaylistNameDialog(
            title = stringResource(R.string.rename),
            confirmButtonLabel = stringResource(R.string.rename),
            initialValue = playlist.title,
            onConfirm = {
                onRename(playlist.id, it)
                renamePlaylist = null
            },
            onDismiss = { renamePlaylist = null }
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    confirmButtonLabel: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name), color = ExcavPalette.TextMuted) },
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
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                shape = ExcavShapes.Pill
            ) {
                Text(confirmButtonLabel, color = ExcavPalette.Ink, fontWeight = FontWeight.Bold)
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
