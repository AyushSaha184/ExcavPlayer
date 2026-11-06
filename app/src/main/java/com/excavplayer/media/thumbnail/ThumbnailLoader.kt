package com.excavplayer.media.thumbnail

import android.content.ComponentCallbacks2
import android.content.ContentUris
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.mediainfo.MediaThumbnailRetriever
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "ThumbnailLoader"
        private const val DISK_CACHE_DIR = "thumbnails_v1"
        private const val COMPRESSION_QUALITY = 85
        private const val MAX_DISK_CACHE_SIZE_BYTES = 64L * 1024 * 1024 // 64 MB disk limit
    }

    private val diskCacheScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatchers.io)
    private var lastTrimTimeMs = 0L

    // Dynamic memory cache: 1/8th of available heap memory, clamped between 16MB and 64MB
    private val maxCacheSizeBytes = run {
        val maxMemory = Runtime.getRuntime().maxMemory()
        (maxMemory / 8).toInt().coerceIn(16 * 1024 * 1024, 64 * 1024 * 1024)
    }

    private val memoryCache = object : LruCache<String, Bitmap>(maxCacheSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val diskCacheDir: File by lazy {
        File(context.cacheDir, DISK_CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    // In-flight request deduplication map to prevent redundant concurrent decodes
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    init {
        context.registerComponentCallbacks(this)
        logger.i(TAG, "ThumbnailLoader initialized with ${maxCacheSizeBytes / (1024 * 1024)} MB memory cache")
    }

    fun getFromCache(
        uriString: String,
        targetWidth: Int = 320,
        targetHeight: Int = 180
    ): Bitmap? {
        val cacheKey = buildCacheKey(uriString, targetWidth, targetHeight)
        return memoryCache.get(cacheKey)
    }

    suspend fun loadThumbnail(
        uriString: String,
        targetWidth: Int = 320,
        targetHeight: Int = 180
    ): Bitmap? = withContext(dispatchers.io) {
        val cacheKey = buildCacheKey(uriString, targetWidth, targetHeight)

        // 1. Check L1 Memory Cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Coalesce concurrent in-flight requests for the same key
        coroutineScope {
            var deferred = inFlightRequests[cacheKey]
            if (deferred == null) {
                val newDeferred = async(dispatchers.io) {
                    executeLoadThumbnail(uriString, cacheKey, targetWidth, targetHeight)
                }
                val existing = inFlightRequests.putIfAbsent(cacheKey, newDeferred)
                deferred = existing ?: newDeferred
            }

            try {
                deferred.await()
            } finally {
                inFlightRequests.remove(cacheKey)
            }
        }
    }

    private suspend fun executeLoadThumbnail(
        uriString: String,
        cacheKey: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        // Double check L1 cache
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Check L2 Disk Cache
        loadFromDiskCache(cacheKey)?.let { diskBitmap ->
            memoryCache.put(cacheKey, diskBitmap)
            return diskBitmap
        }

        // 3. Generate thumbnail from MediaStore / System / MediaMetadataRetriever / FFmpeg
        return try {
            val uri = Uri.parse(uriString)
            var bitmap: Bitmap? = null

            if (uri.scheme == "content") {
                bitmap = try {
                    val contentThumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                    if (contentThumb != null && !isSolidColor(contentThumb)) {
                        contentThumb
                    } else {
                        contentThumb?.recycle()
                        null
                    }
                } catch (_: Throwable) {
                    null
                }
            }

            if (bitmap == null) {
                val nativeRetriever = android.media.MediaMetadataRetriever()
                var ffmpegRetriever: MediaThumbnailRetriever? = null
                try {
                    val path = uri.path?.let { Uri.decode(it) }
                    try {
                        if (uri.scheme == "file" && path != null) {
                            nativeRetriever.setDataSource(path)
                        } else if (uri.scheme == null && uriString.startsWith("/")) {
                            nativeRetriever.setDataSource(uriString)
                        } else {
                            nativeRetriever.setDataSource(context, uri)
                        }
                    } catch (_: Throwable) {}

                    try {
                        ffmpegRetriever = MediaThumbnailRetriever().apply {
                            if (uri.scheme == "file" && path != null) {
                                setDataSource(path)
                            } else if (uri.scheme == null && uriString.startsWith("/")) {
                                setDataSource(uriString)
                            } else {
                                setDataSource(context, uri)
                            }
                        }
                    } catch (_: Throwable) {
                        ffmpegRetriever = null
                    }

                    // 1. First, try embedded picture (album art / poster / cover artwork)
                    val embeddedPicture = try {
                        nativeRetriever.embeddedPicture
                    } catch (_: Throwable) { null }
                        ?: try {
                            ffmpegRetriever?.getEmbeddedPicture()
                        } catch (_: Throwable) { null }

                    val embeddedBitmap = embeddedPicture?.let { pictureBytes ->
                        try {
                            BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
                        } catch (_: Throwable) { null }
                    }

                    if (embeddedBitmap != null) {
                        bitmap = embeddedBitmap
                    } else {
                        // 2. Video duration for offset calculation
                        val videoDuration = try {
                            nativeRetriever
                                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull() ?: 0L
                        } catch (_: Throwable) { 0L }

                        // Helper to extract frame at timeUs
                        fun extractFrame(timeUs: Long): Bitmap? {
                            var frame: Bitmap? = null
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    frame = nativeRetriever.getScaledFrameAtTime(
                                        timeUs,
                                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                        targetWidth,
                                        targetHeight
                                    )
                                }
                                if (frame == null) {
                                    frame = nativeRetriever.getFrameAtTime(
                                        timeUs,
                                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                    )
                                }
                            } catch (_: Throwable) {}

                            if (frame == null && ffmpegRetriever != null) {
                                try {
                                    frame = ffmpegRetriever.getFrameAtTime(timeUs)
                                } catch (_: Throwable) {}
                            }
                            return frame
                        }

                        // 3. Hybrid strategy: Try t=0 first, if null or solid-color (e.g. black intro), try 33%
                        var frame = extractFrame(0L)
                        if (frame == null || isSolidColor(frame)) {
                            if (videoDuration > 0) {
                                val offsetUs = (videoDuration * 0.33f * 1000L).toLong()
                                val secondFrame = extractFrame(offsetUs)
                                if (secondFrame != null) {
                                    frame?.recycle()
                                    frame = secondFrame
                                }
                            }
                        }
                        bitmap = frame
                    }
                } finally {
                    try { nativeRetriever.release() } catch (_: Throwable) {}
                    try { ffmpegRetriever?.release() } catch (_: Throwable) {}
                }
            }

            if (bitmap != null) {
                // Scale if dimensions significantly exceed target dimensions
                if (bitmap.width > targetWidth || bitmap.height > targetHeight) {
                    val scale = minOf(
                        targetWidth.toFloat() / bitmap.width,
                        targetHeight.toFloat() / bitmap.height
                    )
                    if (scale < 1.0f) {
                        val dstWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                        val dstHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true)
                        if (scaled != bitmap) {
                            bitmap.recycle()
                            bitmap = scaled
                        }
                    }
                }

                // Cache into L1 Memory immediately
                memoryCache.put(cacheKey, bitmap)
                // Cache into L2 Disk asynchronously without blocking UI return
                diskCacheScope.launch {
                    saveToDiskCache(cacheKey, bitmap)
                }
            }
            bitmap
        } catch (e: Exception) {
            logger.w(TAG, "Failed to load thumbnail for $uriString: ${e.message}")
            null
        }
    }

    internal fun isSolidColor(bitmap: Bitmap, threshold: Float = 0.7f): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        // Sample a grid in the center region (avoiding edges which may be black letterbox bars)
        val marginX = width / 10
        val marginY = height / 10
        val sampleAreaRight = width - marginX
        val sampleAreaBottom = height - marginY

        // Create a grid of sample points
        val gridSize = 10
        val stepX = (sampleAreaRight - marginX) / gridSize
        val stepY = (sampleAreaBottom - marginY) / gridSize

        if (stepX <= 0 || stepY <= 0) return false

        val sampledColors = mutableListOf<Int>()

        for (x in 0 until gridSize) {
            for (y in 0 until gridSize) {
                val pixelX = marginX + x * stepX
                val pixelY = marginY + y * stepY
                if (pixelX in 0 until width && pixelY in 0 until height) {
                    sampledColors.add(bitmap.getPixel(pixelX, pixelY))
                }
            }
        }

        if (sampledColors.isEmpty()) return false

        // Use the first color as reference
        val referenceColor = sampledColors[0]
        val referenceR = (referenceColor shr 16) and 0xFF
        val referenceG = (referenceColor shr 8) and 0xFF
        val referenceB = referenceColor and 0xFF

        // Count similar colors (within RGB tolerance of 30)
        val tolerance = 30
        val similarCount = sampledColors.count { color ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            abs(r - referenceR) <= tolerance &&
                abs(g - referenceG) <= tolerance &&
                abs(b - referenceB) <= tolerance
        }

        val similarityRatio = similarCount.toFloat() / sampledColors.size
        return similarityRatio >= threshold
    }

    private fun buildCacheKey(uriString: String, width: Int, height: Int): String {
        return "${uriString}_${width}x${height}"
    }

    private fun getDiskFileForKey(key: String): File {
        val hash = hashKey(key)
        return File(diskCacheDir, "$hash.thumb")
    }

    private fun hashKey(key: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(key.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            key.hashCode().toString()
        }
    }

    private fun loadFromDiskCache(key: String): Bitmap? {
        val file = getDiskFileForKey(key)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to read thumbnail from disk cache: ${e.message}")
            null
        }
    }

    private fun saveToDiskCache(key: String, bitmap: Bitmap) {
        try {
            val file = getDiskFileForKey(key)
            FileOutputStream(file).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, COMPRESSION_QUALITY, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
                }
                out.flush()
            }
            trimDiskCacheIfNeeded()
        } catch (e: Exception) {
            logger.w(TAG, "Failed to save thumbnail to disk cache: ${e.message}")
        }
    }

    private fun trimDiskCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastTrimTimeMs < 60_000L) return
        lastTrimTimeMs = now
        try {
            val files = diskCacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize > MAX_DISK_CACHE_SIZE_BYTES) {
                val sorted = files.sortedBy { it.lastModified() }
                for (file in sorted) {
                    totalSize -= file.length()
                    file.delete()
                    if (totalSize <= (MAX_DISK_CACHE_SIZE_BYTES * 3) / 4) break
                }
            }
        } catch (_: Exception) {}
    }

    fun evictThumbnail(uriString: String) {
        listOf(
            buildCacheKey(uriString, 120, 90),
            buildCacheKey(uriString, 240, 160),
            buildCacheKey(uriString, 360, 240),
            buildCacheKey(uriString, 480, 320)
        ).forEach { key ->
            memoryCache.remove(key)
            runCatching {
                val file = getDiskFileForKey(key)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        inFlightRequests.clear()
        try {
            diskCacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            logger.w(TAG, "Failed to clean disk cache directory: ${e.message}")
        }
    }

    // ComponentCallbacks2 memory trimming
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                logger.i(TAG, "Memory critical: Evicting all thumbnail memory cache")
                memoryCache.evictAll()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                logger.i(TAG, "Memory pressure ($level): Trimming thumbnail memory cache by 50%")
                memoryCache.trimToSize(maxCacheSizeBytes / 2)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        logger.w(TAG, "Low memory event: Evicting thumbnail memory cache")
        memoryCache.evictAll()
    }
}
