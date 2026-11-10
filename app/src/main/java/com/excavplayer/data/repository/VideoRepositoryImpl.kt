package com.excavplayer.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
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
import java.io.IOException
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
            val videoTuple = videoDao.getVideoById(videoId)
                ?: return@withContext ExcavResult.Error(IllegalArgumentException("Video with ID $videoId not found"))
            val video = videoTuple.video

            val oldName = video.displayName
            val oldExt = File(oldName).extension
            val targetName = if (oldExt.isNotBlank() && !newName.endsWith(".$oldExt", ignoreCase = true)) {
                val newExt = File(newName).extension
                if (newExt.isBlank()) {
                    "$newName.$oldExt"
                } else {
                    newName
                }
            } else {
                newName
            }

            if (targetName == oldName) {
                return@withContext ExcavResult.Success(Unit)
            }

            logger.i(TAG, "Renaming video $videoId from '$oldName' to '$targetName'")

            val uri = Uri.parse(video.uri)
            var newUriString = video.uri
            var storageRenamed = false

            // 1. SAF document or tree URI renaming
            if (DocumentsContract.isDocumentUri(context, uri) ||
                video.sourceType == MediaSourceType.LOCAL_DOCUMENT.name ||
                video.sourceType == MediaSourceType.LOCAL_TREE.name
            ) {
                val renamedUri = runCatching {
                    DocumentsContract.renameDocument(context.contentResolver, uri, targetName)
                }.getOrNull()
                if (renamedUri != null) {
                    newUriString = renamedUri.toString()
                    storageRenamed = true
                }
            }

            // 2. Direct File / Filesystem renaming
            val physicalFile = getPhysicalFile(uri, video.folderPath, oldName)
            var newPhysicalFile: File? = null
            if (physicalFile != null && physicalFile.exists()) {
                val dest = File(physicalFile.parentFile ?: File(video.folderPath), targetName)
                if (dest.exists() && dest.canonicalPath != physicalFile.canonicalPath) {
                    return@withContext ExcavResult.Error(IOException("A file named '$targetName' already exists"))
                }
                if (physicalFile.renameTo(dest)) {
                    storageRenamed = true
                    newPhysicalFile = dest
                    if (uri.scheme == "file") {
                        newUriString = Uri.fromFile(dest).toString()
                    }
                    logger.i(TAG, "Renamed physical file: ${physicalFile.absolutePath} -> ${dest.absolutePath}")
                } else {
                    logger.w(TAG, "File.renameTo returned false for ${physicalFile.absolutePath}")
                }
            }

            // 3. MediaStore ContentResolver update (if content:// URI)
            if (uri.scheme == ContentResolver.SCHEME_CONTENT && !DocumentsContract.isDocumentUri(context, uri)) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, targetName)
                    if (newPhysicalFile != null) {
                        @Suppress("DEPRECATION")
                        put(MediaStore.Video.Media.DATA, newPhysicalFile.absolutePath)
                    }
                }
                val updatedRows = runCatching {
                    context.contentResolver.update(uri, values, null, null)
                }.getOrDefault(0)

                if (updatedRows > 0) {
                    storageRenamed = true
                    logger.i(TAG, "Updated MediaStore record for $uri ($updatedRows row)")
                }
            }

            // 4. Trigger MediaScanner to index renamed file
            if (physicalFile != null && newPhysicalFile != null && storageRenamed) {
                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(physicalFile.absolutePath, newPhysicalFile.absolutePath),
                        null,
                        null
                    )
                }
            }

            // 5. Update database and thumbnail cache
            if (storageRenamed || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                thumbnailLoader.evictThumbnail(video.uri)
                if (newUriString != video.uri) {
                    thumbnailLoader.evictThumbnail(newUriString)
                    videoDao.renameVideoWithUri(videoId, targetName, newUriString)
                } else {
                    videoDao.renameVideo(videoId, targetName)
                }
                logger.i(TAG, "Successfully renamed video $videoId to $targetName in database")
                ExcavResult.Success(Unit)
            } else {
                logger.w(TAG, "Could not rename video file on storage (permission restricted)")
                ExcavResult.Error(IOException("Could not rename file on storage. Please check storage permissions."))
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to rename video $videoId", e)
            ExcavResult.Error(e)
        }
    }

    private fun getPhysicalFile(uri: Uri, folderPath: String, displayName: String): File? {
        if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            val f = File(uri.path!!)
            if (f.exists()) return f
        }
        if (folderPath.isNotBlank() && displayName.isNotBlank()) {
            val f = File(folderPath, displayName)
            if (f.exists()) return f
        }
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Video.Media.DATA),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (idx != -1) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrBlank()) {
                                val f = File(path)
                                if (f.exists()) return f
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    override suspend fun deleteVideo(videoId: String): ExcavResult<Unit> = withContext(dispatchers.io) {
        try {
            val video = videoDao.getVideoById(videoId)
                ?: return@withContext ExcavResult.Error(IllegalArgumentException("Video with ID $videoId not found"))

            val uri = Uri.parse(video.video.uri)

            // 1. SAF document or tree URI deletion
            if (DocumentsContract.isDocumentUri(context, uri) ||
                video.video.sourceType == MediaSourceType.LOCAL_DOCUMENT.name ||
                video.video.sourceType == MediaSourceType.LOCAL_TREE.name
            ) {
                runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                }
            } else if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                // MediaStore deletion (Android 10 / direct when permitted)
                runCatching {
                    context.contentResolver.delete(uri, null, null)
                }
            } else if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                val f = File(uri.path!!)
                if (f.exists()) {
                    f.delete()
                }
            }

            thumbnailLoader.evictThumbnail(video.video.uri)
            videoDao.deleteVideo(videoId)
            logger.i(TAG, "Deleted video: $videoId")
            ExcavResult.Success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to delete video $videoId", e)
            ExcavResult.Error(e)
        }
    }

    override suspend fun deleteVideosAfterConfirmation(videoIds: List<String>): ExcavResult<Unit> = withContext(dispatchers.io) {
        try {
            for (id in videoIds) {
                val video = videoDao.getVideoById(id)
                if (video != null) {
                    thumbnailLoader.evictThumbnail(video.video.uri)
                }
            }
            videoDao.deleteVideos(videoIds)
            logger.i(TAG, "Cleaned up ${videoIds.size} videos from database after confirmation")
            ExcavResult.Success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to clean up videos after confirmation", e)
            ExcavResult.Error(e)
        }
    }

    override suspend fun updateAvailability(videoId: String, availability: MediaAvailability) = withContext(dispatchers.io) {
        videoDao.updateAvailability(videoId, availability.name)
    }

    override suspend fun syncWithMediaStore(force: Boolean): ExcavResult<Int> {
        return mediaSyncManager.syncMediaStore(force)
    }

    override suspend fun purgeCorruptedEntries() = withContext(dispatchers.io) {
        videoDao.purgePrivateStorageVideos()
    }
}
