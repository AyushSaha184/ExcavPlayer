package com.excavplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.excavplayer.data.database.entity.EqualizerPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EqualizerPreferenceDao {
    @Query("SELECT * FROM equalizer_preferences WHERE video_id = :videoId")
    suspend fun getPreferences(videoId: String): EqualizerPreferenceEntity?

    @Query("SELECT * FROM equalizer_preferences WHERE video_id = :videoId")
    fun observePreferences(videoId: String): Flow<EqualizerPreferenceEntity?>

    @Upsert
    suspend fun upsertPreferences(entity: EqualizerPreferenceEntity)

    @Query("DELETE FROM equalizer_preferences WHERE video_id = :videoId")
    suspend fun deletePreferences(videoId: String)
}
