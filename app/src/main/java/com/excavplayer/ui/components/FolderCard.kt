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
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.ThumbnailShape

@Composable
fun FolderCard(
    folder: Folder,
    previewThumbnailUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        // Thumbnail with Yellow Folder Badge overlay at bottom-left
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 56.dp)
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
                        .background(com.excavplayer.ui.theme.SurfaceDarkElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = FolderYellow,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Small yellow folder badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(BadgeShape)
                    .background(BadgeBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = FolderYellow,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            val sizeStr = formatFileSize(folder.totalSizeBytes)
            Text(
                text = "${folder.videoCount} videos · $sizeStr",
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open Folder",
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}
