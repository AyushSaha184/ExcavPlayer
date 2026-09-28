package com.excavplayer.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    companion object {
        private const val TAG = "MediaStoreDataSource"
        val VIDEO_COLLECTION_URI: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun getProjection(): Array<String> {
        val baseProjection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            baseProjection.add(MediaStore.Video.Media.RELATIVE_PATH)
            baseProjection.add(MediaStore.Video.Media.ORIENTATION)
            baseProjection.add(MediaStore.Video.Media.BITRATE)
        } else {
            @Suppress("DEPRECATION")
            baseProjection.add(MediaStore.Video.Media.DATA)
        }

        return baseProjection.toTypedArray()
    }

    suspend fun queryAllVideosPaged(
        batchSize: Int = 200,
        onBatch: suspend (List<VideoEntity>) -> Unit
    ): Int = withContext(dispatchers.io) {
        var totalCount = 0
        var offset = 0
        var hasMore = true

        val projection = getProjection()
        val selection = "${MediaStore.Video.Media.SIZE} > 0"
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        while (hasMore) {
            val cursor: Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, batchSize)
                    putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Video.Media.DATE_MODIFIED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                }
                contentResolver.query(VIDEO_COLLECTION_URI, projection, queryArgs, null)
            } else {
                val pagedSortOrder = "$sortOrder LIMIT $batchSize OFFSET $offset"
                contentResolver.query(VIDEO_COLLECTION_URI, projection, selection, null, pagedSortOrder)
            }

            cursor?.use { c ->
                val batch = parseCursorBatch(c)
                if (batch.isNotEmpty()) {
                    onBatch(batch)
                    totalCount += batch.size
                    offset += batch.size
                    hasMore = batch.size == batchSize
                } else {
                    hasMore = false
                }
            } ?: run {
                hasMore = false
            }
        }

        logger.d(TAG, "Queried $totalCount videos from MediaStore")
        totalCount
    }

    private fun parseCursorBatch(cursor: Cursor): List<VideoEntity> {
        val videos = mutableListOf<VideoEntity>()

        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
        val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
        val dateAddedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
        val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
        val bucketNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

        val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
        } else -1

        val orientationColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.Video.Media.ORIENTATION)
        } else -1

        val bitrateColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.Video.Media.BITRATE)
        } else -1

        val dataColumn = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            cursor.getColumnIndex(MediaStore.Video.Media.DATA)
        } else -1

        while (cursor.moveToNext()) {
            val mediaStoreId = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, mediaStoreId).toString()
            val stableId = "ms_$mediaStoreId"

            val displayName = if (nameColumn != -1 && !cursor.isNull(nameColumn)) {
                cursor.getString(nameColumn)
            } else "Video_$mediaStoreId"

            val mimeType = if (mimeColumn != -1 && !cursor.isNull(mimeColumn)) {
                cursor.getString(mimeColumn)
            } else "video/mp4"

            val durationMs = if (durationColumn != -1 && !cursor.isNull(durationColumn)) {
                cursor.getLong(durationColumn)
            } else 0L

            val sizeBytes = if (sizeColumn != -1 && !cursor.isNull(sizeColumn)) {
                cursor.getLong(sizeColumn)
            } else 0L

            val dateAdded = if (dateAddedColumn != -1 && !cursor.isNull(dateAddedColumn)) {
                cursor.getLong(dateAddedColumn)
            } else 0L

            val dateModified = if (dateModifiedColumn != -1 && !cursor.isNull(dateModifiedColumn)) {
                cursor.getLong(dateModifiedColumn)
            } else 0L

            val width = if (widthColumn != -1 && !cursor.isNull(widthColumn)) {
                cursor.getInt(widthColumn)
            } else 0

            val height = if (heightColumn != -1 && !cursor.isNull(heightColumn)) {
                cursor.getInt(heightColumn)
            } else 0

            val orientation = if (orientationColumn != -1 && !cursor.isNull(orientationColumn)) {
                cursor.getInt(orientationColumn)
            } else 0

            val bitrate = if (bitrateColumn != -1 && !cursor.isNull(bitrateColumn)) {
                cursor.getLong(bitrateColumn)
            } else null

            var relativePath = ""
            var folderName = ""
            var folderPath = ""

            if (relativePathColumn != -1 && !cursor.isNull(relativePathColumn)) {
                relativePath = cursor.getString(relativePathColumn) ?: ""
                folderPath = relativePath.trimEnd('/')
                folderName = folderPath.substringAfterLast('/', folderPath)
            } else if (dataColumn != -1 && !cursor.isNull(dataColumn)) {
                val data = cursor.getString(dataColumn) ?: ""
                val parentFile = File(data).parentFile
                if (parentFile != null) {
                    folderPath = parentFile.absolutePath
                    folderName = parentFile.name
                }
            }

            if (folderName.isEmpty() && bucketNameColumn != -1 && !cursor.isNull(bucketNameColumn)) {
                folderName = cursor.getString(bucketNameColumn) ?: "Internal"
                if (folderPath.isEmpty()) folderPath = folderName
            }

            videos.add(
                VideoEntity(
                    id = stableId,
                    uri = contentUri,
                    displayName = displayName,
                    mimeType = mimeType,
                    durationMs = durationMs,
                    sizeBytes = sizeBytes,
                    dateAddedSeconds = dateAdded,
                    dateModifiedSeconds = dateModified,
                    width = width,
                    height = height,
                    bitrate = bitrate,
                    frameRate = null,
                    orientation = orientation,
                    folderName = folderName.ifEmpty { "Videos" },
                    folderPath = folderPath.ifEmpty { "Videos" },
                    relativePath = relativePath,
                    sourceType = MediaSourceType.LOCAL_MEDIASTORE.name,
                    availability = MediaAvailability.AVAILABLE.name,
                    lastScannedTimestamp = System.currentTimeMillis()
                )
            )
        }

        return videos
    }
}
