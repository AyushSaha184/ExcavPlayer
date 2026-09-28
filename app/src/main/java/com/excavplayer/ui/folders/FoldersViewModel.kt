package com.excavplayer.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Video
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
class FoldersViewModel @Inject constructor(
    private val videoLibrary: VideoLibrary,
    private val playerManager: PlayerManager
) : ViewModel() {

    val folders: StateFlow<List<Folder>> = videoLibrary.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map each folder path to its first video URI for thumbnail preview
    val folderThumbnails: StateFlow<Map<String, String>> = videoLibrary.observeVideos()
        .map { videos ->
            videos.groupBy { it.folderPath }
                .mapValues { (_, vids) -> vids.firstOrNull()?.uri ?: "" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun observeVideosInFolder(folderPath: String): Flow<List<Video>> {
        return videoLibrary.observeVideosInFolder(folderPath)
    }

    fun playVideo(video: Video, folderVideos: List<Video>) {
        val startIdx = folderVideos.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        playerManager.queue.setQueue(folderVideos, startIdx)
        viewModelScope.launch {
            playerManager.play(video)
        }
    }

    fun playAll(videos: List<Video>) {
        if (videos.isNotEmpty()) {
            playerManager.queue.setQueue(videos, 0)
            viewModelScope.launch {
                playerManager.play(videos.first())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            videoLibrary.refresh()
        }
    }
}
