package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.Folder
import com.excavplayer.ui.theme.BadgeBackground
import com.excavplayer.ui.theme.BadgeShape
import com.excavplayer.ui.theme.CardShape
import com.excavplayer.ui.theme.FolderYellow
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDark
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.SurfaceGlass
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.TextTertiary
import com.excavplayer.ui.theme.ThumbnailShape

private val FolderCardShape = RoundedCornerShape(13.dp)

@Composable
fun FolderCard(
    folder: Folder,
    previewThumbnailUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExcavSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick() },
        shape = FolderCardShape,
        backgroundColor = SurfaceGlass,
        borderColor = SurfaceBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Yellow Folder Badge overlay
            Box(
                modifier = Modifier
                    .size(width = 78.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = FolderYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Small yellow folder badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(BadgeShape)
                        .background(BadgeBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = FolderYellow,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val sizeStr = formatFileSize(folder.totalSizeBytes)
                Text(
                    text = "${folder.videoCount} videos · $sizeStr",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Folder",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
