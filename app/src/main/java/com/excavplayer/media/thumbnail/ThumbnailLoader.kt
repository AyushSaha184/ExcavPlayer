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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
    }

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

        // 3. Generate thumbnail from MediaStore / System
        return try {
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
                // Cache into L1 Memory
                memoryCache.put(cacheKey, bitmap)
                // Cache into L2 Disk asynchronously
                saveToDiskCache(cacheKey, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            logger.w(TAG, "Failed to load thumbnail for $uriString: ${e.message}")
            null
        }
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
        } catch (e: Exception) {
            logger.w(TAG, "Failed to save thumbnail to disk cache: ${e.message}")
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
