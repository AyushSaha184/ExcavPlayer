package com.excavplayer.domain.repository

import com.excavplayer.domain.model.WatchHistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(): Flow<List<WatchHistoryEntry>>
    suspend fun recordHistory(entry: WatchHistoryEntry)
    suspend fun removeFromHistory(videoId: String)
    suspend fun clearHistory()
    suspend fun pruneOldHistory(keepCount: Int = 500)
}
