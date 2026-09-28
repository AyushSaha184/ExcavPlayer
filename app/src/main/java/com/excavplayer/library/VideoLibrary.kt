package com.excavplayer.library

import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.FavoritesRepository
import com.excavplayer.domain.repository.PlaybackRepository
import com.excavplayer.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoLibrary @Inject constructor(
    private val videoRepository: VideoRepository,
    private val playbackRepository: PlaybackRepository,
    private val favoritesRepository: FavoritesRepository
) {
    fun observeVideos(): Flow<List<Video>> = videoRepository.observeVideos()

    fun observeFolders(): Flow<List<Folder>> = videoRepository.observeFolders()

    fun observeVideosInFolder(folderPath: String): Flow<List<Video>> = videoRepository.observeVideosInFolder(folderPath)

    fun observeFavorites(): Flow<List<Video>> = favoritesRepository.observeFavorites()

    fun observeContinueWatching(threshold: Float = 0.95f): Flow<List<Video>> =
        playbackRepository.observeContinueWatching(threshold)

    fun searchVideos(query: String): Flow<List<Video>> = videoRepository.searchVideos(query)

    suspend fun refresh(): ExcavResult<Int> = videoRepository.syncWithMediaStore()

    suspend fun deleteVideo(videoId: String): ExcavResult<Unit> = videoRepository.deleteVideo(videoId)

    suspend fun toggleFavorite(videoId: String) = favoritesRepository.toggleFavorite(videoId)
}
