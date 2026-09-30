package com.excavplayer.library

import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import com.excavplayer.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistManager @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "PlaylistManager"
    }

    fun observePlaylists(): Flow<List<Playlist>> = playlistRepository.observePlaylists()

    fun observePlaylist(playlistId: Long): Flow<Playlist?> = playlistRepository.observePlaylist(playlistId)

    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = playlistRepository.observePlaylistItems(playlistId)

    suspend fun createPlaylist(title: String): Long {
        logger.i(TAG, "Creating new playlist with title: $title")
        return playlistRepository.createPlaylist(title)
    }

    suspend fun renamePlaylist(playlistId: Long, newTitle: String) {
        logger.i(TAG, "Renaming playlist $playlistId to: $newTitle")
        playlistRepository.renamePlaylist(playlistId, newTitle)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        logger.i(TAG, "Deleting playlist with id: $playlistId")
        playlistRepository.deletePlaylist(playlistId)
    }

    suspend fun addVideo(playlistId: Long, videoId: String): Boolean {
        logger.i(TAG, "Adding video $videoId to playlist $playlistId")
        return playlistRepository.addVideoToPlaylist(playlistId, videoId)
    }

    suspend fun removeVideo(playlistId: Long, videoId: String) {
        logger.i(TAG, "Removing video $videoId from playlist $playlistId")
        playlistRepository.removeVideoFromPlaylist(playlistId, videoId)
    }

    suspend fun reorder(playlistId: Long, fromIndex: Int, toIndex: Int) {
        logger.i(TAG, "Reordering playlist $playlistId item from $fromIndex to $toIndex")
        playlistRepository.reorderPlaylist(playlistId, fromIndex, toIndex)
    }
}
