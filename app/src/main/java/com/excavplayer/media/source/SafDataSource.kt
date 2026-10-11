package com.excavplayer.media.source

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.dao.MediaSourceDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.data.database.mapper.toEntity
import com.excavplayer.data.media.MediaStoreDataSource
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaSourceDao: MediaSourceDao,
    private val videoDao: VideoDao,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "SafDataSource"
    }

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun ensureVideoSaved(video: Video) = withContext(dispatchers.io) {
        runCatching {
            videoDao.insertVideo(video.toEntity())
        }
    }

    suspend fun registerDocumentUri(uri: Uri): VideoEntity? = withContext(dispatchers.io) {
        try {
            val uriStr = uri.toString()

            // 0. Check if this exact URI already exists in database
            videoDao.getVideoByUri(uriStr)?.let { existing ->
                logger.i(TAG, "Video with URI $uriStr already exists in library with ID ${existing.id}")
                return@withContext existing
            }

            // 1. If it's a native MediaStore URI (e.g. content://media/external/video/media/12345)
            if (uri.scheme == "content" && (uri.authority == "media" || uri.authority?.startsWith("com.android.providers.media") == true && !DocumentsContract.isDocumentUri(context, uri))) {
                val mediaStoreId = runCatching { ContentUris.parseId(uri) }.getOrNull()
                if (mediaStoreId != null) {
                    videoDao.getVideoById("ms_$mediaStoreId")?.video?.let { return@withContext it }
                    val entity = mediaStoreDataSource.queryVideoById(mediaStoreId)
                    if (entity != null) {
                        videoDao.insertVideo(entity)
                        logger.i(TAG, "Resolved native MediaStore URI $uri to video: ${entity.displayName}")
                        return@withContext entity
                    }
                }
            }

            // 2. If it's a DocumentsContract document URI from a file manager or system picker
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val authority = uri.authority
                if (authority == "com.android.providers.media.documents") {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val id = docId.substringAfter("video:").toLongOrNull() ?: docId.toLongOrNull()
                    if (id != null) {
                        videoDao.getVideoById("ms_$id")?.video?.let { return@withContext it }
                        val mediaStoreUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                        videoDao.getVideoByUri(mediaStoreUri)?.let { return@withContext it }
                        val entity = mediaStoreDataSource.queryVideoById(id)
                        if (entity != null) {
                            videoDao.insertVideo(entity)
                            logger.i(TAG, "Resolved document URI $uri to MediaStore video: ${entity.displayName}")
                            return@withContext entity
                        }
                    }
                } else if (authority == "com.android.externalstorage.documents") {
                    val docId = DocumentsContract.getDocumentId(uri)
                    if (docId.startsWith("primary:")) {
                        val relPath = docId.substringAfter("primary:")
                        val file = java.io.File(android.os.Environment.getExternalStorageDirectory(), relPath)
                        if (file.exists()) {
                            mediaStoreDataSource.queryVideoByDisplayName(file.name, file.length())?.let { entity ->
                                videoDao.insertVideo(entity)
                                logger.i(TAG, "Resolved external storage doc URI $uri to MediaStore video: ${entity.displayName}")
                                return@withContext entity
                            }
                            mediaStoreDataSource.scanAndIndexFile(file.absolutePath)?.let { entity ->
                                videoDao.insertVideo(entity)
                                logger.i(TAG, "Scanned and indexed external storage doc $uri to MediaStore video: ${entity.displayName}")
                                return@withContext entity
                            }
                        }
                    }
                }
            }

            // 3. If it's a file path or file:// URI
            if (uri.scheme == "file" || (uri.scheme == null && uri.path?.startsWith("/") == true)) {
                val filePath = uri.path ?: uriStr.removePrefix("file://")
                val decodedPath = Uri.decode(filePath)
                val canonicalFile = java.io.File(decodedPath)
                val fileUriStr = Uri.fromFile(canonicalFile).toString()

                videoDao.getVideoByUri(fileUriStr)?.let { return@withContext it }

                if (canonicalFile.exists()) {
                    mediaStoreDataSource.queryVideoByDisplayName(canonicalFile.name, canonicalFile.length())?.let { entity ->
                        videoDao.insertVideo(entity)
                        logger.i(TAG, "Resolved file URI $fileUriStr to MediaStore video: ${entity.displayName}")
                        return@withContext entity
                    }
                    mediaStoreDataSource.scanAndIndexFile(canonicalFile.absolutePath)?.let { entity ->
                        videoDao.insertVideo(entity)
                        logger.i(TAG, "Scanned and indexed file $fileUriStr to MediaStore video: ${entity.displayName}")
                        return@withContext entity
                    }
                }
            }

            // 4. For any other content URI (e.g. from 3rd party apps, WhatsApp, FileProvider)
            var displayName = "External_Video"
            var sizeBytes = 0L
            var mimeType = "video/*"

            if (uri.scheme == "content") {
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
                                displayName = cursor.getString(nameIdx)
                            }
                            if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                                sizeBytes = cursor.getLong(sizeIdx)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logger.w(TAG, "Could not query contentResolver for $uri: ${e.message}")
                }
                try {
                    contentResolver.getType(uri)?.let { mimeType = it }
                } catch (_: Throwable) {}

                if (displayName == "External_Video") {
                    displayName = uri.lastPathSegment?.substringAfterLast('/')?.let { Uri.decode(it) } ?: "External_Video"
                }

                // Check if this video is already indexed in MediaStore by displayName and size
                if (displayName.isNotBlank() && displayName != "External_Video") {
                    mediaStoreDataSource.queryVideoByDisplayName(displayName, sizeBytes.takeIf { it > 0 })?.let { entity ->
                        videoDao.insertVideo(entity)
                        logger.i(TAG, "Matched content URI $uri to existing MediaStore video: ${entity.displayName}")
                        return@withContext entity
                    }
                }
            } else {
                displayName = uri.lastPathSegment?.substringAfterLast('/')?.let { Uri.decode(it) } ?: "External_Video"
            }

            takePersistablePermission(uri)

            val stableId = "saf_doc_${UUID.nameUUIDFromBytes(uriStr.toByteArray())}"
            val sourceId = "src_doc_${stableId}"

            runCatching {
                val sourceEntity = MediaSourceEntity(
                    id = sourceId,
                    uri = uriStr,
                    type = MediaSourceType.LOCAL_DOCUMENT.name,
                    name = displayName,
                    isAccessible = true,
                    lastValidatedTimestamp = System.currentTimeMillis()
                )
                mediaSourceDao.insertSource(sourceEntity)
            }

            val videoEntity = VideoEntity(
                id = stableId,
                uri = uriStr,
                displayName = displayName,
                mimeType = mimeType,
                durationMs = 0L, // Duration will be detected when player prepares
                sizeBytes = sizeBytes,
                dateAddedSeconds = System.currentTimeMillis() / 1000,
                dateModifiedSeconds = System.currentTimeMillis() / 1000,
                width = 0,
                height = 0,
                folderName = "External Videos",
                folderPath = "External Videos",
                relativePath = "",
                sourceType = MediaSourceType.LOCAL_DOCUMENT.name,
                availability = MediaAvailability.AVAILABLE.name,
                lastScannedTimestamp = System.currentTimeMillis()
            )

            val inserted = runCatching { videoDao.insertVideo(videoEntity) }
            if (inserted.isFailure) {
                val existing = videoDao.getVideoByUri(uriStr)
                if (existing != null) {
                    return@withContext existing
                }
            }
            logger.i(TAG, "Registered SAF/External document: $displayName ($uriStr)")
            videoEntity
        } catch (e: Throwable) {
            logger.e(TAG, "Failed to register document URI: $uri", e)
            videoDao.getVideoByUri(uri.toString())
        }
    }

    suspend fun registerTreeUri(treeUri: Uri): List<VideoEntity> = withContext(dispatchers.io) {
        takePersistablePermission(treeUri)

        val discovered = mutableListOf<VideoEntity>()
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val sourceId = "src_tree_${UUID.nameUUIDFromBytes(treeUri.toString().toByteArray())}"

        val sourceEntity = MediaSourceEntity(
            id = sourceId,
            uri = treeUri.toString(),
            type = MediaSourceType.LOCAL_TREE.name,
            name = treeUri.lastPathSegment ?: "Selected Folder",
            isAccessible = true,
            lastValidatedTimestamp = System.currentTimeMillis()
        )
        mediaSourceDao.insertSource(sourceEntity)

        scanDocumentTree(treeUri, treeId, "Selected Folder", discovered)

        if (discovered.isNotEmpty()) {
            videoDao.insertVideos(discovered)
            logger.i(TAG, "Registered SAF tree with ${discovered.size} videos from: $treeUri")
        }
        discovered
    }

    private fun scanDocumentTree(
        treeUri: Uri,
        parentDocumentId: String,
        currentFolder: String,
        result: MutableList<VideoEntity>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(childrenUri, projection, null, null, null)
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val docId = c.getString(idIdx)
                    val name = c.getString(nameIdx)
                    val mime = c.getString(mimeIdx)

                    if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                        // Recurse into subdirectory
                        scanDocumentTree(treeUri, docId, "$currentFolder/$name", result)
                    } else if (mime.startsWith("video/") || isVideoExtension(name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val size = if (sizeIdx != -1 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                        val modified = if (modifiedIdx != -1 && !c.isNull(modifiedIdx)) c.getLong(modifiedIdx) / 1000 else 0L
                        val stableId = "saf_tree_${UUID.nameUUIDFromBytes(docUri.toString().toByteArray())}"

                        result.add(
                            VideoEntity(
                                id = stableId,
                                uri = docUri.toString(),
                                displayName = name,
                                mimeType = mime.ifEmpty { "video/mp4" },
                                durationMs = 0L,
                                sizeBytes = size,
                                dateAddedSeconds = System.currentTimeMillis() / 1000,
                                dateModifiedSeconds = modified,
                                width = 0,
                                height = 0,
                                folderName = currentFolder.substringAfterLast('/'),
                                folderPath = currentFolder,
                                relativePath = currentFolder,
                                sourceType = MediaSourceType.LOCAL_TREE.name,
                                availability = MediaAvailability.AVAILABLE.name,
                                lastScannedTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.w(TAG, "Error scanning document tree node: $parentDocumentId - ${e.message}")
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        if (uri.scheme != "content") return
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            logger.d(TAG, "Taken persistable URI permission for: $uri")
        } catch (e: Throwable) {
            logger.w(TAG, "Could not take persistable URI permission for $uri: ${e.message}")
        }
    }

    private fun isVideoExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp")
    }
}
