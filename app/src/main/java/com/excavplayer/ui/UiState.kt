package com.excavplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.model.NaturalVideoComparator
import com.excavplayer.library.PlaylistManager
import com.excavplayer.library.VideoLibrary
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.queue.PlaybackQueue
import com.excavplayer.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.excavplayer.update.AppUpdateManager
import com.excavplayer.update.GitHubAsset
import com.excavplayer.update.UpdateState
import java.io.File
import javax.inject.Inject

data class UserMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false,
    val actionLabel: String? = null
)

data class LibraryUiState(
    val videos: List<Video> = emptyList(),
    val continueWatching: List<Video> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val favorites: List<Video> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Video> = emptyList(),
    val selectedFolder: Folder? = null,
    val folderVideos: List<Video> = emptyList(),
    val isSyncing: Boolean = false,
    val loading: Boolean = true
)

@HiltViewModel
class ExcavViewModel @Inject constructor(
    private val library: VideoLibrary,
    private val playlistManager: PlaylistManager,
    private val settingsManager: SettingsManager,
    val player: PlayerManager,
    val queue: PlaybackQueue,
    val updateManager: AppUpdateManager,
    private val logger: AppLogger
) : ViewModel() {

    companion object {
        private const val TAG = "ExcavViewModel"
    }

    private val query = MutableStateFlow("")
    private val selectedFolder = MutableStateFlow<Folder?>(null)
    private val _userMessage = MutableStateFlow<UserMessage?>(null)
    val userMessage: StateFlow<UserMessage?> = _userMessage.asStateFlow()

    val userSettings: StateFlow<UserSettings> = settingsManager.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val updateState: StateFlow<UpdateState> = updateManager.updateState

    private val searchResults = query.flatMapLatest { value ->
        if (value.isBlank()) flowOf(emptyList()) else library.searchVideos(value)
    }

    private val folderVideos = selectedFolder.flatMapLatest { folder ->
        folder?.let { library.observeVideosInFolder(it.path) } ?: flowOf(emptyList())
    }

    @Suppress("UNCHECKED_CAST")
    val libraryState: StateFlow<LibraryUiState> = combine(
        library.observeVideos(),
        library.observeContinueWatching(),
        library.observeFolders(),
        library.observeFavorites(),
        playlistManager.observePlaylists(),
        query,
        searchResults,
        selectedFolder,
        folderVideos
    ) { values ->
        LibraryUiState(
            videos = values[0] as List<Video>,
            continueWatching = values[1] as List<Video>,
            folders = values[2] as List<Folder>,
            favorites = values[3] as List<Video>,
            playlists = values[4] as List<Playlist>,
            searchQuery = values[5] as String,
            searchResults = values[6] as List<Video>,
            selectedFolder = values[7] as Folder?,
            folderVideos = values[8] as List<Video>,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val playerState = player.state
    val queueState = queue.state

    fun setSearchQuery(value: String) {
        logger.d(TAG, "Search query updated: $value")
        query.value = value
    }

    fun openFolder(folder: Folder) {
        logger.i(TAG, "Opening folder: ${folder.name} (${folder.path})")
        selectedFolder.value = folder
        setLastOpenedFolder(folder.path)
    }

    fun closeFolder() {
        logger.d(TAG, "Closing active folder view")
        selectedFolder.value = null
        setLastOpenedFolder(null)
    }

    fun setLastOpenedFolder(path: String?) {
        viewModelScope.launch {
            settingsManager.setLastOpenedFolder(path)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            logger.i(TAG, "User requested media library refresh")
            when (val result = library.refresh()) {
                is ExcavResult.Success -> {
                    // Refreshed silently without toast banner
                }
                is ExcavResult.Error -> {
                    logger.e(TAG, "Failed to refresh library", result.exception)
                    showMessage("Failed to sync media: ${result.message ?: result.exception.localizedMessage ?: "Unknown error"}", isError = true)
                }
                is ExcavResult.Loading -> Unit
            }
        }
    }

    private val _isPlayerOpen = MutableStateFlow(false)
    val isPlayerOpen: StateFlow<Boolean> = _isPlayerOpen.asStateFlow()

    fun setPlayerOpen(open: Boolean) {
        _isPlayerOpen.value = open
    }

    fun play(video: Video, contextList: List<Video>? = null) {
        logger.i(TAG, "play() invoked for video: ${video.displayName} [id=${video.id}]")
        _isPlayerOpen.value = true
        val currentFolderVideos = libraryState.value.folderVideos
        val allVideos = libraryState.value.videos
        val activeList = when {
            contextList != null && contextList.isNotEmpty() -> contextList
            selectedFolder.value != null && currentFolderVideos.isNotEmpty() -> currentFolderVideos
            else -> {
                val sameFolderVideos = allVideos.filter {
                    it.folderPath.isNotEmpty() && it.folderPath.equals(video.folderPath, ignoreCase = true)
                }
                if (sameFolderVideos.size > 1) {
                    sameFolderVideos.sortedWith(NaturalVideoComparator)
                } else {
                    allVideos
                }
            }
        }
        val items = if (activeList.any { it.id == video.id }) activeList else listOf(video)
        val index = items.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        queue.setQueue(items, index)
        player.dispatch(PlayerCommand.Play(video, video.resumePositionMs))
    }

    fun playQueueItem(index: Int) {
        logger.i(TAG, "playQueueItem at index: $index")
        val video = queue.moveTo(index)
        if (video != null) {
            player.dispatch(PlayerCommand.Play(video, video.resumePositionMs))
        } else {
            logger.w(TAG, "Queue item at index $index was null")
        }
    }

    fun toggleFavorite(video: Video) {
        viewModelScope.launch {
            logger.i(TAG, "toggleFavorite for: ${video.id}")
            library.toggleFavorite(video.id)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: Video) {
        viewModelScope.launch {
            logger.i(TAG, "addVideoToPlaylist: ${video.id} -> $playlistId")
            val added = playlistManager.addVideo(playlistId, video.id)
            if (added) {
                showMessage("Added to playlist")
            } else {
                showMessage("Video already in playlist or failed to add", isError = true)
            }
        }
    }

    fun renameVideo(video: Video, newName: String) {
        viewModelScope.launch {
            if (newName.isBlank()) {
                showMessage("Video title cannot be empty", isError = true)
                return@launch
            }
            logger.i(TAG, "renameVideo: ${video.id} to $newName")
            when (val res = library.renameVideo(video.id, newName.trim())) {
                is ExcavResult.Success -> showMessage("Video renamed successfully")
                is ExcavResult.Error -> showMessage("Failed to rename video: ${res.message ?: "Unknown error"}", isError = true)
                else -> Unit
            }
        }
    }

    fun deleteVideo(video: Video) {
        viewModelScope.launch {
            logger.i(TAG, "deleteVideo: ${video.id}")
            when (val res = library.deleteVideo(video.id)) {
                is ExcavResult.Success -> showMessage("Video deleted")
                is ExcavResult.Error -> showMessage("Failed to delete video: ${res.message ?: "Unknown error"}", isError = true)
                else -> Unit
            }
        }
    }

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            if (title.isBlank()) {
                showMessage("Playlist name cannot be empty", isError = true)
                return@launch
            }
            logger.i(TAG, "Creating playlist: $title")
            val id = playlistManager.createPlaylist(title)
            if (id > 0) {
                showMessage("Created playlist \"$title\"")
            } else {
                showMessage("Failed to create playlist", isError = true)
            }
        }
    }

    fun renamePlaylist(id: Long, title: String) {
        viewModelScope.launch {
            if (title.isBlank()) {
                showMessage("Playlist name cannot be empty", isError = true)
                return@launch
            }
            logger.i(TAG, "Renaming playlist $id to: $title")
            playlistManager.renamePlaylist(id, title)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            logger.i(TAG, "Deleting playlist: $id")
            playlistManager.deletePlaylist(id)
        }
    }

    fun observePlaylistItems(playlistId: Long): kotlinx.coroutines.flow.Flow<List<com.excavplayer.domain.model.PlaylistItem>> {
        return playlistManager.observePlaylistItems(playlistId)
    }

    fun removeVideoFromPlaylist(playlistId: Long, videoId: String) {
        viewModelScope.launch {
            logger.i(TAG, "Removing video $videoId from playlist $playlistId")
            playlistManager.removeVideo(playlistId, videoId)
        }
    }

    fun reorderQueue(from: Int, to: Int) {
        logger.d(TAG, "Reordering queue from $from to $to")
        queue.moveItem(from, to)
    }

    // Settings actions
    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setAutoResume: $enabled")
            settingsManager.setAutoResume(enabled)
        }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            logger.i(TAG, "setDefaultPlaybackSpeed: $speed")
            settingsManager.setPlaybackSpeed(speed)
        }
    }

    fun setDefaultRepeatMode(mode: RepeatMode) {
        viewModelScope.launch {
            logger.i(TAG, "setDefaultRepeatMode: $mode")
            settingsManager.setDefaultRepeatMode(mode)
        }
    }

    fun setResumeThreshold(threshold: Float) {
        viewModelScope.launch {
            logger.i(TAG, "setResumeThreshold: $threshold")
            settingsManager.setResumeThreshold(threshold)
        }
    }

    fun setPreferredSubtitleLanguage(lang: String?) {
        viewModelScope.launch {
            logger.i(TAG, "setPreferredSubtitleLanguage: $lang")
            settingsManager.setPreferredSubtitleLanguage(lang)
        }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setSubtitlesEnabled: $enabled")
            settingsManager.setSubtitlesEnabled(enabled)
        }
    }

    fun setPreferredAudioLanguage(lang: String?) {
        viewModelScope.launch {
            logger.i(TAG, "setPreferredAudioLanguage: $lang")
            settingsManager.setPreferredAudioLanguage(lang)
        }
    }

    fun setSubtitleTextSize(size: String) {
        viewModelScope.launch {
            logger.i(TAG, "setSubtitleTextSize: $size")
            settingsManager.setSubtitleTextSize(size)
        }
    }

    fun setSubtitleTextColor(color: String) {
        viewModelScope.launch {
            logger.i(TAG, "setSubtitleTextColor: $color")
            settingsManager.setSubtitleTextColor(color)
        }
    }

    fun setSubtitleBackgroundStyle(style: String) {
        viewModelScope.launch {
            logger.i(TAG, "setSubtitleBackgroundStyle: $style")
            settingsManager.setSubtitleBackgroundStyle(style)
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setAutoplayNext: $enabled")
            settingsManager.setAutoplayNext(enabled)
        }
    }

    fun setRememberVolume(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setRememberVolume: $enabled")
            settingsManager.setRememberVolume(enabled)
        }
    }

    fun setContinueWatching(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setContinueWatching: $enabled")
            settingsManager.setContinueWatching(enabled)
        }
    }

    fun dismissFromContinueWatching(videoId: String) {
        viewModelScope.launch {
            logger.i(TAG, "dismissFromContinueWatching: $videoId")
            library.dismissFromContinueWatching(videoId)
            showMessage("Removed from Continue Watching")
        }
    }

    fun setDefaultScreenOrientation(orientation: String) {
        viewModelScope.launch {
            logger.i(TAG, "setDefaultScreenOrientation: $orientation")
            settingsManager.setDefaultScreenOrientation(orientation)
        }
    }

    fun setDefaultMediaFit(fit: String) {
        viewModelScope.launch {
            logger.i(TAG, "setDefaultMediaFit: $fit")
            settingsManager.setDefaultMediaFit(fit)
        }
    }

    fun setBrightnessGesture(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setBrightnessGesture: $enabled")
            settingsManager.setBrightnessGesture(enabled)
        }
    }

    fun setVolumeGesture(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setVolumeGesture: $enabled")
            settingsManager.setVolumeGesture(enabled)
        }
    }

    fun setSubtitlePosition(percentY: Float) {
        viewModelScope.launch {
            logger.i(TAG, "setSubtitlePosition: $percentY")
            settingsManager.setSubtitlePosition(percentY)
        }
    }

    fun setAutoRescanOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setAutoRescanOnLaunch: $enabled")
            settingsManager.setAutoRescanOnLaunch(enabled)
        }
    }

    fun setHeadsetDetection(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setHeadsetDetection: $enabled")
            settingsManager.setHeadsetDetection(enabled)
        }
    }

    fun setStopOnScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setStopOnScreenOff: $enabled")
            settingsManager.setStopOnScreenOff(enabled)
        }
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setHardwareAcceleration: $enabled")
            settingsManager.setHardwareAcceleration(enabled)
        }
    }

    fun setHardwareAccelerationMode(mode: String) {
        viewModelScope.launch {
            logger.i(TAG, "setHardwareAccelerationMode: $mode")
            settingsManager.setHardwareAccelerationMode(mode)
        }
    }

    fun setDialogueBoost(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setDialogueBoost: $enabled")
            settingsManager.setDialogueBoost(enabled)
        }
    }

    fun setMatchDisplayRefreshRate(enabled: Boolean) {
        viewModelScope.launch {
            logger.i(TAG, "setMatchDisplayRefreshRate: $enabled")
            settingsManager.setMatchDisplayRefreshRate(enabled)
        }
    }

    fun showMessage(text: String, isError: Boolean = false, actionLabel: String? = null) {
        _userMessage.value = UserMessage(message = text, isError = isError, actionLabel = actionLabel)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    // Update methods
    fun checkForUpdates(isManualCheck: Boolean = false) {
        viewModelScope.launch {
            logger.i(TAG, "Checking for updates (manual: $isManualCheck)")
            val result = updateManager.checkForUpdates(isManualCheck)
            if (result is UpdateState.UpToDate && isManualCheck) {
                showMessage("You're on the latest version!")
            } else if (result is UpdateState.Error && isManualCheck) {
                showMessage(result.message, isError = true)
            }
        }
    }

    fun downloadAndInstallUpdate(asset: GitHubAsset) {
        viewModelScope.launch {
            updateManager.downloadAndInstallUpdate(asset)
        }
    }

    fun dismissUpdate() {
        updateManager.dismissUpdate()
    }

    fun installApk(file: File) {
        updateManager.installApk(file)
    }
}
