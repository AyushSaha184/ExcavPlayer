package com.excavplayer.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_states",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["last_updated_timestamp"])
    ]
)
data class PlaybackEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,

    @ColumnInfo(name = "current_position_ms")
    val currentPositionMs: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "buffered_position_ms")
    val bufferedPositionMs: Long = 0L,

    @ColumnInfo(name = "playback_status")
    val playbackStatus: String = "IDLE",

    @ColumnInfo(name = "playback_speed")
    val playbackSpeed: Float = 1.0f,

    @ColumnInfo(name = "volume")
    val volume: Float = 1.0f,

    @ColumnInfo(name = "repeat_mode")
    val repeatMode: String = "OFF",

    @ColumnInfo(name = "is_shuffle_enabled")
    val isShuffleEnabled: Boolean = false,

    @ColumnInfo(name = "selected_audio_track_id")
    val selectedAudioTrackId: String? = null,

    @ColumnInfo(name = "selected_subtitle_track_id")
    val selectedSubtitleTrackId: String? = null,

    @ColumnInfo(name = "selected_video_track_id")
    val selectedVideoTrackId: String? = null,

    @ColumnInfo(name = "last_updated_timestamp")
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "watch_history",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["video_id"], unique = true),
        Index(value = ["last_played_timestamp"]),
        Index(value = ["is_completed"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "video_id")
    val videoId: String,

    @ColumnInfo(name = "first_played_timestamp")
    val firstPlayedTimestamp: Long,

    @ColumnInfo(name = "last_played_timestamp")
    val lastPlayedTimestamp: Long,

    @ColumnInfo(name = "total_watch_duration_ms")
    val totalWatchDurationMs: Long,

    @ColumnInfo(name = "completion_percentage")
    val completionPercentage: Float,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "last_position_ms")
    val lastPositionMs: Long
)
