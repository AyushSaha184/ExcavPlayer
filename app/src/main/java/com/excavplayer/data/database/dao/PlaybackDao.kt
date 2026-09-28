package com.excavplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.excavplayer.data.database.entity.PlaybackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_states WHERE video_id = :videoId")
    fun observePlaybackState(videoId: String): Flow<PlaybackEntity?>

    @Query("SELECT * FROM playback_states WHERE video_id = :videoId")
    suspend fun getPlaybackState(videoId: String): PlaybackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackState(state: PlaybackEntity)

    @Query("DELETE FROM playback_states WHERE video_id = :videoId")
    suspend fun deletePlaybackState(videoId: String)

    @Query("DELETE FROM playback_states")
    suspend fun clearAll()
}
