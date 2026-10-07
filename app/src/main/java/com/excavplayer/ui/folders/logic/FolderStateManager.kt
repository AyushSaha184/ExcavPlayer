package com.excavplayer.ui.folders.logic

import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI State for the Folder Navigation and directory tree hierarchy view.
 */
data class FolderUiState(
    val currentPath: String? = null,
    val normalizedPath: String = FolderTreeManager.INTERNAL_STORAGE_ROOT,
    val currentFolderName: String = "Internal Storage",
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val subfolders: List<Folder> = emptyList(),
    val subfolderPreviewVideos: Map<String, List<Video>> = emptyMap(),
    val directVideos: List<Video> = emptyList(),
    val selectedFolderPaths: Set<String> = emptySet(),
    val selectedVideoIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val canGoBack: Boolean = false,
    val isEmpty: Boolean = false
)

/**
 * Dedicated manager holding folder navigation stack, directory tree hierarchy state,
 * and multi-selection mode.
 */
class FolderStateManager(
    initialPath: String? = null
) {
    private val _currentPath = MutableStateFlow<String?>(initialPath)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    private val _selectedFolderPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderPaths: StateFlow<Set<String>> = _selectedFolderPaths.asStateFlow()

    private val _selectedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedVideoIds: StateFlow<Set<String>> = _selectedVideoIds.asStateFlow()

    private var cachedVideosRef: List<Video>? = null
    private var cachedTree: FolderTree = FolderTreeManager.buildTree(emptyList())

    /**
     * Resolves the current FolderUiState given the latest media library videos.
     */
    fun computeUiState(videos: List<Video>): FolderUiState {
        if (videos !== cachedVideosRef) {
            cachedVideosRef = videos
            cachedTree = FolderTreeManager.buildTree(videos)
        }

        val path = _currentPath.value
        val norm = path?.let { FolderTreeManager.normalizePath(it) } ?: FolderTreeManager.INTERNAL_STORAGE_ROOT
        val isRoot = norm == FolderTreeManager.INTERNAL_STORAGE_ROOT || path == null

        val subfolders = FolderTreeManager.getSubfolders(cachedTree, path)
        val directVideos = FolderTreeManager.getDirectVideos(cachedTree, path)
        val previews = FolderTreeManager.getSubfolderPreviews(cachedTree, path)
        val breadcrumbs = FolderTreeManager.getBreadcrumbs(norm)

        val selFolders = _selectedFolderPaths.value
        val selVideos = _selectedVideoIds.value
        val isSelection = if (isRoot) selFolders.isNotEmpty() else (selFolders.isNotEmpty() || selVideos.isNotEmpty())
        val canGoBack = !isRoot && breadcrumbs.size > 1

        val displayName = if (isRoot) {
            "Internal Storage"
        } else {
            FolderTreeManager.formatDirectoryName(norm)
        }

        return FolderUiState(
            currentPath = path,
            normalizedPath = norm,
            currentFolderName = displayName,
            breadcrumbs = breadcrumbs,
            subfolders = subfolders,
            subfolderPreviewVideos = previews,
            directVideos = directVideos,
            selectedFolderPaths = selFolders,
            selectedVideoIds = selVideos,
            isSelectionMode = isSelection,
            canGoBack = canGoBack,
            isEmpty = subfolders.isEmpty() && directVideos.isEmpty()
        )
    }

    fun navigateInto(folder: Folder) {
        clearSelection()
        _currentPath.value = folder.path
    }

    fun navigateToPath(targetPath: String?) {
        clearSelection()
        if (targetPath == null || targetPath == FolderTreeManager.INTERNAL_STORAGE_ROOT || targetPath.isEmpty()) {
            _currentPath.value = null
        } else {
            _currentPath.value = FolderTreeManager.normalizePath(targetPath)
        }
    }

    fun navigateUp(): Boolean {
        if (_selectedFolderPaths.value.isNotEmpty() || _selectedVideoIds.value.isNotEmpty()) {
            clearSelection()
            return true
        }

        val curr = _currentPath.value ?: return false
        val parent = FolderTreeManager.getParentPath(curr)
        if (parent == null || parent == FolderTreeManager.INTERNAL_STORAGE_ROOT) {
            _currentPath.value = null
            return true
        } else {
            _currentPath.value = parent
            return true
        }
    }

    fun toggleFolderSelection(folderPath: String) {
        val current = _selectedFolderPaths.value
        _selectedFolderPaths.value = if (folderPath in current) current - folderPath else current + folderPath
    }

    fun toggleVideoSelection(videoId: String) {
        val current = _selectedVideoIds.value
        _selectedVideoIds.value = if (videoId in current) current - videoId else current + videoId
    }

    fun clearSelection() {
        _selectedFolderPaths.value = emptySet()
        _selectedVideoIds.value = emptySet()
    }

    fun selectAll(subfolders: List<Folder>, videos: List<Video>) {
        val norm = _currentPath.value?.let { FolderTreeManager.normalizePath(it) }
        val isRoot = norm == null || norm == FolderTreeManager.INTERNAL_STORAGE_ROOT
        if (isRoot) {
            _selectedFolderPaths.value = subfolders.map { it.path }.toSet()
        } else {
            _selectedFolderPaths.value = subfolders.map { it.path }.toSet()
            _selectedVideoIds.value = videos.map { it.id }.toSet()
        }
    }

    /**
     * Resolves all videos recursively within the specified folder paths using the cached tree.
     */
    fun resolveDescendantVideos(folderPaths: Collection<String>): List<Video> {
        return folderPaths.flatMap { path ->
            FolderTreeManager.getAllDescendantVideos(cachedTree, path)
        }.distinctBy { it.id }
    }
}
