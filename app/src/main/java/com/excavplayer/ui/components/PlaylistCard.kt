package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Playlist
import com.excavplayer.ui.theme.BadgeBackground
import com.excavplayer.ui.theme.CardShape
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.FavoriteRed
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDark
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.ThumbnailShape

@Composable
fun PlaylistCard(
    playlist: Playlist,
    previewThumbnailUri: String?,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavoritesVirtualPlaylist: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceDark)
            .border(0.5.dp, SurfaceBorder, CardShape)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Artwork with optional heart badge if it's Favorites
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 56.dp)
                .clip(ThumbnailShape)
        ) {
            if (previewThumbnailUri != null) {
                AsyncThumbnail(
                    uriString = previewThumbnailUri,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(SurfaceDarkElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavoritesVirtualPlaylist) Icons.Default.Favorite else Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = if (isFavoritesVirtualPlaylist) FavoriteRed else CyanAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (isFavoritesVirtualPlaylist) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(BadgeBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = FavoriteRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${playlist.itemCount} videos",
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceDarkElevated)
            ) {
                DropdownMenuItem(
                    text = { Text("Play All", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onPlayAllClick()
                    }
                )
                if (!isFavoritesVirtualPlaylist) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = FavoriteRed) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}
