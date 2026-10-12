package com.excavplayer.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.excavplayer.data.database.entity.VideoEntity

data class FolderTuple(
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "video_count") val videoCount: Int,
    @ColumnInfo(name = "total_size_bytes") val totalSizeBytes: Long
)

data class VideoWithMetadataTuple(
    @Embedded val video: VideoEntity,
    @ColumnInfo(name = "resume_position_ms") val resumePositionMs: Long?
)

data class IdUriTuple(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "uri") val uri: String
)
