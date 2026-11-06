package com.excavplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.derivation.LibraryDerivation
import com.excavplayer.domain.derivation.VideoGroup
import com.excavplayer.domain.lifecycle.DeleteOutcome
import com.excavplayer.domain.lifecycle.VideoLifecycle
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.domain.model.Video
import com.excavplayer.library.VideoLibrary
import com.excavplayer.player.core.PlayerController
import com.excavplayer.player.queue.PlaybackQueue
import com.excavplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.excavplayer.update.AppUpdateManager
import com.excavplayer.update.GitHubAsset
import com.excavplayer.update.UpdateState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import android.content.Context
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.update
import com.excavplayer.R

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
    val groups: List<VideoGroup> = emptyList(),
    val folderVideosMap: Map<String, List<Video>> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<Video> = emptyList(),
    val selectedFolder: Folder? = null,
    val folderVideos: List<Video> = emptyList(),
    val isSyncing: Boolean = false,
    val loading: Boolean = true
)

@HiltViewModel
class ExcavViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: VideoLibrary,
    private val lifecycle: VideoLifecycle,
    private val settings: SettingsRepository,
    val player: PlayerController,
    val queue: PlaybackQueue,
    val updateManager: AppUpdateManager,
    private val logger: AppLogger
) : ViewModel() {

    companion object {
        private const val TAG = "ExcavViewModel"
    }

    /** System-approval delete requests waiting for the activity to fire the dialog. */
    private val _pendingApproval = MutableStateFlow<DeleteOutcome.ApprovalRequired?>(null)
    val pendingApproval: StateFlow<DeleteOutcome.ApprovalRequired?> = _pendingApproval.asStateFlow()

    /** The activity consumes the approval right before firing the system dialog. */
    fun onApprovalConsumed() {
        _pendingApproval.value = null
    }

    init {
        viewModelScope.launch {
            try {
                lifecycle.refresh(force = true)
            } catch (e: Throwable) {
                logger.w(TAG, "Startup media purge/refresh warning: ${e.message}")
            }
        }
    }

    private val query = MutableStateFlow("")
    private val selectedFolder = MutableStateFlow<Folder?>(null)
    private val _userMessage = MutableStateFlow<UserMessage?>(null)
    val userMessage: StateFlow<UserMessage?> = _userMessage.asStateFlow()

    val userSettings: StateFlow<UserSettings> = settings.userSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val updateState: StateFlow<UpdateState> = updateManager.updateState

    val pagedVideos: kotlinx.coroutines.flow.Flow<PagingData<Video>> = library.observePagedVideos()
        .cachedIn(viewModelScope)

    @OptIn(FlowPreview::class)
    private val searchResults = query
        .debounce(280L)
        .flatMapLatest { value ->
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
        query,
        searchResults,
        selectedFolder,
        folderVideos,
        settings.userSettings
    ) { values ->
        val videos = values[0] as List<Video>
        val continueWatching = values[1] as List<Video>
        val folders = values[2] as List<Folder>
        val searchQuery = values[3] as String
        val searchResultsList = values[4] as List<Video>
        val currentFolder = values[5] as Folder?
        val currentFolderVideos = values[6] as List<Video>
        val settings = values[7] as UserSettings

        val derived = LibraryDerivation.derive(videos, folders)
        val groups = derived.groups
        val folderMap = derived.folderVideosMap

        val resolvedFolderVideos = if (currentFolderVideos.isNotEmpty()) {
            currentFolderVideos
        } else if (currentFolder != null) {
            val norm = LibraryDerivation.normalizeFolderPath(currentFolder.path)
            folderMap[norm] ?: folderMap[currentFolder.name.lowercase()].orEmpty()
        } else {
            emptyList()
        }

        LibraryUiState(
            videos = videos,
            continueWatching = continueWatching,
            folders = folders,
            groups = groups,
            folderVideosMap = folderMap,
            searchQuery = searchQuery,
            searchResults = searchResultsList,
            selectedFolder = currentFolder,
            folderVideos = resolvedFolderVideos,
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
            settings.updateLastOpenedFolder(path)
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshLibrary() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                logger.i(TAG, "User requested media library refresh")
                when (val result = lifecycle.refresh(force = true)) {
                    is ExcavResult.Success -> {
                        // Refreshed silently without toast banner
                    }
                    is ExcavResult.Error -> {
                        logger.e(TAG, "Failed to refresh library", result.exception)
                        showMessage("Failed to sync media: ${result.message ?: result.exception.localizedMessage ?: "Unknown error"}", isError = true)
                    }
                    is ExcavResult.Loading -> Unit
                }
            } finally {
                _isRefreshing.value = false
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
        val state = libraryState.value
        // Resolve the context centrally: continue-watching, search, and external
        // intents pass no list, so the queue must be rebuilt from the library.
        // Resolving here (group first, then folder, then library) keeps those
        // entry points from silently collapsing to a single-item queue.
        val activeList = if (!contextList.isNullOrEmpty()) {
            contextList
        } else {
            LibraryDerivation.resolvePlaybackContext(video, state.groups, state.videos)
        }
        val items = if (activeList.any { it.id == video.id }) activeList else listOf(video)
        val index = items.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        queue.setQueue(items, index)
        player.playNow(video, video.resumePositionMs)
    }

    fun playQueueItem(index: Int) {
        logger.i(TAG, "playQueueItem at index: $index")
        val video = queue.moveTo(index)
        if (video != null) {
            player.playNow(video, video.resumePositionMs)
        } else {
            logger.w(TAG, "Queue item at index $index was null")
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
        deleteVideos(listOf(video))
    }

    fun deleteVideos(videos: List<Video>) {
        viewModelScope.launch {
            if (videos.isEmpty()) return@launch
            logger.i(TAG, "deleteVideos: ${videos.size} items")

            when (val outcome = lifecycle.deleteVideos(videos)) {
                is DeleteOutcome.ApprovalRequired -> _pendingApproval.value = outcome
                is DeleteOutcome.Deleted ->
                    showMessage("${videos.size} ${if (videos.size == 1) "video" else "videos"} deleted")
                is DeleteOutcome.Partial ->
                    showMessage("Deleted ${outcome.deletedIds.size} of ${videos.size} videos")
            }
        }
    }

    fun onDeleteConfirmed(videoIds: List<String>) {
        viewModelScope.launch {
            _pendingApproval.value = null
            logger.i(TAG, "onDeleteConfirmed for ${videoIds.size} videos")
            when (val res = lifecycle.finalizeApprovedDeletion(videoIds)) {
                is ExcavResult.Success -> {
                    showMessage("${videoIds.size} ${if (videoIds.size == 1) "video" else "videos"} deleted")
                }
                is ExcavResult.Error -> {
                    showMessage("Failed to update library after deletion: ${res.message}", isError = true)
                }
                else -> Unit
            }
        }
    }

    /**
     * Folder deletion: descendants come from the fresh library state via the
     * folder-path prefix match, then the whole batch goes through the same
     * deleteVideos pipeline. No stale folder-videos map involved.
     */
    fun deleteFolders(folders: List<Folder>) {
        viewModelScope.launch {
            if (folders.isEmpty()) return@launch
            logger.i(TAG, "deleteFolders: ${folders.size} folders")
            val allVids = mutableListOf<Video>()
            val allLibraryVideos = libraryState.value.videos
            for (f in folders) {
                val normP = LibraryDerivation.normalizeFolderPath(f.path)
                val matching = allLibraryVideos.filter {
                    val vNorm = LibraryDerivation.normalizeFolderPath(it.folderPath)
                    vNorm == normP || vNorm.startsWith("$normP/")
                }
                allVids.addAll(matching)
            }
            val distinctVids = allVids.distinctBy { it.id }
            if (distinctVids.isNotEmpty()) {
                deleteVideos(distinctVids)
            } else {
                showMessage("No videos to delete in selected folders")
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            val clean = newName.trim()
            if (clean.isBlank()) {
                showMessage("Folder name cannot be empty", isError = true)
                return@launch
            }
            try {
                val oldDir = java.io.File(folder.path)
                if (oldDir.exists() && oldDir.isDirectory) {
                    val parent = oldDir.parentFile ?: java.io.File("/storage/emulated/0")
                    val newDir = java.io.File(parent, clean)
                    val renamed = oldDir.renameTo(newDir)
                    if (renamed) {
                        library.refresh()
                        showMessage("Folder renamed successfully")
                        return@launch
                    }
                }
                showMessage("Could not rename folder (protected by Android storage restrictions)", isError = true)
            } catch (e: SecurityException) {
                logger.e(TAG, "SecurityException renaming folder", e)
                showMessage("Permission denied by system storage", isError = true)
            } catch (e: Exception) {
                logger.e(TAG, "Error renaming folder", e)
                showMessage("Error renaming folder: ${e.message}", isError = true)
            }
        }
    }



    fun reorderQueue(from: Int, to: Int) {
        logger.d(TAG, "Reordering queue from $from to $to")
        queue.moveItem(from, to)
    }

    // Settings actions
    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateAutoResume(enabled)
        }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settings.updatePlaybackSpeed(speed)
        }
    }

    fun setDefaultRepeatMode(mode: RepeatMode) {
        viewModelScope.launch {
            settings.updateDefaultRepeatMode(mode)
        }
    }

    fun setResumeThreshold(threshold: Float) {
        viewModelScope.launch {
            settings.updateResumeThreshold(threshold)
        }
    }

    fun setPreferredSubtitleLanguage(lang: String?) {
        viewModelScope.launch {
            settings.updatePreferredSubtitleLanguage(lang)
        }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateSubtitlesEnabled(enabled)
        }
    }

    fun setPreferredAudioLanguage(lang: String?) {
        viewModelScope.launch {
            settings.updatePreferredAudioLanguage(lang)
        }
    }

    fun setSubtitleTextSize(size: String) {
        viewModelScope.launch {
            settings.updateSubtitleTextSize(size)
        }
    }

    fun setSubtitleTextColor(color: String) {
        viewModelScope.launch {
            settings.updateSubtitleTextColor(color)
        }
    }

    fun setSubtitleBackgroundStyle(style: String) {
        viewModelScope.launch {
            settings.updateSubtitleBackgroundStyle(style)
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateAutoplayNext(enabled)
        }
    }

    fun setContinueWatching(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateContinueWatching(enabled)
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
            settings.updateDefaultScreenOrientation(orientation)
        }
    }

    fun setDefaultMediaFit(fit: String) {
        viewModelScope.launch {
            settings.updateDefaultMediaFit(fit)
        }
    }

    fun setBrightnessGesture(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateBrightnessGesture(enabled)
        }
    }

    fun setVolumeGesture(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateVolumeGesture(enabled)
        }
    }

    fun setSubtitlePosition(percentY: Float) {
        viewModelScope.launch {
            settings.updateSubtitlePosition(percentY)
        }
    }

    fun setAutoRescanOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateAutoRescanOnLaunch(enabled)
        }
    }

    fun setHeadsetDetection(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateHeadsetDetection(enabled)
        }
    }

    fun setStopOnScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateStopOnScreenOff(enabled)
        }
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateHardwareAcceleration(enabled)
        }
    }

    fun setHardwareAccelerationMode(mode: String) {
        viewModelScope.launch {
            settings.updateHardwareAccelerationMode(mode)
        }
    }

    fun setDialogueBoost(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateDialogueBoost(enabled)
        }
    }

    fun setMatchDisplayRefreshRate(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateMatchDisplayRefreshRate(enabled)
        }
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.updateLiquidGlassEnabled(enabled)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        player.setVideoEqualizerEnabled(enabled)
    }

    fun setEqualizerPreset(preset: String) {
        player.setVideoEqualizerPreset(preset)
    }

    fun setEqualizerBandLevels(levels: String) {
        player.setVideoEqualizerBandLevels(levels)
    }

    fun setBassBoostStrength(strength: Int) {
        player.setVideoEqualizerBassStrength(strength)
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

    fun setSubtitleDelay(delayMs: Long) {
        player.setSubtitleDelay(delayMs)
    }
}
