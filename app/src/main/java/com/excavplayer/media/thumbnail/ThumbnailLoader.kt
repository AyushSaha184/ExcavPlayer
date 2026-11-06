package com.excavplayer.media.thumbnail

import android.content.ComponentCallbacks2
import android.content.ContentUris
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.mediainfo.MediaThumbnailRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Production video thumbnail loader rebuilt from NextPlayer's VideoThumbnailDecoder pipeline:
 * 1. L1 Memory Cache (bounded 1/8 heap)
 * 2. L2 Disk Cache (persistent WEBP/JPEG storage)
 * 3. Embedded Picture extraction (album art/poster/cover)
 * 4. First frame extraction (t=0)
 * 5. Solid color detection & rejection (10x10 inner grid sampling with RGB tolerance 30)
 * 6. Percentage fallback (t=33% of video duration) when first frame is null or solid color
 * 7. Dual retriever fallback: Android native MediaMetadataRetriever + NextLib MediaThumbnailRetriever (FFmpeg)
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "ThumbnailLoader"
        private const val DISK_CACHE_DIR = "thumbnails_v2"
        private const val COMPRESSION_QUALITY = 80
        private const val MAX_DISK_CACHE_SIZE_BYTES = 64L * 1024 * 1024 // 64 MB
        private const val MAX_CONCURRENT_EXTRACTIONS = 3
    }

    private val diskCacheScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val extractionSemaphore = Semaphore(MAX_CONCURRENT_EXTRACTIONS)
    private var lastTrimTimeMs = 0L

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

    /**
     * Preloads thumbnails in the background so they are ready before the user scrolls to them.
     */
    fun preloadThumbnails(
        uriStrings: List<String>,
        targetWidth: Int = 320,
        targetHeight: Int = 180
    ) {
        if (uriStrings.isEmpty()) return
        diskCacheScope.launch {
            for (uriString in uriStrings) {
                val cacheKey = buildCacheKey(uriString, targetWidth, targetHeight)
                if (memoryCache.get(cacheKey) == null) {
                    loadThumbnail(uriString, targetWidth, targetHeight)
                }
            }
        }
    }

    suspend fun loadThumbnail(
        uriString: String,
        targetWidth: Int = 320,
        targetHeight: Int = 180
    ): Bitmap? = withContext(dispatchers.io) {
        val cacheKey = buildCacheKey(uriString, targetWidth, targetHeight)

        // 1. Check L1 Memory Cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Coalesce concurrent in-flight requests for identical cache key
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
        // Double-check L1 memory cache
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Check L2 Disk Cache
        loadFromDiskCache(cacheKey)?.let { diskBitmap ->
            memoryCache.put(cacheKey, diskBitmap)
            return diskBitmap
        }

        // 3. Throttle heavy extraction pipeline with Semaphore to avoid CPU/IO thrashing
        return extractionSemaphore.withPermit {
            // Re-check caches after acquiring permit
            memoryCache.get(cacheKey)?.let { return@withPermit it }
            loadFromDiskCache(cacheKey)?.let { diskBitmap ->
                memoryCache.put(cacheKey, diskBitmap)
                return@withPermit diskBitmap
            }

            extractThumbnailFromSource(uriString, cacheKey, targetWidth, targetHeight)
        }
    }

    private fun extractThumbnailFromSource(
        uriString: String,
        cacheKey: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        // Generate thumbnail using optimized hybrid extraction
        return try {
            val uri = Uri.parse(uriString)
            var bitmap: Bitmap? = null

            // First: Quick platform MediaStore thumbnail check for content:// URIs
            if (uri.scheme == "content") {
                try {
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
                        bitmap = contentThumb
                    } else {
                        contentThumb?.recycle()
                    }
                } catch (_: Throwable) {}
            }

            // Dual retriever hybrid pipeline (Native first, lazy FFmpeg fallback)
            if (bitmap == null) {
                val nativeRetriever = MediaMetadataRetriever()
                var ffmpegRetriever: MediaThumbnailRetriever? = null
                var ffmpegAttempted = false

                fun getFfmpeg(): MediaThumbnailRetriever? {
                    if (!ffmpegAttempted) {
                        ffmpegAttempted = true
                        ffmpegRetriever = runCatching {
                            MediaThumbnailRetriever().apply {
                                setDataSourceSafe(this, uri, uriString)
                            }
                        }.getOrNull()
                    }
                    return ffmpegRetriever
                }

                try {
                    setDataSourceSafe(nativeRetriever, uri, uriString)

                    // Step 1: Check embedded picture (album art / poster / cover artwork)
                    val embeddedPicture = runCatching { nativeRetriever.embeddedPicture }.getOrNull()
                        ?: runCatching { getFfmpeg()?.getEmbeddedPicture() }.getOrNull()

                    val embeddedBitmap = embeddedPicture?.let { pictureBytes ->
                        runCatching {
                            BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
                        }.getOrNull()
                    }

                    if (embeddedBitmap != null) {
                        bitmap = embeddedBitmap
                    } else {
                        // Video duration for percentage offset calculation
                        val durationMs = runCatching {
                            nativeRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        }.getOrNull() ?: 0L

                        // Helper to extract frame at timeUs using dual retrievers
                        fun extractFrame(timeUs: Long): Bitmap? {
                            var frame: Bitmap? = null
                            // Try native scaled frame first (API 27+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                frame = runCatching {
                                    nativeRetriever.getScaledFrameAtTime(
                                        timeUs,
                                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                        targetWidth,
                                        targetHeight
                                    )
                                }.getOrNull()
                            }
                            if (frame == null) {
                                frame = runCatching {
                                    nativeRetriever.getFrameAtTime(
                                        timeUs,
                                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                    )
                                }.getOrNull()
                            }
                            // Fallback to FFmpeg retriever only when native fails
                            if (frame == null) {
                                frame = runCatching {
                                    getFfmpeg()?.getFrameAtTime(timeUs)
                                }.getOrNull()
                            }
                            return frame
                        }

                        // Step 2: Try first frame (t = 0), test for solid color rejection
                        var frame = extractFrame(0L)
                        if (frame == null || isSolidColor(frame)) {
                            var secondFrame: Bitmap? = null
                            // Fast check: Try near-start offset (2-3s) before seeking deeply across large files
                            if (durationMs > 2_000L) {
                                val nearStartUs = minOf(3_000_000L, (durationMs * 1000L) / 4)
                                val nearStartFrame = extractFrame(nearStartUs)
                                if (nearStartFrame != null && !isSolidColor(nearStartFrame)) {
                                    secondFrame = nearStartFrame
                                } else {
                                    nearStartFrame?.recycle()
                                }
                            }

                            // Step 3: Fallback to frame at 33% of duration if near-start was also solid/null
                            if (secondFrame == null && durationMs > 0L) {
                                val timeUs = (durationMs * 0.33f * 1000L).toLong()
                                secondFrame = extractFrame(timeUs)
                            }

                            if (secondFrame != null) {
                                frame?.recycle()
                                frame = secondFrame
                            }
                        }
                        bitmap = frame
                    }
                } finally {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            nativeRetriever.close()
                        } else {
                            @Suppress("DEPRECATION")
                            nativeRetriever.release()
                        }
                    }
                    runCatching { ffmpegRetriever?.release() }
                }
            }

            if (bitmap != null) {
                // Normalize and downsample if dimensions significantly exceed target dimensions
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
                // Save to L2 Disk asynchronously
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

    private fun setDataSourceSafe(retriever: MediaMetadataRetriever, uri: Uri, uriString: String) {
        val path = uri.path?.let { Uri.decode(it) }
        when {
            uri.scheme == "file" && path != null -> retriever.setDataSource(path)
            uri.scheme == null && uriString.startsWith("/") -> retriever.setDataSource(uriString)
            else -> retriever.setDataSource(context, uri)
        }
    }

    private fun setDataSourceSafe(retriever: MediaThumbnailRetriever, uri: Uri, uriString: String) {
        val path = uri.path?.let { Uri.decode(it) }
        when {
            uri.scheme == "file" && path != null -> retriever.setDataSource(path)
            uri.scheme == null && uriString.startsWith("/") -> retriever.setDataSource(uriString)
            else -> retriever.setDataSource(context, uri)
        }
    }

    /**
     * Checks if a bitmap is mostly a solid color.
     * Samples a 10x10 grid inside the inner 80% boundary of the bitmap, comparing pixels against
     * the reference color with an RGB tolerance of 30.
     * Returns true if threshold (default 70%) or more of sampled pixels are similar.
     */
    internal fun isSolidColor(bitmap: Bitmap, threshold: Float = 0.7f): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true

        // Sample a grid in the center region (avoiding edges which may be black letterbox bars)
        val marginX = width / 10
        val marginY = height / 10
        val sampleAreaRight = width - marginX
        val sampleAreaBottom = height - marginY

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

        val referenceColor = sampledColors[0]
        val referenceR = (referenceColor shr 16) and 0xFF
        val referenceG = (referenceColor shr 8) and 0xFF
        val referenceB = referenceColor and 0xFF

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
        } catch (_: Exception) {
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
            buildCacheKey(uriString, 320, 180),
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
