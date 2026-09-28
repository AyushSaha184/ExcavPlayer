package com.excavplayer.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.domain.model.Video
import com.excavplayer.library.VideoLibrary
import com.excavplayer.player.core.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val videoLibrary: VideoLibrary,
    private val playerManager: PlayerManager
) : ViewModel() {

    val favorites: StateFlow<List<Video>> = videoLibrary.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playFavorite(video: Video) {
        val list = favorites.value
        val startIdx = list.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        playerManager.queue.setQueue(list, startIdx)
        viewModelScope.launch {
            playerManager.play(video)
        }
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch {
            videoLibrary.toggleFavorite(videoId)
        }
    }
}
