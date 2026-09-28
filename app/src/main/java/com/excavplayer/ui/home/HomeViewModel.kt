package com.excavplayer.ui.home

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
class HomeViewModel @Inject constructor(
    private val videoLibrary: VideoLibrary,
    private val playerManager: PlayerManager
) : ViewModel() {

    val continueWatching: StateFlow<List<Video>> = videoLibrary.observeContinueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVideos: StateFlow<List<Video>> = videoLibrary.observeVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playVideo(video: Video) {
        viewModelScope.launch {
            // Set queue to all videos with clicked video first
            val currentList = if (continueWatching.value.any { it.id == video.id }) {
                continueWatching.value + allVideos.value.filter { v -> continueWatching.value.none { it.id == v.id } }
            } else {
                allVideos.value
            }
            val startIdx = currentList.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            playerManager.queue.setQueue(currentList, startIdx)
            playerManager.play(video)
        }
    }

    fun playNext(video: Video) {
        playerManager.queue.addVideo(video)
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch {
            videoLibrary.toggleFavorite(videoId)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            videoLibrary.refresh()
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            videoLibrary.deleteVideo(videoId)
        }
    }
}
