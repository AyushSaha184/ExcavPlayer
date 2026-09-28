package com.excavplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import com.excavplayer.domain.model.Video
import com.excavplayer.library.PlaylistManager
import com.excavplayer.library.VideoLibrary
import com.excavplayer.player.core.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistManager: PlaylistManager,
    private val videoLibrary: VideoLibrary,
    private val playerManager: PlayerManager
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistManager.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesCount: StateFlow<Int> = videoLibrary.observeFavorites()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoritesThumbnail: StateFlow<String?> = videoLibrary.observeFavorites()
        .map { it.firstOrNull()?.uri }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun observePlaylist(id: Long): Flow<Playlist?> = playlistManager.observePlaylist(id)

    fun observePlaylistItems(id: Long): Flow<List<PlaylistItem>> = playlistManager.observePlaylistItems(id)

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                playlistManager.createPlaylist(title.trim())
            }
        }
    }

    fun renamePlaylist(id: Long, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isNotBlank()) {
                playlistManager.renamePlaylist(id, newTitle.trim())
            }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistManager.deletePlaylist(id)
        }
    }

    fun playPlaylist(items: List<PlaylistItem>, startIndex: Int = 0) {
        val videos = items.mapNotNull { it.video }
        if (videos.isNotEmpty()) {
            playerManager.queue.setQueue(videos, startIndex)
            viewModelScope.launch {
                playerManager.play(videos[startIndex.coerceIn(0, videos.size - 1)])
            }
        }
    }

    fun removePlaylistItem(playlistId: Long, videoId: String) {
        viewModelScope.launch {
            playlistManager.removeVideo(playlistId, videoId)
        }
    }

    fun reorderItem(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            playlistManager.reorder(playlistId, fromIndex, toIndex)
        }
    }
}
