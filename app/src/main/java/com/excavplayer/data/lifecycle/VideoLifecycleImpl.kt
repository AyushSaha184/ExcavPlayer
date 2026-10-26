package com.excavplayer.data.lifecycle

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.media.thumbnail.ThumbnailLoader
import com.excavplayer.domain.lifecycle.DeleteOutcome
import com.excavplayer.domain.lifecycle.VideoLifecycle
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.VideoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of the video lifecycle. The delete classifier (which videos
 * need the system approval dialog and which can go immediately) lives here —
 * the viewmodel and the repository both consult this one implementation.
 */
@Singleton
class VideoLifecycleImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val videoDao: VideoDao,
    private val thumbnailLoader: ThumbnailLoader,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : VideoLifecycle {

    companion object {
        private const val TAG = "VideoLifecycle"
    }

    /**
     * The unmockable system boundary: the MediaStore approval request.
     * Tests replace this to inject a fake approval handle (the plan's
     * "small data holder" for the wrapped sender).
     */
    @androidx.annotation.VisibleForTesting
    internal var approvalRequestFactory: (uris: List<Uri>) -> android.app.PendingIntent = { uris ->
        MediaStore.createDeleteRequest(context.contentResolver, uris)
    }

    override suspend fun refresh(force: Boolean): ExcavResult<Int> = withContext(dispatchers.io) {
        purgeCorruptedEntries()
        videoRepository.syncWithMediaStore(force)
    }

    override suspend fun purgeCorruptedEntries() {
        videoRepository.purgeCorruptedEntries()
    }

    override suspend fun deleteVideos(videos: List<Video>): DeleteOutcome = withContext(dispatchers.io) {
        if (videos.isEmpty()) return@withContext DeleteOutcome.Deleted(emptyList())

        val distinct = videos.distinctBy { it.id }
        val mediaStoreOnR = distinct.filter { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isMediaStoreVideo(it) }
        val direct = distinct.filterNot { it in mediaStoreOnR }

        val deletedIds = mutableListOf<String>()
        val failedIds = mutableListOf<String>()
        for (video in direct) {
            when (videoRepository.deleteVideo(video.id)) {
                is ExcavResult.Success -> deletedIds += video.id
                else -> failedIds += video.id
            }
        }

        if (mediaStoreOnR.isNotEmpty()) {
            try {
                DeleteOutcome.ApprovalRequired(
                    senderHandle = approvalRequestFactory(mediaStoreOnR.map { Uri.parse(it.uri) }).intentSender,
                    videoIds = mediaStoreOnR.map { it.id }
                )
            } catch (e: Exception) {
                logger.e(TAG, "Failed to create MediaStore delete request", e)
                DeleteOutcome.Partial(
                    deletedIds = deletedIds,
                    failedIds = failedIds + mediaStoreOnR.map { it.id }
                )
            }
        } else {
            when {
                failedIds.isEmpty() -> DeleteOutcome.Deleted(deletedIds)
                else -> DeleteOutcome.Partial(deletedIds, failedIds)
            }
        }
    }

    /**
     * Post-approval cleanup for MediaStore deletions: the system dialog has
     * already deleted the files; this evicts thumbnails and removes the rows.
     */
    override suspend fun finalizeApprovedDeletion(videoIds: List<String>): ExcavResult<Unit> =
        withContext(dispatchers.io) {
            try {
                for (id in videoIds) {
                    videoDao.getVideoById(id)?.let { row ->
                        thumbnailLoader.evictThumbnail(row.video.uri)
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

    /**
     * The single classifier: MediaStore-backed means a content-scheme URI that
     * is not an SAF document or tree — exactly the videos Android R+ requires
     * the system approval dialog for.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun isMediaStoreVideo(video: Video): Boolean {
        val uri = Uri.parse(video.uri)
        return uri.scheme == "content" &&
            video.sourceType != MediaSourceType.LOCAL_DOCUMENT &&
            video.sourceType != MediaSourceType.LOCAL_TREE
    }
}
