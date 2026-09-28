package com.excavplayer.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.excavplayer.data.database.entity.PlaylistItemEntity
import com.excavplayer.data.database.entity.VideoEntity

data class FolderTuple(
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "video_count") val videoCount: Int,
    @ColumnInfo(name = "total_size_bytes") val totalSizeBytes: Long
)

data class VideoWithMetadataTuple(
    @Embedded val video: VideoEntity,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "resume_position_ms") val resumePositionMs: Long?
)

data class PlaylistItemWithVideoTuple(
    @Embedded val item: PlaylistItemEntity,
    @Embedded(prefix = "v_") val video: VideoEntity?
)

data class PlaylistWithCountTuple(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "item_count") val itemCount: Int
)
