package com.excavplayer.media.thumbnail

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeekPreviewLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "SeekPreviewLoader"
        const val BUCKET_MS = 5_000L // 5s per storyboard bucket for granular preview
        private const val TARGET_WIDTH = 420
        private const val TARGET_HEIGHT = 236
        private const val MAX_CACHE_ENTRIES = 120
    }

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    // Set of cache keys that failed extraction to prevent tight retry loops
    private val failedKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Serial lock: Ensure only ONE MediaMetadataRetriever executes at a time
    // to prevent heavy I/O contention with ExoPlayer on slow storage
    private val retrieverMutex = Mutex()

    // Reusable active retriever session to eliminate ~200ms setup overhead on every scrub tick
    private var activeRetriever: MediaMetadataRetriever? = null
    private var activeUriString: String? = null
    private var activeRotation: Int = 0

    init {
        context.registerComponentCallbacks(this)
    }

    fun getFromCache(videoId: String, targetMs: Long): Bitmap? {
        val bucket = targetMs.coerceAtLeast(0L) / BUCKET_MS
        val key = "$videoId:$bucket"
        return memoryCache.get(key)
    }

    private fun getOrCreateRetriever(uri: Uri): MediaMetadataRetriever {
        val uriStr = uri.toString()
        if (activeRetriever != null && activeUriString == uriStr) {
            return activeRetriever!!
        }

        try {
            activeRetriever?.release()
        } catch (_: Throwable) {}

        val newRetriever = MediaMetadataRetriever()
        newRetriever.setDataSource(context, uri)
        val rotationStr = newRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        activeRotation = rotationStr?.toIntOrNull() ?: 0
        activeRetriever = newRetriever
        activeUriString = uriStr
        return newRetriever
    }

    suspend fun loadPreview(
        videoId: String,
        uri: Uri,
        targetMs: Long,
        durationMs: Long
    ): Bitmap? = withContext(dispatchers.io) {
        if (durationMs <= 0L || videoId.isBlank()) return@withContext null

        val clampedTarget = targetMs.coerceIn(0L, durationMs)
        val bucket = clampedTarget / BUCKET_MS
        val key = "$videoId:$bucket"

        memoryCache.get(key)?.let { return@withContext it }
        if (failedKeys.contains(key)) return@withContext null

        retrieverMutex.withLock {
            // Re-check cache after acquiring lock
            memoryCache.get(key)?.let { return@withContext it }
            if (failedKeys.contains(key)) return@withContext null

            try {
                val retriever = getOrCreateRetriever(uri)
                val timeUs = (clampedTarget * 1000L).coerceAtLeast(0L)

                val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        TARGET_WIDTH,
                        TARGET_HEIGHT
                    )
                } else {
                    val full = retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    full?.let {
                        val scaled = Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true)
                        if (scaled != it) it.recycle()
                        scaled
                    }
                }

                if (rawBitmap != null) {
                    val rotation = activeRotation
                    val rotatedBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        val rotated = Bitmap.createBitmap(
                            rawBitmap,
                            0,
                            0,
                            rawBitmap.width,
                            rawBitmap.height,
                            matrix,
                            true
                        )
                        if (rotated != rawBitmap) rawBitmap.recycle()
                        rotated
                    } else {
                        rawBitmap
                    }

                    // Convert to RGB_565 without alpha to conserve RAM
                    val finalBitmap = if (rotatedBitmap.config != Bitmap.Config.RGB_565) {
                        val rgb565 = rotatedBitmap.copy(Bitmap.Config.RGB_565, false)
                        if (rgb565 != null) {
                            rotatedBitmap.recycle()
                            rgb565
                        } else {
                            rotatedBitmap
                        }
                    } else {
                        rotatedBitmap
                    }

                    memoryCache.put(key, finalBitmap)
                    finalBitmap
                } else {
                    failedKeys.add(key)
                    null
                }
            } catch (e: Throwable) {
                logger.w(TAG, "Failed to extract seek preview at $clampedTarget ms for video $videoId: ${e.message}")
                failedKeys.add(key)
                try {
                    activeRetriever?.release()
                } catch (_: Throwable) {}
                activeRetriever = null
                activeUriString = null
                null
            }
        }
    }

    suspend fun prefetchAdjacent(
        videoId: String,
        uri: Uri,
        targetMs: Long,
        durationMs: Long
    ) = withContext(dispatchers.io) {
        if (durationMs <= 0L) return@withContext
        val currentBucket = targetMs.coerceIn(0L, durationMs) / BUCKET_MS
        val prevMs = (currentBucket - 1) * BUCKET_MS
        val nextMs = (currentBucket + 1) * BUCKET_MS

        if (prevMs >= 0L) {
            loadPreview(videoId, uri, prevMs, durationMs)
        }
        if (nextMs <= durationMs) {
            loadPreview(videoId, uri, nextMs, durationMs)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            memoryCache.evictAll()
            failedKeys.clear()
            try {
                activeRetriever?.release()
            } catch (_: Throwable) {}
            activeRetriever = null
            activeUriString = null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        memoryCache.evictAll()
        failedKeys.clear()
        try {
            activeRetriever?.release()
        } catch (_: Throwable) {}
        activeRetriever = null
        activeUriString = null
    }
}
