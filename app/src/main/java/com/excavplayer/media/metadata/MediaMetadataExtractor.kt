package com.excavplayer.media.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedVideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrate: Long?,
    val rotation: Int,
    val mimeType: String?
)

@Singleton
class MediaMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "MediaMetadataExtractor"
    }

    suspend fun extractMetadata(uri: Uri): ExtractedVideoMetadata? = withContext(dispatchers.io) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            ExtractedVideoMetadata(
                durationMs = durationStr?.toLongOrNull() ?: 0L,
                width = widthStr?.toIntOrNull() ?: 0,
                height = heightStr?.toIntOrNull() ?: 0,
                bitrate = bitrateStr?.toLongOrNull(),
                rotation = rotationStr?.toIntOrNull() ?: 0,
                mimeType = mimeStr
            )
        } catch (e: Exception) {
            logger.w(TAG, "Failed to extract metadata for $uri: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
