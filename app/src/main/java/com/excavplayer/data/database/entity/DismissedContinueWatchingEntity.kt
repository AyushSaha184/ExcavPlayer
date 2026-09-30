package com.excavplayer.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "dismissed_continue_watching",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DismissedContinueWatchingEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String
)
