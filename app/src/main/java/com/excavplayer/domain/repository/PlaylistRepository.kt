package com.excavplayer.domain.repository

import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylist(playlistId: Long): Flow<Playlist?>
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>>
    suspend fun createPlaylist(title: String): Long
    suspend fun renamePlaylist(playlistId: Long, newTitle: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addVideoToPlaylist(playlistId: Long, videoId: String): Boolean
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String)
    suspend fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int)
}
