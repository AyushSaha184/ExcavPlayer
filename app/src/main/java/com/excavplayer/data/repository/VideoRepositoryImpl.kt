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

    override suspend fun syncWithMediaStore(): ExcavResult<Int> {
        return mediaSyncManager.syncMediaStore()
    }
}
