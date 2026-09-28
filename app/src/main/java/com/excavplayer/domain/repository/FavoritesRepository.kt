package com.excavplayer.domain.repository

import com.excavplayer.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun observeFavorites(): Flow<List<Video>>
    fun isFavorite(videoId: String): Flow<Boolean>
    suspend fun addFavorite(videoId: String)
    suspend fun removeFavorite(videoId: String)
    suspend fun toggleFavorite(videoId: String)
}
