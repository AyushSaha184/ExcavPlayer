package com.excavplayer.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equalizer_preferences")
data class EqualizerPreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = false,

    @ColumnInfo(name = "preset")
    val preset: String = "Custom",

    @ColumnInfo(name = "band_levels")
    val bandLevels: String = "0,0,0,0,0",

    @ColumnInfo(name = "bass_boost_strength")
    val bassBoostStrength: Int = 0
)
