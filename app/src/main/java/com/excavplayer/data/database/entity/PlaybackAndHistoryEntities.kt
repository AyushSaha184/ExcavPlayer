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

