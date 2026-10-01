package com.excavplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.mapper.toDomain
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.VideoRepository
import com.excavplayer.media.discovery.MediaSyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val mediaSyncManager: MediaSyncManager,
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
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                // If content uri, try to remove from MediaStore via ContentResolver
                runCatching {
                    context.contentResolver.delete(uri, null, null)
                }.onFailure { e ->
                    logger.w(TAG, "ContentResolver could not delete URI directly (may require user consent or scoped storage delete intent): ${e.message}")
                }
            }

            // Remove or mark deleted in local database
            videoDao.deleteVideo(videoId)
            logger.i(TAG, "Deleted video from repository: $videoId")
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
