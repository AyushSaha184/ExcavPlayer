package com.excavplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.derivation.LibraryDerivation
import com.excavplayer.domain.derivation.VideoGroup
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.model.NaturalVideoComparator
import com.excavplayer.library.VideoLibrary
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.queue.PlaybackQueue
import com.excavplayer.settings.SettingsManager
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
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.excavplayer.domain.model.MediaSourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.update
import com.excavplayer.subtitles.SubtitleHelper
import com.excavplayer.subtitles.SubtitleSearchResult
import com.excavplayer.subtitles.SubtitleLanguage
import com.excavplayer.subtitles.SubtitleSource

sealed interface DeleteRequestEvent {
    data class MediaStoreDelete(
        val intentSender: IntentSender,
        val videoIds: List<String>
    ) : DeleteRequestEvent
}

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

data class OnlineSubtitleUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<SubtitleSearchResult> = emptyList(),
    val downloadingIds: Set<String> = emptySet(),
    val downloadedMap: Map<String, File> = emptyMap(),
    val appliedSubtitleId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ExcavViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: VideoLibrary,
    private val settingsManager: SettingsManager,
    val player: PlayerManager,
    val queue: PlaybackQueue,
    val updateManager: AppUpdateManager,
    val subtitleHelper: SubtitleHelper,
    private val logger: AppLogger
) : ViewModel() {

    companion object {
        private const val TAG = "ExcavViewModel"
    }

    private val _deleteRequestEvents = Channel<DeleteRequestEvent>(Channel.BUFFERED)
    val deleteRequestEvents = _deleteRequestEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                library.purgeCorruptedEntries()
                library.refresh(force = true)
            } catch (e: Throwable) {
                logger.w(TAG, "Startup media purge/refresh warning: ${e.message}")
            }
        }
    }

    private val query = MutableStateFlow("")
    private val selectedFolder = MutableStateFlow<Folder?>(null)
    private val _userMessage = MutableStateFlow<UserMessage?>(null)
    val userMessage: StateFlow<UserMessage?> = _userMessage.asStateFlow()

    val userSettings: StateFlow<UserSettings> = settingsManager.settings
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
        settingsManager.settings
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
            settingsManager.setLastOpenedFolder(path)
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
                library.purgeCorruptedEntries()
                when (val result = library.refresh(force = true)) {
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

            val mediaStoreUris = mutableListOf<Uri>()
            val mediaStoreIds = mutableListOf<String>()
            val directVideos = mutableListOf<Video>()

            for (v in videos) {
                val uri = Uri.parse(v.uri)
                val isMediaStore = uri.scheme == ContentResolver.SCHEME_CONTENT &&
                    v.sourceType != MediaSourceType.LOCAL_DOCUMENT &&
                    v.sourceType != MediaSourceType.LOCAL_TREE

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isMediaStore) {
                    mediaStoreUris.add(uri)
                    mediaStoreIds.add(v.id)
                } else {
                    directVideos.add(v)
                }
            }

            var directSuccess = 0
            for (vid in directVideos) {
                when (library.deleteVideo(vid.id)) {
                    is ExcavResult.Success -> directSuccess++
                    else -> Unit
                }
            }

            if (mediaStoreUris.isNotEmpty()) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                    _deleteRequestEvents.send(
                        DeleteRequestEvent.MediaStoreDelete(
                            pendingIntent.intentSender,
                            mediaStoreIds
                        )
                    )
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to create MediaStore delete request", e)
                    showMessage("Failed to request deletion: ${e.message}", isError = true)
                }
            } else if (directVideos.isNotEmpty()) {
                if (directSuccess == directVideos.size) {
                    showMessage("${directVideos.size} ${if (directVideos.size == 1) "video" else "videos"} deleted")
                } else {
                    showMessage("Deleted $directSuccess of ${directVideos.size} videos")
                }
            }
        }
    }

    fun onDeleteConfirmed(videoIds: List<String>) {
        viewModelScope.launch {
            logger.i(TAG, "onDeleteConfirmed for ${videoIds.size} videos")
            when (val res = library.deleteVideosAfterConfirmation(videoIds)) {
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

    fun deleteFolders(folders: List<Folder>, folderVideosMap: Map<String, List<Video>>) {
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
                if (matching.isNotEmpty()) {
                    allVids.addAll(matching)
                } else {
                    val fallback = folderVideosMap[normP] ?: folderVideosMap[f.name.lowercase()].orEmpty()
                    allVids.addAll(fallback)
                }
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

    // -------------------------------------------------------------------------------------
    // Online Subtitles
    // -------------------------------------------------------------------------------------
    private val _onlineSubtitleState = MutableStateFlow(OnlineSubtitleUiState())
    val onlineSubtitleState: StateFlow<OnlineSubtitleUiState> = _onlineSubtitleState.asStateFlow()

    fun updateSubtitleQuery(query: String) {
        _onlineSubtitleState.update { it.copy(query = query) }
    }

    fun searchOnlineSubtitles(queryOverride: String? = null) {
        val q = (queryOverride ?: _onlineSubtitleState.value.query).trim()
        if (q.isBlank()) return

        val preferredLang = userSettings.value.preferredSubtitleLanguage
        val langCode = if (preferredLang.isNullOrBlank() || preferredLang.equals("auto", ignoreCase = true) || preferredLang.equals("auto (default)", ignoreCase = true)) {
            "en"
        } else {
            preferredLang.trim().lowercase()
        }

        viewModelScope.launch {
            _onlineSubtitleState.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val results = subtitleHelper.searchOnlineSubtitles(
                    query = q,
                    languageCode = langCode,
                    source = SubtitleSource.ALL
                )
                _onlineSubtitleState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = results,
                        errorMessage = if (results.isEmpty()) "No subtitles found for \"$q\"" else null
                    )
                }
            } catch (e: Exception) {
                logger.e(TAG, "Subtitle search failed: ${e.message}", e)
                _onlineSubtitleState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = e.message ?: "Failed to search subtitles"
                    )
                }
            }
        }
    }

    fun downloadAndApplySubtitle(result: SubtitleSearchResult, videoTitle: String) {
        viewModelScope.launch {
            _onlineSubtitleState.update {
                it.copy(downloadingIds = it.downloadingIds + result.id, errorMessage = null)
            }
            try {
                val dlResult = subtitleHelper.downloadAndSaveSubtitle(result, videoTitle)
                val file = dlResult.getOrThrow()
                val updatedMap = _onlineSubtitleState.value.downloadedMap + (result.id to file)

                val shouldApplyImmediately = _onlineSubtitleState.value.appliedSubtitleId == null

                if (shouldApplyImmediately) {
                    val uri = Uri.fromFile(file).toString()
                    val mimeType = subtitleHelper.inferMimeTypeFromUri(Uri.fromFile(file))
                    val label = if (result.fileName.isNotBlank()) {
                        result.fileName.substringBeforeLast('.')
                    } else {
                        "${result.language} (${result.source})"
                    }
                    player.addExternalSubtitle(uri, label, mimeType)

                    _onlineSubtitleState.update {
                        it.copy(
                            downloadingIds = it.downloadingIds - result.id,
                            downloadedMap = updatedMap,
                            appliedSubtitleId = result.id
                        )
                    }
                    showMessage("Subtitle applied: $label")
                } else {
                    _onlineSubtitleState.update {
                        it.copy(
                            downloadingIds = it.downloadingIds - result.id,
                            downloadedMap = updatedMap
                        )
                    }
                }
            } catch (e: Exception) {
                logger.e(TAG, "Subtitle download failed: ${e.message}", e)
                _onlineSubtitleState.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - result.id,
                        errorMessage = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun applyDownloadedResult(result: SubtitleSearchResult) {
        val file = _onlineSubtitleState.value.downloadedMap[result.id] ?: return
        val uri = Uri.fromFile(file).toString()
        val mimeType = subtitleHelper.inferMimeTypeFromUri(Uri.fromFile(file))
        val label = if (result.fileName.isNotBlank()) {
            result.fileName.substringBeforeLast('.')
        } else {
            "${result.language} (${result.source})"
        }
        player.addExternalSubtitle(uri, label, mimeType)
        _onlineSubtitleState.update { it.copy(appliedSubtitleId = result.id) }
        showMessage("Subtitle applied: $label")
    }

    fun setSubtitleDelay(delayMs: Long) {
        player.setSubtitleDelay(delayMs)
    }
}
