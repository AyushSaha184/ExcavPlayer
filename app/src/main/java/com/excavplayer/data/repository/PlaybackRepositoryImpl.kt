package com.excavplayer.data.repository

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.dao.PlaybackDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.mapper.toDomain
import com.excavplayer.data.database.mapper.toEntity
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl @Inject constructor(
    private val playbackDao: PlaybackDao,
    private val videoDao: VideoDao,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : PlaybackRepository {

    companion object {
        private const val TAG = "PlaybackRepository"
    }

    override fun observePlaybackState(videoId: String): Flow<PlaybackState?> {
        return playbackDao.observePlaybackState(videoId)
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override suspend fun getPlaybackState(videoId: String): PlaybackState? = withContext(dispatchers.io) {
        playbackDao.getPlaybackState(videoId)?.toDomain()
    }

    override suspend fun savePlaybackState(state: PlaybackState) = withContext(dispatchers.io) {
        if (state.videoId.isNullOrBlank()) {
            logger.w(TAG, "Cannot save playback state with null or blank videoId")
            return@withContext
        }
        playbackDao.upsertPlaybackState(state.toEntity())
    }

    override fun observeContinueWatching(threshold: Float): Flow<List<Video>> {
        return videoDao.observeContinueWatching(threshold)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun clearPlaybackState(videoId: String) = withContext(dispatchers.io) {
        playbackDao.deletePlaybackState(videoId)
    }

    override suspend fun clearAllPlaybackStates() = withContext(dispatchers.io) {
        playbackDao.clearAll()
    }
}
