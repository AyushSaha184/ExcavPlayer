package com.excavplayer.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["folder_path"]),
        Index(value = ["date_added"]),
        Index(value = ["display_name"])
    ]
)
data class VideoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "date_added")
    val dateAddedSeconds: Long,

    @ColumnInfo(name = "date_modified")
    val dateModifiedSeconds: Long,

    @ColumnInfo(name = "width")
    val width: Int,

    @ColumnInfo(name = "height")
    val height: Int,

    @ColumnInfo(name = "bitrate")
    val bitrate: Long? = null,

    @ColumnInfo(name = "frame_rate")
    val frameRate: Float? = null,

    @ColumnInfo(name = "orientation")
    val orientation: Int = 0,

    @ColumnInfo(name = "folder_name")
    val folderName: String = "",

    @ColumnInfo(name = "folder_path")
    val folderPath: String = "",

    @ColumnInfo(name = "relative_path")
    val relativePath: String = "",

    @ColumnInfo(name = "source_type")
    val sourceType: String = "LOCAL_MEDIASTORE",

    @ColumnInfo(name = "availability")
    val availability: String = "AVAILABLE",

    @ColumnInfo(name = "last_scanned_timestamp")
    val lastScannedTimestamp: Long = System.currentTimeMillis()
)
