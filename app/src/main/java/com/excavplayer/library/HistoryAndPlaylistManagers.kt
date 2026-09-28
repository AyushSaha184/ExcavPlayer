package com.excavplayer.library

import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import com.excavplayer.domain.model.WatchHistoryEntry
import com.excavplayer.domain.repository.HistoryRepository
import com.excavplayer.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryManager @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    fun observeHistory(): Flow<List<WatchHistoryEntry>> = historyRepository.observeHistory()

    suspend fun clearHistory() = historyRepository.clearHistory()

    suspend fun removeFromHistory(videoId: String) = historyRepository.removeFromHistory(videoId)
}

@Singleton
class PlaylistManager @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    fun observePlaylists(): Flow<List<Playlist>> = playlistRepository.observePlaylists()

    fun observePlaylist(playlistId: Long): Flow<Playlist?> = playlistRepository.observePlaylist(playlistId)

    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = playlistRepository.observePlaylistItems(playlistId)

    suspend fun createPlaylist(title: String): Long = playlistRepository.createPlaylist(title)

    suspend fun renamePlaylist(playlistId: Long, newTitle: String) = playlistRepository.renamePlaylist(playlistId, newTitle)

    suspend fun deletePlaylist(playlistId: Long) = playlistRepository.deletePlaylist(playlistId)

    suspend fun addVideo(playlistId: Long, videoId: String): Boolean = playlistRepository.addVideoToPlaylist(playlistId, videoId)

    suspend fun removeVideo(playlistId: Long, videoId: String) = playlistRepository.removeVideoFromPlaylist(playlistId, videoId)

    suspend fun reorder(playlistId: Long, fromIndex: Int, toIndex: Int) = playlistRepository.reorderPlaylist(playlistId, fromIndex, toIndex)
}
