package com.excavplayer.domain.repository

import androidx.paging.PagingData
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun observeVideos(): Flow<List<Video>>
    fun observePagedVideos(): Flow<PagingData<Video>>
    fun observeVideoById(id: String): Flow<Video?>
    suspend fun getVideoById(id: String): Video?
    fun observeFolders(): Flow<List<Folder>>
    fun observeVideosInFolder(folderPath: String): Flow<List<Video>>
    fun searchVideos(query: String): Flow<List<Video>>
    suspend fun renameVideo(videoId: String, newName: String): ExcavResult<Unit>
    suspend fun deleteVideo(videoId: String): ExcavResult<Unit>
    suspend fun updateAvailability(videoId: String, availability: MediaAvailability)
    suspend fun syncWithMediaStore(): ExcavResult<Int>
}
