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

data class VideoGroup(
    val id: String,
    val name: String,
    val path: String,
    val videos: List<Video>
)

data class LibraryUiState(
    val videos: List<Video> = emptyList(),
    val continueWatching: List<Video> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val favorites: List<Video> = emptyList(),
    val favoriteFolders: List<Folder> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val groups: List<VideoGroup> = emptyList(),
    val folderVideosMap: Map<String, List<Video>> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<Video> = emptyList(),
    val selectedFolder: Folder? = null,
    val folderVideos: List<Video> = emptyList(),
    val isSyncing: Boolean = false,
    val loading: Boolean = true
)

fun normalizeFolderPath(raw: String?): String {
    val trimmed = raw.orEmpty().trim().trimEnd('/')
    return when {
        trimmed.isEmpty() || trimmed == "/storage/emulated/0" || trimmed == "/storage/emulated" || trimmed.equals("Internal Storage", ignoreCase = true) -> "/storage/emulated/0"
        trimmed.startsWith("/storage/emulated/0") -> trimmed
        trimmed.startsWith("/storage/") -> trimmed
        trimmed.startsWith("/") -> trimmed
        else -> "/storage/emulated/0/$trimmed"
    }
}

private val NOISE_TAGS = setOf(
    "1080p", "720p", "480p", "360p", "2160p", "4k", "uhd", "fhd", "hd",
    "x264", "x265", "h264", "h265", "hevc", "avc", "10bit", "8bit",
    "web-dl", "webrip", "bluray", "bdrip", "dvdrip", "hdtv", "hdrip",
    "aac", "aac2", "dts", "ac3", "ddp5", "ddp", "eac3", "flac", "mp3",
    "repack", "proper", "remux", "dual", "multi", "eng", "ita", "sub",
    "dub", "uncensored", "directors", "cut", "extended", "complete"
)

private val GENERIC_FOLDER_NAMES = setOf(
    "0", "emulated", "storage", "internal storage", "videos", "video",
    "dcim", "camera", "download", "downloads", "telegram", "whatsapp video",
    "movies", "movie", "series", "tv shows", "tv", "media"
)

private fun cleanSeriesTitle(raw: String): String {
    var name = raw.trim()
    name = name.replace(Regex("""^\s*\[[^\]]+\]\s*"""), "")
    name = name.replace(Regex("""^\s*\([^)]+\)\s*"""), "")
    if (name.contains('.')) {
        val ext = name.substringAfterLast('.').lowercase()
        if (ext in setOf("mp4", "mkv", "avi", "webm", "mov", "flv", "ts", "m4v", "3gp")) {
            name = name.substringBeforeLast('.')
        }
    }
    name = name.replace('.', ' ').replace('_', ' ')

    val tokens = name.split(Regex("""\s+""")).filter { it.isNotBlank() }
    val cleanTokens = mutableListOf<String>()
    for (token in tokens) {
        val lower = token.lowercase().trim('(', ')', '[', ']', '{', '}', '-', '_', '.')
        if (NOISE_TAGS.contains(lower) || lower.matches(Regex("""\d{3,4}p""")) || (lower.length == 4 && lower.startsWith("20") && lower.toIntOrNull() != null && cleanTokens.isNotEmpty())) {
            break
        }
        val cleaned = token.trim('(', ')', '[', ']', '{', '}', '-', '_', '.')
        if (cleaned.isNotBlank()) {
            cleanTokens.add(cleaned)
        }
    }

    val result = cleanTokens.joinToString(" ").trim()
    return if (result.length >= 2) {
        result.split(" ").joinToString(" ") { word ->
            if (word.all { it.isUpperCase() } && word.length <= 4) word
            else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    } else {
        raw.trim()
    }
}

private fun extractSeriesSeason(displayName: String, folderName: String): Pair<String, String>? {
    val cleanName = displayName.trim()

    // Pattern 1: S11E01 / S11.E01 / S11_E01 / S11 - E01 / Season 11 Episode 01
    val sMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(?:s|season)[.\s_\-]*(\d{1,2})[.\s_\-]*(?:e|ep|episode)[.\s_\-]*\d{1,3}""", RegexOption.IGNORE_CASE).find(cleanName)
    if (sMatch != null) {
        val rawSeries = sMatch.groupValues[1]
        val seasonNum = sMatch.groupValues[2].toIntOrNull()
        val seriesTitle = cleanSeriesTitle(rawSeries)
        if (seriesTitle.isNotBlank()) {
            val seasonStr = if (seasonNum != null) "S$seasonNum" else ""
            val key = "${seriesTitle.lowercase()}_s${seasonNum ?: 0}"
            val display = if (seasonNum != null) "$seriesTitle $seasonStr" else seriesTitle
            return Pair(key, display)
        }
    }

    // Pattern 2: 11x01 (e.g. Supernatural.11x01.mp4)
    val xMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(\d{1,2})x\d{1,3}""", RegexOption.IGNORE_CASE).find(cleanName)
    if (xMatch != null) {
        val rawSeries = xMatch.groupValues[1]
        val seasonNum = xMatch.groupValues[2].toIntOrNull()
        val seriesTitle = cleanSeriesTitle(rawSeries)
        if (seriesTitle.isNotBlank() && seasonNum != null) {
            val key = "${seriesTitle.lowercase()}_s$seasonNum"
            val display = "$seriesTitle S$seasonNum"
            return Pair(key, display)
        }
    }

    // Pattern 3: S11 / Season 11 alone in file name
    val sOnlyMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(?:s|season)[.\s_\-]*(\d{1,2})(?:[.\s_\-]|\b)""", RegexOption.IGNORE_CASE).find(cleanName)
    if (sOnlyMatch != null) {
        val rawSeries = sOnlyMatch.groupValues[1]
        val seasonNum = sOnlyMatch.groupValues[2].toIntOrNull()
        val seriesTitle = cleanSeriesTitle(rawSeries)
        if (seriesTitle.isNotBlank() && seasonNum != null) {
            val key = "${seriesTitle.lowercase()}_s$seasonNum"
            val display = "$seriesTitle S$seasonNum"
            return Pair(key, display)
        }
    }

    // Pattern 4: Anime episode format: [SubsPlease] Show - 01 (1080p).mkv
    val animeMatch = Regex("""^(?:\[[^\]]+\]\s*)?(.+?)\s*[-_]\s*(?:(?:ep|episode)\s*)?(\d{1,4})(?:\s*\(.*?\))?(?:\s*\[.*?\])?(?:\.[\w\d]+)?$""", RegexOption.IGNORE_CASE).find(cleanName)
    if (animeMatch != null) {
        val rawSeries = animeMatch.groupValues[1]
        val seriesTitle = cleanSeriesTitle(rawSeries)
        if (seriesTitle.length >= 2 && !seriesTitle.all { it.isDigit() }) {
            val folderSeason = Regex("""(?i)(?:s|season)[.\s_\-]*(\d{1,2})""").find(folderName)?.groupValues?.get(1)?.toIntOrNull()
            val key = if (folderSeason != null) "${seriesTitle.lowercase()}_s$folderSeason" else seriesTitle.lowercase()
            val display = if (folderSeason != null) "$seriesTitle S$folderSeason" else seriesTitle
            return Pair(key, display)
        }
    }

    // Pattern 5: Check folder name for Series/Season
    if (folderName.isNotBlank() && !GENERIC_FOLDER_NAMES.contains(folderName.lowercase().trim())) {
        val folderSeasonMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(?:s|season)[.\s_\-]*(\d{1,2})""", RegexOption.IGNORE_CASE).find(folderName)
        if (folderSeasonMatch != null) {
            val rawSeries = folderSeasonMatch.groupValues[1]
            val seasonNum = folderSeasonMatch.groupValues[2].toIntOrNull()
            val seriesTitle = cleanSeriesTitle(rawSeries)
            if (seriesTitle.isNotBlank() && seasonNum != null) {
                return Pair("${seriesTitle.lowercase()}_s$seasonNum", "$seriesTitle S$seasonNum")
            }
        }
    }

    return null
}

private fun computeVideoGroups(videos: List<Video>, folders: List<Folder>): List<VideoGroup> {
    if (videos.isEmpty()) return emptyList()

    val groups = mutableListOf<VideoGroup>()
    val groupedVideoIds = mutableSetOf<String>()

    // Pass 1: Smart Series & Season pattern extraction
    val seriesSeasonBuckets = mutableMapOf<String, Pair<String, MutableList<Video>>>()
    for (video in videos) {
        val extracted = extractSeriesSeason(video.displayName, video.folderName)
        if (extracted != null) {
            val (key, display) = extracted
            val bucket = seriesSeasonBuckets.getOrPut(key) { Pair(display, mutableListOf()) }
            bucket.second.add(video)
        }
    }

    for ((key, pair) in seriesSeasonBuckets) {
        val (displayName, vids) = pair
        if (vids.size >= 2) {
            val sortedVideos = vids.distinctBy { it.id }.sortedWith(NaturalVideoComparator)
            groups.add(
                VideoGroup(
                    id = "group_$key",
                    name = displayName,
                    path = sortedVideos.firstOrNull()?.folderPath.orEmpty(),
                    videos = sortedVideos
                )
            )
            vids.forEach { groupedVideoIds.add(it.id) }
        }
    }

    // Pass 2: Remaining videos grouped by dedicated non-generic folders
    val remainingVideos = videos.filter { it.id !in groupedVideoIds }
    val folderBuckets = mutableMapOf<String, MutableList<Video>>()
    for (video in remainingVideos) {
        val folderName = video.folderName.trim()
        val cleanNorm = folderName.lowercase()
        if (folderName.isNotEmpty() && !GENERIC_FOLDER_NAMES.contains(cleanNorm)) {
            val folderKey = video.folderPath.ifEmpty { folderName }
            folderBuckets.getOrPut(folderKey) { mutableListOf() }.add(video)
        }
    }

    for ((folderPath, vids) in folderBuckets) {
        if (vids.size >= 2) {
            val folderName = vids.firstOrNull()?.folderName ?: "Videos"
            val sortedVideos = vids.distinctBy { it.id }.sortedWith(NaturalVideoComparator)
            groups.add(
                VideoGroup(
                    id = "folder_$folderPath",
                    name = cleanSeriesTitle(folderName),
                    path = folderPath,
                    videos = sortedVideos
                )
            )
            vids.forEach { groupedVideoIds.add(it.id) }
        }
    }

    return groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun computeFolderVideosMap(videos: List<Video>): Map<String, List<Video>> {
    if (videos.isEmpty()) return emptyMap()
    val map = mutableMapOf<String, MutableList<Video>>()
    for (v in videos) {
        val norm = normalizeFolderPath(v.folderPath)
        map.getOrPut(norm) { mutableListOf() }.add(v)
        if (v.folderName.isNotEmpty()) {
            map.getOrPut(v.folderName.lowercase()) { mutableListOf() }.add(v)
        }
    }
    return map.mapValues { (_, list) -> list.sortedWith(NaturalVideoComparator) }
}

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

    private var cachedVideosRef: List<Video>? = null
    private var cachedFoldersRef: List<Folder>? = null
    private var cachedGroups: List<VideoGroup> = emptyList()
    private var cachedFolderMap: Map<String, List<Video>> = emptyMap()

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
        folderVideos,
        settingsManager.settings
    ) { values ->
        val videos = values[0] as List<Video>
        val continueWatching = values[1] as List<Video>
        val folders = values[2] as List<Folder>
        val favorites = values[3] as List<Video>
        val playlists = values[4] as List<Playlist>
        val searchQuery = values[5] as String
        val searchResultsList = values[6] as List<Video>
        val currentFolder = values[7] as Folder?
        val currentFolderVideos = values[8] as List<Video>
        val settings = values[9] as UserSettings

        val groups: List<VideoGroup>
        val folderMap: Map<String, List<Video>>

        if (videos === cachedVideosRef && folders === cachedFoldersRef) {
            groups = cachedGroups
            folderMap = cachedFolderMap
        } else {
            groups = computeVideoGroups(videos, folders)
            folderMap = computeFolderVideosMap(videos)
            cachedVideosRef = videos
            cachedFoldersRef = folders
            cachedGroups = groups
            cachedFolderMap = folderMap
        }

        val favoriteFolders = folders.filter { settings.favoriteFolderPaths.contains(it.path) }

        LibraryUiState(
            videos = videos,
            continueWatching = continueWatching,
            folders = folders,
            favorites = favorites,
            favoriteFolders = favoriteFolders,
            playlists = playlists,
            groups = groups,
            folderVideosMap = folderMap,
            searchQuery = searchQuery,
            searchResults = searchResultsList,
            selectedFolder = currentFolder,
            folderVideos = currentFolderVideos,
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
            showMessage("Playlist deleted")
        }
    }

    fun deleteVideos(videos: List<Video>) {
        viewModelScope.launch {
            if (videos.isEmpty()) return@launch
            logger.i(TAG, "deleteVideos: ${videos.size} items")
            var successCount = 0
            for (vid in videos) {
                if (library.deleteVideo(vid.id) is ExcavResult.Success) {
                    successCount++
                }
            }
            if (successCount == videos.size) {
                showMessage("${videos.size} ${if (videos.size == 1) "video" else "videos"} deleted")
            } else {
                showMessage("Deleted $successCount of ${videos.size} videos")
            }
        }
    }

    fun deleteFolders(folders: List<Folder>, folderVideosMap: Map<String, List<Video>>) {
        viewModelScope.launch {
            if (folders.isEmpty()) return@launch
            logger.i(TAG, "deleteFolders: ${folders.size} folders")
            var totalVidsDeleted = 0
            var diskDeleteFailures = 0
            for (f in folders) {
                val normP = normalizeFolderPath(f.path)
                val vids = folderVideosMap[normP] ?: folderVideosMap[f.name.lowercase()].orEmpty()
                for (vid in vids) {
                    if (library.deleteVideo(vid.id) is ExcavResult.Success) {
                        totalVidsDeleted++
                    }
                }
                try {
                    val dir = java.io.File(f.path)
                    if (dir.exists() && dir.isDirectory) {
                        val deleted = dir.deleteRecursively()
                        if (!deleted) {
                            diskDeleteFailures++
                            logger.w(TAG, "deleteRecursively returned false for: ${f.path}")
                        }
                    }
                } catch (e: SecurityException) {
                    diskDeleteFailures++
                    logger.w(TAG, "SecurityException deleting folder on disk: ${f.path}", e)
                } catch (e: Exception) {
                    diskDeleteFailures++
                    logger.w(TAG, "Failed to delete folder on disk: ${f.path}", e)
                }
            }
            library.refresh()
            if (diskDeleteFailures > 0) {
                showMessage("${folders.size} ${if (folders.size == 1) "folder" else "folders"} removed from library")
            } else {
                showMessage("${folders.size} ${if (folders.size == 1) "folder" else "folders"} deleted")
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
                        val curFavs = userSettings.value.favoriteFolderPaths
                        if (curFavs.contains(folder.path) || curFavs.contains(normalizeFolderPath(folder.path))) {
                            val updated = curFavs.toMutableSet()
                            updated.remove(folder.path)
                            updated.remove(normalizeFolderPath(folder.path))
                            updated.add(newDir.absolutePath)
                            updated.add(normalizeFolderPath(newDir.absolutePath))
                            settingsManager.setFavoriteFolders(updated)
                        }
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

    fun deletePlaylists(playlists: List<Playlist>) {
        viewModelScope.launch {
            if (playlists.isEmpty()) return@launch
            logger.i(TAG, "deletePlaylists: ${playlists.size} playlists")
            for (pl in playlists) {
                playlistManager.deletePlaylist(pl.id)
            }
            showMessage("${playlists.size} ${if (playlists.size == 1) "playlist" else "playlists"} deleted")
        }
    }

    fun setVideosFavorite(videos: List<Video>, isFavorite: Boolean) {
        viewModelScope.launch {
            if (videos.isEmpty()) return@launch
            for (vid in videos) {
                if (vid.isFavorite != isFavorite) {
                    library.toggleFavorite(vid.id)
                }
            }
            showMessage(
                if (isFavorite) "Added ${videos.size} ${if (videos.size == 1) "video" else "videos"} to Favorites"
                else "Removed ${videos.size} ${if (videos.size == 1) "video" else "videos"} from Favorites"
            )
        }
    }

    fun addVideosToFavorites(videos: List<Video>) {
        setVideosFavorite(videos, true)
    }

    fun toggleFavoriteFolder(folder: Folder) {
        viewModelScope.launch {
            settingsManager.toggleFavoriteFolder(folder.path)
            val isNowFav = !userSettings.value.favoriteFolderPaths.contains(folder.path)
            showMessage(if (isNowFav) "Added folder to Favorites" else "Removed folder from Favorites")
        }
    }

    fun setFoldersFavorite(folders: List<Folder>, isFavorite: Boolean) {
        viewModelScope.launch {
            if (folders.isEmpty()) return@launch
            val current = userSettings.value.favoriteFolderPaths.toMutableSet()
            for (f in folders) {
                val norm = normalizeFolderPath(f.path)
                if (isFavorite) {
                    current.add(f.path)
                    current.add(norm)
                } else {
                    current.remove(f.path)
                    current.remove(norm)
                }
            }
            settingsManager.setFavoriteFolders(current)
            showMessage(
                if (isFavorite) "Added ${folders.size} ${if (folders.size == 1) "folder" else "folders"} to Favorites"
                else "Removed ${folders.size} ${if (folders.size == 1) "folder" else "folders"} from Favorites"
            )
        }
    }

    fun addFoldersToFavorites(folders: List<Folder>) {
        setFoldersFavorite(folders, true)
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
