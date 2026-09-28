package com.excavplayer.domain.usecase

import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import com.excavplayer.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManagePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    fun observePlaylists(): Flow<List<Playlist>> = playlistRepository.observePlaylists()

    fun observePlaylist(playlistId: Long): Flow<Playlist?> = playlistRepository.observePlaylist(playlistId)

    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = playlistRepository.observePlaylistItems(playlistId)

    suspend fun createPlaylist(name: String): Long = playlistRepository.createPlaylist(name)

    suspend fun renamePlaylist(playlistId: Long, newName: String) = playlistRepository.renamePlaylist(playlistId, newName)

    suspend fun deletePlaylist(playlistId: Long) = playlistRepository.deletePlaylist(playlistId)

    suspend fun addVideo(playlistId: Long, videoId: String): Boolean = playlistRepository.addVideoToPlaylist(playlistId, videoId)

    suspend fun removeVideo(playlistId: Long, videoId: String) = playlistRepository.removeVideoFromPlaylist(playlistId, videoId)

    suspend fun reorder(playlistId: Long, fromIndex: Int, toIndex: Int) = playlistRepository.reorderPlaylist(playlistId, fromIndex, toIndex)
}
