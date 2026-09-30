package com.excavplayer.data.repository

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.data.database.dao.FavoriteDao
import com.excavplayer.data.database.entity.FavoriteEntity
import com.excavplayer.data.database.mapper.toDomain
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val dispatchers: DispatcherProvider
) : FavoritesRepository {

    override fun observeFavorites(): Flow<List<Video>> {
        return favoriteDao.observeFavoritesWithMetadata()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun isFavorite(videoId: String): Flow<Boolean> {
        return favoriteDao.isFavorite(videoId)
            .flowOn(dispatchers.io)
    }

    override suspend fun addFavorite(videoId: String) = withContext(dispatchers.io) {
        favoriteDao.insertFavorite(FavoriteEntity(videoId = videoId))
    }

    override suspend fun removeFavorite(videoId: String) = withContext(dispatchers.io) {
        favoriteDao.deleteFavorite(videoId)
    }

    override suspend fun toggleFavorite(videoId: String) = withContext(dispatchers.io) {
        val currentlyFav = favoriteDao.isFavorite(videoId).first()
        if (currentlyFav) {
            favoriteDao.deleteFavorite(videoId)
        } else {
            favoriteDao.insertFavorite(FavoriteEntity(videoId = videoId))
        }
    }
}

