package com.excavplayer.media.source

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.dao.MediaSourceDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
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
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "SafDataSource"
    }

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun registerDocumentUri(uri: Uri): VideoEntity? = withContext(dispatchers.io) {
        takePersistablePermission(uri)

        var displayName = "Document_Video"
        var sizeBytes = 0L
        var mimeType = "video/*"

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
            contentResolver.getType(uri)?.let { mimeType = it }

            val stableId = "saf_doc_${UUID.nameUUIDFromBytes(uri.toString().toByteArray())}"
            val sourceId = "src_doc_${stableId}"

            val sourceEntity = MediaSourceEntity(
                id = sourceId,
                uri = uri.toString(),
                type = MediaSourceType.LOCAL_DOCUMENT.name,
                name = displayName,
                isAccessible = true,
                lastValidatedTimestamp = System.currentTimeMillis()
            )
            mediaSourceDao.insertSource(sourceEntity)

            val videoEntity = VideoEntity(
                id = stableId,
                uri = uri.toString(),
                displayName = displayName,
                mimeType = mimeType,
                durationMs = 0L, // Duration will be detected when player prepares
                sizeBytes = sizeBytes,
                dateAddedSeconds = System.currentTimeMillis() / 1000,
                dateModifiedSeconds = System.currentTimeMillis() / 1000,
                width = 0,
                height = 0,
                folderName = "SAF Files",
                folderPath = "SAF Files",
                relativePath = "",
                sourceType = MediaSourceType.LOCAL_DOCUMENT.name,
                availability = MediaAvailability.AVAILABLE.name,
                lastScannedTimestamp = System.currentTimeMillis()
            )

            videoDao.insertVideo(videoEntity)
            logger.i(TAG, "Registered SAF document: $displayName ($uri)")
            videoEntity
        } catch (e: Exception) {
            logger.e(TAG, "Failed to register document URI: $uri", e)
            null
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
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            logger.d(TAG, "Taken persistable URI permission for: $uri")
        } catch (e: SecurityException) {
            logger.w(TAG, "Could not take persistable URI permission for $uri: ${e.message}")
        }
    }

    private fun isVideoExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp")
    }
}
