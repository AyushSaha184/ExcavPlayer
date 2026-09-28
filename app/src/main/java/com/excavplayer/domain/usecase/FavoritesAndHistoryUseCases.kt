package com.excavplayer.domain.usecase

import com.excavplayer.domain.model.Video
import com.excavplayer.domain.model.WatchHistoryEntry
import com.excavplayer.domain.repository.FavoritesRepository
import com.excavplayer.domain.repository.HistoryRepository
import com.excavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

class ManageHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository
) {
    fun observeHistory(): Flow<List<WatchHistoryEntry>> = historyRepository.observeHistory()

    suspend fun recordHistory(entry: WatchHistoryEntry) {
        val settings = settingsRepository.userSettings.first()
        if (settings.historyEnabled) {
            historyRepository.recordHistory(entry)
            historyRepository.pruneOldHistory(500)
        }
    }

    suspend fun removeFromHistory(videoId: String) = historyRepository.removeFromHistory(videoId)

    suspend fun clearHistory() = historyRepository.clearHistory()
}
