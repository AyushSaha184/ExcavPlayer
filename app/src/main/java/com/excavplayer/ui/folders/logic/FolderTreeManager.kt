package com.excavplayer.ui.folders.logic

import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.NaturalVideoComparator
import com.excavplayer.domain.model.Video

/**
 * Breadcrumb element representing a step in directory path traversal.
 */
data class BreadcrumbItem(
    val title: String,
    val path: String
)

/**
 * In-memory node representing a directory in the true folder tree hierarchy.
 */
data class FolderTreeNode(
    val path: String,
    val name: String,
    val parentPath: String?,
    val directVideos: List<Video> = emptyList(),
    val subfolders: Map<String, FolderTreeNode> = emptyMap(),
    val recursiveVideoCount: Int = 0,
    val recursiveSizeBytes: Long = 0L,
    val previewVideos: List<Video> = emptyList()
) {
    fun toFolder(): Folder {
        return Folder(
            name = name,
            path = path,
            videoCount = recursiveVideoCount,
            totalSizeBytes = recursiveSizeBytes
        )
    }
}

/**
 * Immutable tree data structure holding all indexed video directory nodes.
 */
data class FolderTree(
    val rootVolumes: List<FolderTreeNode>,
    val nodesByPath: Map<String, FolderTreeNode>
)

object FolderTreeManager {

    const val INTERNAL_STORAGE_ROOT = "/storage/emulated/0"
    private const val INTERNAL_STORAGE_LABEL = "Internal Storage"

    /**
     * Normalizes directory and file paths to canonical absolute Unix paths.
     */
    fun normalizePath(raw: String?): String {
        val trimmed = raw.orEmpty().trim().trimEnd('/')
        return when {
            trimmed.isEmpty() ||
            trimmed == INTERNAL_STORAGE_ROOT ||
            trimmed == "/storage/emulated" ||
            trimmed.equals(INTERNAL_STORAGE_LABEL, ignoreCase = true) -> INTERNAL_STORAGE_ROOT
            trimmed.startsWith(INTERNAL_STORAGE_ROOT) -> trimmed
            trimmed.startsWith("/storage/") -> trimmed
            trimmed.startsWith("/") -> trimmed
            else -> "$INTERNAL_STORAGE_ROOT/$trimmed"
        }
    }

    /**
     * Builds a full directory tree hierarchy from a flat list of videos.
     */
    fun buildTree(videos: List<Video>): FolderTree {
        if (videos.isEmpty()) {
            val emptyRoot = FolderTreeNode(
                path = INTERNAL_STORAGE_ROOT,
                name = INTERNAL_STORAGE_LABEL,
                parentPath = null
            )
            return FolderTree(
                rootVolumes = listOf(emptyRoot),
                nodesByPath = mapOf(INTERNAL_STORAGE_ROOT to emptyRoot)
            )
        }

        // Group videos by their normalized immediate folder path
        val directVideosByFolder = mutableMapOf<String, MutableList<Video>>()
        for (v in videos) {
            val normFolder = normalizePath(v.folderPath)
            directVideosByFolder.getOrPut(normFolder) { mutableListOf() }.add(v)
        }

        // Collect all distinct folder paths and determine storage root prefixes
        val allFolders = directVideosByFolder.keys.toSet()
        val allPaths = mutableSetOf<String>()

        for (folderPath in allFolders) {
            var curr: String? = folderPath
            while (curr != null && curr.isNotEmpty()) {
                allPaths.add(curr)
                val parent = getParentPath(curr)
                if (parent == curr) break
                curr = parent
            }
        }

        // Ensure default internal storage root is tracked
        allPaths.add(INTERNAL_STORAGE_ROOT)

        // Identify storage roots (e.g. /storage/emulated/0 or /storage/ABCD-1234)
        val storageRoots = allPaths.filter { isStorageRoot(it) }.toSet().ifEmpty {
            setOf(INTERNAL_STORAGE_ROOT)
        }

        // Build nodes bottom-up (sorted by path length descending so children resolve before parents)
        val sortedPaths = allPaths.sortedByDescending { it.length }
        val nodesMap = mutableMapOf<String, FolderTreeNode>()

        for (path in sortedPaths) {
            val directVids = (directVideosByFolder[path] ?: emptyList()).sortedWith(NaturalVideoComparator)
            val parentPath = getParentPath(path)

            // Collect direct subfolder nodes already processed
            val directSubNodes = nodesMap.values.filter { it.parentPath == path }
                .associateBy { it.path }

            val recursiveCount = directVids.size + directSubNodes.values.sumOf { it.recursiveVideoCount }
            val recursiveSize = directVids.sumOf { it.sizeBytes } + directSubNodes.values.sumOf { it.recursiveSizeBytes }

            // Take up to 4 previews (direct videos first, followed by subfolder previews)
            val previews = mutableListOf<Video>()
            previews.addAll(directVids.take(4))
            if (previews.size < 4) {
                for (subNode in directSubNodes.values) {
                    for (pv in subNode.previewVideos) {
                        if (previews.none { it.id == pv.id }) {
                            previews.add(pv)
                            if (previews.size >= 4) break
                        }
                    }
                    if (previews.size >= 4) break
                }
            }

            val displayName = formatDirectoryName(path)

            nodesMap[path] = FolderTreeNode(
                path = path,
                name = displayName,
                parentPath = parentPath,
                directVideos = directVids,
                subfolders = directSubNodes,
                recursiveVideoCount = recursiveCount,
                recursiveSizeBytes = recursiveSize,
                previewVideos = previews
            )
        }

        // Determine top root nodes (storage volumes)
        val rootVolumes = storageRoots.mapNotNull { nodesMap[it] }.ifEmpty {
            nodesMap[INTERNAL_STORAGE_ROOT]?.let { listOf(it) } ?: emptyList()
        }

        return FolderTree(
            rootVolumes = rootVolumes,
            nodesByPath = nodesMap
        )
    }

    /**
     * Determines whether the given normalized path represents a top-level storage root.
     */
    fun isStorageRoot(path: String): Boolean {
        if (path == INTERNAL_STORAGE_ROOT || path == "/storage/emulated") return true
        if (path.startsWith("/storage/")) {
            val rem = path.removePrefix("/storage/").trimStart('/')
            return rem.isNotEmpty() && !rem.contains('/')
        }
        return path == "/"
    }

    /**
     * Computes the immediate parent directory path, or null if already at or above a storage root.
     */
    fun getParentPath(path: String): String? {
        val norm = normalizePath(path)
        if (isStorageRoot(norm)) return null

        val lastSlash = norm.lastIndexOf('/')
        if (lastSlash <= 0) return null
        val parent = norm.substring(0, lastSlash)
        return when {
            parent == "/storage/emulated" -> INTERNAL_STORAGE_ROOT
            parent.isEmpty() -> null
            else -> parent
        }
    }

    /**
     * Generates a human-friendly display name for a directory path.
     */
    fun formatDirectoryName(path: String): String {
        val norm = normalizePath(path)
        return when {
            norm == INTERNAL_STORAGE_ROOT -> INTERNAL_STORAGE_LABEL
            norm.startsWith("/storage/") && isStorageRoot(norm) -> {
                val cardId = norm.removePrefix("/storage/").trim('/')
                if (cardId.isEmpty()) "Storage" else "SD Card ($cardId)"
            }
            norm.contains('/') -> norm.substringAfterLast('/')
            else -> norm
        }
    }

    /**
     * Generates breadcrumbs from storage root to the target directory path.
     */
    fun getBreadcrumbs(targetPath: String?): List<BreadcrumbItem> {
        val norm = normalizePath(targetPath)
        val crumbs = mutableListOf<BreadcrumbItem>()

        if (norm.startsWith(INTERNAL_STORAGE_ROOT)) {
            crumbs.add(BreadcrumbItem(INTERNAL_STORAGE_LABEL, INTERNAL_STORAGE_ROOT))
            val sub = norm.removePrefix(INTERNAL_STORAGE_ROOT).trimStart('/')
            if (sub.isNotEmpty()) {
                val segments = sub.split('/').filter { it.isNotEmpty() }
                var accumulated = INTERNAL_STORAGE_ROOT
                for (seg in segments) {
                    accumulated = "$accumulated/$seg"
                    crumbs.add(BreadcrumbItem(seg, accumulated))
                }
            }
        } else if (norm.startsWith("/storage/")) {
            val sub = norm.removePrefix("/storage/").trimStart('/')
            val segments = sub.split('/').filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                val cardId = segments[0]
                val rootPath = "/storage/$cardId"
                crumbs.add(BreadcrumbItem("SD Card", rootPath))
                var accumulated = rootPath
                for (i in 1 until segments.size) {
                    accumulated = "$accumulated/${segments[i]}"
                    crumbs.add(BreadcrumbItem(segments[i], accumulated))
                }
            } else {
                crumbs.add(BreadcrumbItem("Storage", norm))
            }
        } else {
            val segments = norm.split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            for (seg in segments) {
                accumulated = "$accumulated/$seg"
                crumbs.add(BreadcrumbItem(seg, accumulated))
            }
        }

        return crumbs
    }

    /**
     * Returns direct child folders for the given current directory path.
     * When currentPath is null or a storage root:
     * - If single volume, returns its immediate subdirectories that contain videos.
     * - If multiple volumes, returns the storage volumes.
     */
    fun getSubfolders(tree: FolderTree, currentPath: String?): List<Folder> {
        val normCurrent = currentPath?.let { normalizePath(it) }

        if (normCurrent == null || normCurrent == INTERNAL_STORAGE_ROOT) {
            // Root view
            if (tree.rootVolumes.size > 1 && normCurrent == null) {
                return tree.rootVolumes.map { it.toFolder() }
            }
            val primaryRoot = tree.nodesByPath[INTERNAL_STORAGE_ROOT]
                ?: tree.rootVolumes.firstOrNull()
                ?: return emptyList()

            return primaryRoot.subfolders.values
                .filter { it.recursiveVideoCount > 0 }
                .map { it.toFolder() }
                .sortedBy { it.name.lowercase() }
        }

        val node = tree.nodesByPath[normCurrent] ?: return emptyList()
        return node.subfolders.values
            .filter { it.recursiveVideoCount > 0 }
            .map { it.toFolder() }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Returns direct videos located strictly inside the current directory path.
     */
    fun getDirectVideos(tree: FolderTree, currentPath: String?): List<Video> {
        val normCurrent = currentPath?.let { normalizePath(it) } ?: INTERNAL_STORAGE_ROOT
        val node = tree.nodesByPath[normCurrent] ?: return emptyList()
        return node.directVideos
    }

    /**
     * Returns map of preview videos (up to 4) for each direct subfolder.
     */
    fun getSubfolderPreviews(tree: FolderTree, currentPath: String?): Map<String, List<Video>> {
        val normCurrent = currentPath?.let { normalizePath(it) }

        val subfolderNodes = if (normCurrent == null || normCurrent == INTERNAL_STORAGE_ROOT) {
            val primaryRoot = tree.nodesByPath[INTERNAL_STORAGE_ROOT]
                ?: tree.rootVolumes.firstOrNull()
            primaryRoot?.subfolders?.values ?: emptyList()
        } else {
            tree.nodesByPath[normCurrent]?.subfolders?.values ?: emptyList()
        }

        return subfolderNodes.associate { it.path to it.previewVideos }
    }

    /**
     * Recursively retrieves all videos within a directory path and its descendants.
     */
    fun getAllDescendantVideos(tree: FolderTree, targetFolderPath: String): List<Video> {
        val normTarget = normalizePath(targetFolderPath)
        val node = tree.nodesByPath[normTarget]
        if (node != null) {
            val result = mutableListOf<Video>()
            fun collect(n: FolderTreeNode) {
                result.addAll(n.directVideos)
                for (sub in n.subfolders.values) {
                    collect(sub)
                }
            }
            collect(node)
            return result
        }

        // Fallback: match any node whose path equals or starts with targetFolderPath/
        return tree.nodesByPath.values
            .filter { it.path == normTarget || it.path.startsWith("$normTarget/") }
            .flatMap { it.directVideos }
    }
}
