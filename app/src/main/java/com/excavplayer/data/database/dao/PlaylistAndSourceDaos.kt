package com.excavplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.SubtitlePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaSourceDao {
    @Query("SELECT * FROM media_sources ORDER BY last_validated_timestamp DESC")
    fun observeSources(): Flow<List<MediaSourceEntity>>

    @Query("SELECT * FROM media_sources")
    suspend fun getAllSources(): List<MediaSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: MediaSourceEntity)

    @Query("DELETE FROM media_sources WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: String)

    @Query("UPDATE media_sources SET is_accessible = :isAccessible, last_validated_timestamp = :timestamp WHERE id = :sourceId")
    suspend fun updateAccessibility(sourceId: String, isAccessible: Boolean, timestamp: Long)
}

@Dao
interface SubtitlePreferenceDao {
    @Query("SELECT * FROM subtitle_preferences WHERE video_id = :videoId")
    suspend fun getPreferences(videoId: String): SubtitlePreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(entity: SubtitlePreferenceEntity)
}
