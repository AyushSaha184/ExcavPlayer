package com.excavplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.excavplayer.data.database.entity.FavoriteEntity
import com.excavplayer.data.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY last_played_timestamp DESC")
    fun observeHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE video_id = :videoId")
    suspend fun getHistoryForVideo(videoId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE video_id = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()

    @Query("""
        DELETE FROM watch_history 
        WHERE id NOT IN (
            SELECT id FROM watch_history 
            ORDER BY last_played_timestamp DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun pruneOldEntries(keepCount: Int)
}

@Dao
interface FavoriteDao {
    @Query("""
        SELECT v.*, 1 AS is_favorite, p.current_position_ms AS resume_position_ms
        FROM favorites f
        INNER JOIN videos v ON f.video_id = v.id
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.availability = 'AVAILABLE'
        ORDER BY f.added_timestamp DESC
    """)
    fun observeFavoritesWithMetadata(): Flow<List<VideoWithMetadataTuple>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE video_id = :videoId)")
    fun isFavorite(videoId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE video_id = :videoId")
    suspend fun deleteFavorite(videoId: String)
}
