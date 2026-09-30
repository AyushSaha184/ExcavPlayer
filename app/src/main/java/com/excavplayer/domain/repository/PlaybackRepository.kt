package com.excavplayer.domain.repository

import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface PlaybackRepository {
    fun observePlaybackState(videoId: String): Flow<PlaybackState?>
    suspend fun getPlaybackState(videoId: String): PlaybackState?
    suspend fun savePlaybackState(state: PlaybackState)
    fun observeContinueWatching(threshold: Float = 0.95f): Flow<List<Video>>
    suspend fun dismissFromContinueWatching(videoId: String)
    suspend fun clearPlaybackState(videoId: String)
    suspend fun clearAllPlaybackStates()
}
