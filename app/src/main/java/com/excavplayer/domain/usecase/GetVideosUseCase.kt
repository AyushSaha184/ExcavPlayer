package com.excavplayer.domain.usecase

import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    operator fun invoke(): Flow<List<Video>> = videoRepository.observeVideos()

    fun getById(id: String): Flow<Video?> = videoRepository.observeVideoById(id)

    suspend fun getDirect(id: String): Video? = videoRepository.getVideoById(id)

    fun inFolder(folderPath: String): Flow<List<Video>> = videoRepository.observeVideosInFolder(folderPath)

    fun search(query: String): Flow<List<Video>> = videoRepository.searchVideos(query)

    fun getFolders(): Flow<List<Folder>> = videoRepository.observeFolders()

    suspend fun sync(): ExcavResult<Int> = videoRepository.syncWithMediaStore()

    suspend fun delete(videoId: String): ExcavResult<Unit> = videoRepository.deleteVideo(videoId)
}
