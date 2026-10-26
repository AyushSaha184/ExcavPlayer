package com.excavplayer.media.discovery

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.domain.derivation.LibraryDerivation
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageFileScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "StorageFileScanner"
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv",
            "3gp", "ts", "m4v", "vob", "ogv", "f4v", "rmvb", "asf",
            "m2ts", "mts", "divx", "mpg", "mpeg"
        )
        private val EXCLUDED_DIR_NAMES = setOf(
            "data", "obb", ".thumbnails", ".trashed", ".trash", "cache",
            "self", "knox", "enc_media", "lost.dir",
            // OEM-specific trash/recycle folders (Xiaomi, Samsung, Huawei, Oppo, Vivo, etc.)
            ".trashbin", "trashbin", ".recycle", "recycle",
            ".recycler", "recycler", ".securetrash",
            "recently_deleted", ".recently_deleted",
            "trash", ".gallery_trash", "gallery_trash"
        )
        private val SD_CARD_PATTERN = Regex("""^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$""")
    }

    fun getStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        try {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists()) {
                roots.add(primary)
            }
        } catch (_: Exception) {}

        try {
            val fallbackPrimary = File("/storage/emulated/0")
            if (fallbackPrimary.exists() && !roots.contains(fallbackPrimary)) {
                roots.add(fallbackPrimary)
            }
        } catch (_: Exception) {}

        try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in externalDirs) {
                if (dir != null) {
                    var parent = dir.parentFile
                    while (parent != null && parent.name != "Android") {
                        parent = parent.parentFile
                    }
                    val root = parent?.parentFile
                    if (root != null && root.exists() && !roots.contains(root)) {
                        val name = root.name.lowercase()
                        if (name !in EXCLUDED_DIR_NAMES && !name.startsWith(".")) {
                            roots.add(root)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val storageDir = File("/storage")
            if (storageDir.exists()) {
                val children = storageDir.listFiles()
                if (children != null) {
                    for (c in children) {
                        val name = c.name
                        val lower = name.lowercase()
                        if (c.isDirectory && !roots.contains(c)) {
                            if (lower !in setOf("emulated", "self", "knox", "private", "lost.dir", "enc_media") && !name.startsWith(".")) {
                                roots.add(c)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return roots
    }

    /**
     * Recursively scans storage roots for video files missing from the database.
     * @param existingKeys Set of "filename:filesize" (lowercase) representing videos already indexed.
     */
    fun scanForUnindexedVideos(existingKeys: Set<String>): List<VideoEntity> {
        val discovered = mutableListOf<VideoEntity>()
        val roots = getStorageRoots()
        logger.i(TAG, "Scanning ${roots.size} storage roots for unindexed video files")

        for (root in roots) {
            scanDirectory(root, existingKeys, discovered)
        }

        if (discovered.isNotEmpty()) {
            logger.i(TAG, "Discovered ${discovered.size} unindexed video files on storage")
            try {
                val paths = discovered.mapNotNull {
                    val u = Uri.parse(it.uri)
                    if (u.scheme == "file") u.path else null
                }.toTypedArray()
                if (paths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, paths, null, null)
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed to notify MediaScannerConnection: ${e.message}")
            }
        }

        return discovered
    }

    private fun scanDirectory(
        dir: File,
        existingKeys: Set<String>,
        outList: MutableList<VideoEntity>,
        depth: Int = 0
    ) {
        if (depth > 30) return
        val dirNameLower = dir.name.lowercase()
        if (dirNameLower in EXCLUDED_DIR_NAMES || dir.name.startsWith(".")) {
            return
        }
        // Full library exclusion rule for directory paths (private storage, trash,
        // SAF Files, etc.) — single owner: LibraryDerivation. Traversal pruning above
        // (EXCLUDED_DIR_NAMES) is scan-time concern and stays local.
        if (LibraryDerivation.isExcludedFolderPath(dir.absolutePath)) {
            return
        }

        val files = try {
            dir.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return

        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, existingKeys, outList, depth + 1)
            } else if (file.isFile && file.length() > 0) {
                val ext = file.extension.lowercase()
                if (ext in VIDEO_EXTENSIONS) {
                    val key = "${file.name.lowercase()}:${file.length()}"
                    if (!existingKeys.contains(key)) {
                        val entity = createVideoEntity(file)
                        outList.add(entity)
                    }
                }
            }
        }
    }

    private fun createVideoEntity(file: File): VideoEntity {
        val stableId = "fs_${UUID.nameUUIDFromBytes(file.absolutePath.toByteArray())}"
        val uri = Uri.fromFile(file).toString()
        val parent = file.parentFile
        val folderPath = parent?.absolutePath ?: "/storage/emulated/0"
        val folderName = parent?.name ?: "Internal Storage"
        val relativePath = folderPath.removePrefix("/storage/emulated/0/").removePrefix("/storage/emulated/0").trimStart('/')
        val mime = getMimeType(file.extension)

        return VideoEntity(
            id = stableId,
            uri = uri,
            displayName = file.name,
            mimeType = mime,
            durationMs = 0L,
            sizeBytes = file.length(),
            dateAddedSeconds = file.lastModified() / 1000,
            dateModifiedSeconds = file.lastModified() / 1000,
            width = 0,
            height = 0,
            bitrate = null,
            frameRate = null,
            orientation = 0,
            folderName = folderName,
            folderPath = folderPath,
            relativePath = relativePath,
            sourceType = MediaSourceType.LOCAL_MEDIASTORE.name,
            availability = MediaAvailability.AVAILABLE.name,
            lastScannedTimestamp = System.currentTimeMillis()
        )
    }

    private fun getMimeType(extension: String): String {
        val ext = extension.lowercase()
        return when (ext) {
            "mkv" -> "video/x-matroska"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/*"
        }
    }
}
