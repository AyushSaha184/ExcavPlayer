package com.excavplayer.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtitle_preferences"
)
data class SubtitlePreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,

    @ColumnInfo(name = "preferred_subtitle_track_id")
    val preferredSubtitleTrackId: String? = null,

    @ColumnInfo(name = "subtitle_delay_ms")
    val subtitleDelayMs: Long = 0L,

    @ColumnInfo(name = "external_subtitle_uri")
    val externalSubtitleUri: String? = null
)

@Entity(
    tableName = "media_sources",
    indices = [
        Index(value = ["uri"], unique = true)
    ]
)
data class MediaSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_accessible")
    val isAccessible: Boolean = true,

    @ColumnInfo(name = "last_validated_timestamp")
    val lastValidatedTimestamp: Long = System.currentTimeMillis()
)
