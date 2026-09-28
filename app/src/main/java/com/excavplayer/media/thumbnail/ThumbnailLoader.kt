package com.excavplayer.media.thumbnail

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "ThumbnailLoader"
        private const val MAX_CACHE_SIZE_BYTES = 20 * 1024 * 1024 // 20 MB
    }

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    suspend fun loadThumbnail(
        uriString: String,
        targetWidth: Int = 320,
        targetHeight: Int = 180
    ): Bitmap? = withContext(dispatchers.io) {
        val cacheKey = "${uriString}_${targetWidth}x${targetHeight}"
        memoryCache.get(cacheKey)?.let {
            return@withContext it
        }

        try {
            val uri = Uri.parse(uriString)
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), null)
            } else {
                val id = ContentUris.parseId(uri)
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            logger.w(TAG, "Failed to load thumbnail for $uriString: ${e.message}")
            null
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
