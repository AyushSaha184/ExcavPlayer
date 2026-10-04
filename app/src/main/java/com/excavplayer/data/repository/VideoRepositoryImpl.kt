package com.excavplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.mapper.toDomain
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.VideoRepository
import com.excavplayer.media.discovery.MediaSyncManager
import com.excavplayer.media.thumbnail.ThumbnailLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val mediaSyncManager: MediaSyncManager,
    private val thumbnailLoader: ThumbnailLoader,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : VideoRepository {

    companion object {
        private const val TAG = "VideoRepository"
    }

    override fun observeVideos(): Flow<List<Video>> {
        return videoDao.observeAllVideosWithMetadata()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observePagedVideos(): Flow<PagingData<Video>> {
        return Pager(
            config = PagingConfig(
                pageSize = 40,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { videoDao.pagingSourceAllVideos() }
        ).flow
            .map { pagingData -> pagingData.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeVideoById(id: String): Flow<Video?> {
        return videoDao.observeVideoById(id)
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override suspend fun getVideoById(id: String): Video? = withContext(dispatchers.io) {
        videoDao.getVideoById(id)?.toDomain()
    }

    override fun observeFolders(): Flow<List<Folder>> {
        return videoDao.observeFolders()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeVideosInFolder(folderPath: String): Flow<List<Video>> {
        val clean = folderPath.trim().trimEnd('/')
        val rel = clean.removePrefix("/storage/emulated/0/").removePrefix("/storage/emulated/0").trimStart('/')
        val abs = if (clean.startsWith("/storage/emulated/0")) clean else "/storage/emulated/0/$clean"

        return videoDao.observeVideosInFolder(
            path1 = clean,
            path2 = abs,
            path3 = rel,
            path4 = if (rel.isNotEmpty()) "$rel/" else "",
            relPath1 = if (rel.isNotEmpty()) "$rel/" else "",
            relPath2 = rel
        )
            .map { list -> list.map { it.toDomain() }.sortedWith(com.excavplayer.domain.model.NaturalVideoComparator) }
            .flowOn(dispatchers.io)
    }

    override fun searchVideos(query: String): Flow<List<Video>> {
        return videoDao.searchVideos(query.trim())
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun renameVideo(videoId: String, newName: String): ExcavResult<Unit> = withContext(dispatchers.io) {
        try {
            logger.i(TAG, "Renaming video $videoId to: $newName")
            videoDao.renameVideo(videoId, newName)
            ExcavResult.Success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to rename video $videoId", e)
            ExcavResult.Error(e)
        }
    }

    override suspend fun deleteVideo(videoId: String): ExcavResult<Unit> = withContext(dispatchers.io) {
        try {
            val video = videoDao.getVideoById(videoId)
            if (video == null) {
                return@withContext ExcavResult.Error(IllegalArgumentException("Video with ID $videoId not found"))
            }

            val uri = Uri.parse(video.video.uri)
            var storageDeleted = false
            val deletedPaths = mutableListOf<String>()

            // 1. SAF document or tree URI deletion
            if (DocumentsContract.isDocumentUri(context, uri) ||
                video.video.sourceType == MediaSourceType.LOCAL_DOCUMENT.name ||
                video.video.sourceType == MediaSourceType.LOCAL_TREE.name
            ) {
                runCatching {
                    storageDeleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                    if (storageDeleted) {
                        logger.i(TAG, "Deleted SAF document via DocumentsContract: $uri")
                    }
                }.onFailure { e ->
                    logger.w(TAG, "DocumentsContract deleteDocument failed for $uri: ${e.message}")
                }
            }

            // 2. Gather all direct file path candidates
            val directFiles = mutableSetOf<File>()
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                directFiles.add(File(uri.path!!))
            }
            if (video.video.folderPath.isNotBlank() && video.video.displayName.isNotBlank()) {
                directFiles.add(File(video.video.folderPath, video.video.displayName))
            }
            if (video.video.relativePath.isNotBlank() && video.video.displayName.isNotBlank()) {
                val cleanRel = video.video.relativePath.trim().trim('/')
                directFiles.add(File("/storage/emulated/0/$cleanRel", video.video.displayName))
            }

            // Resolve DATA column from MediaStore if content URI
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                runCatching {
                    @Suppress("DEPRECATION")
                    val projection = arrayOf(MediaStore.Video.Media.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                            if (dataIdx != -1 && !cursor.isNull(dataIdx)) {
                                val filePath = cursor.getString(dataIdx)
                                if (!filePath.isNullOrBlank()) {
                                    directFiles.add(File(filePath))
                                }
                            }
                        }
                    }
                }.onFailure { e ->
                    logger.w(TAG, "Query DATA column failed for $uri: ${e.message}")
                }
            }

            // Attempt direct File deletion on all candidate paths
            for (targetFile in directFiles) {
                if (targetFile.exists()) {
                    runCatching {
                        if (targetFile.delete()) {
                            storageDeleted = true
                            deletedPaths.add(targetFile.absolutePath)
                            logger.i(TAG, "Deleted physical file from disk: ${targetFile.absolutePath}")
                        }
                    }.onFailure { e ->
                        logger.w(TAG, "Direct File.delete() failed for ${targetFile.absolutePath}: ${e.message}")
                    }
                }
            }

            // 3. Delete via ContentResolver for MediaStore content URIs
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                runCatching {
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) {
                        storageDeleted = true
                        logger.i(TAG, "Deleted MediaStore content URI: $uri (rows=$rows)")
                    }
                }.onFailure { e ->
                    logger.w(TAG, "ContentResolver delete failed for URI $uri: ${e.message}")
                }

                if (videoId.startsWith("ms_")) {
                    val msId = videoId.removePrefix("ms_")
                    runCatching {
                        val rows = context.contentResolver.delete(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Video.Media._ID} = ?",
                            arrayOf(msId)
                        )
                        if (rows > 0) {
                            storageDeleted = true
                            logger.i(TAG, "Deleted MediaStore row for ID $msId (rows=$rows)")
                        }
                    }.onFailure { e ->
                        logger.w(TAG, "ContentResolver delete by ID failed for $msId: ${e.message}")
                    }
                }
            }

            // 4. Trigger MediaScanner to remove deleted paths from MediaStore index immediately
            if (deletedPaths.isNotEmpty()) {
                runCatching {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        deletedPaths.toTypedArray(),
                        null,
                        null
                    )
                }
            }

            // 5. Evict from thumbnail caches
            runCatching {
                thumbnailLoader.evictThumbnail(video.video.uri)
            }

            // 6. Check if file still exists on disk
            val stillExists = directFiles.any { it.exists() }
            if (stillExists && !storageDeleted) {
                logger.w(TAG, "File could not be deleted from physical storage: ${video.video.displayName}")
                return@withContext ExcavResult.Error(
                    java.io.IOException("Could not delete video file from device storage. Please check storage permissions.")
                )
            }

            // 7. Remove from local Room database
            videoDao.deleteVideo(videoId)
            logger.i(TAG, "Deleted video from repository & storage: $videoId (storageDeleted=$storageDeleted)")
            ExcavResult.Success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to delete video $videoId", e)
            ExcavResult.Error(e)
        }
    }

    override suspend fun updateAvailability(videoId: String, availability: MediaAvailability) = withContext(dispatchers.io) {
        videoDao.updateAvailability(videoId, availability.name)
    }

    override suspend fun syncWithMediaStore(): ExcavResult<Int> {
        return mediaSyncManager.syncMediaStore()
    }
}
