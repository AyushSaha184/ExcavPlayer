package com.excavplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.media.thumbnail.ThumbnailLoader
import com.excavplayer.ui.theme.BadgeBackground
import com.excavplayer.ui.theme.BadgeShape
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.ThumbnailShape

val LocalThumbnailLoader = compositionLocalOf<ThumbnailLoader?> { null }

@Composable
fun AsyncThumbnail(
    uriString: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ThumbnailShape,
    durationText: String? = null,
    progressPercentage: Float? = null,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidth: Int = 400,
    targetHeight: Int = 240
) {
    val thumbnailLoader = LocalThumbnailLoader.current
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(uriString) { mutableStateOf(true) }

    LaunchedEffect(uriString) {
        if (thumbnailLoader != null) {
            val loaded = thumbnailLoader.loadThumbnail(uriString, targetWidth, targetHeight)
            bitmap = loaded
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceDarkElevated)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDarkElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Duration Badge (bottom-right translucent pill)
        if (!durationText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 7.dp,
                        bottom = if (progressPercentage != null && progressPercentage > 0f) 14.dp else 7.dp
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(BadgeBackground)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                androidx.compose.material3.Text(
                    text = durationText,
                    color = TextPrimary,
                    fontSize = 10.5.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 1
                )
            }
        }

        // Integrated Progress Bar (bottom = 7dp, start = 7dp, fill width minus ~8dp)
        if (progressPercentage != null && progressPercentage > 0f) {
            ExcavProgressBar(
                progress = progressPercentage,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 7.dp, end = 7.dp, bottom = 7.dp),
                height = 3.dp,
                activeColor = CyanAccent,
                trackColor = Color(0x66000000)
            )
        }
    }
}
