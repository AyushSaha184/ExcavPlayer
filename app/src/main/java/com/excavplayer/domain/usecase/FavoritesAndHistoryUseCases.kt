package com.excavplayer.domain.usecase

import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageFavoritesUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    fun observeFavorites(): Flow<List<Video>> = favoritesRepository.observeFavorites()

    fun isFavorite(videoId: String): Flow<Boolean> = favoritesRepository.isFavorite(videoId)

    suspend fun toggleFavorite(videoId: String) = favoritesRepository.toggleFavorite(videoId)

    suspend fun addFavorite(videoId: String) = favoritesRepository.addFavorite(videoId)

    suspend fun removeFavorite(videoId: String) = favoritesRepository.removeFavorite(videoId)
}

