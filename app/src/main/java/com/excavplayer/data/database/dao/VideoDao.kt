package com.excavplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.excavplayer.data.database.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("""
        SELECT v.*, 
               (f.video_id IS NOT NULL) AS is_favorite,
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN favorites f ON v.id = f.video_id
        LEFT JOIN playback_states p ON v.id = p.video_id
        ORDER BY v.date_added DESC
    """)
    fun observeAllVideosWithMetadata(): Flow<List<VideoWithMetadataTuple>>

    @Query("""
        SELECT v.*, 
               (f.video_id IS NOT NULL) AS is_favorite,
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN favorites f ON v.id = f.video_id
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.id = :id
    """)
    fun observeVideoById(id: String): Flow<VideoWithMetadataTuple?>

    @Query("""
        SELECT v.*, 
               (f.video_id IS NOT NULL) AS is_favorite,
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN favorites f ON v.id = f.video_id
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.id = :id
    """)
    suspend fun getVideoById(id: String): VideoWithMetadataTuple?

    @Query("""
        SELECT v.*, 
               (f.video_id IS NOT NULL) AS is_favorite,
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN favorites f ON v.id = f.video_id
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.folder_path = :folderPath
        ORDER BY v.date_added DESC
    """)
    fun observeVideosInFolder(folderPath: String): Flow<List<VideoWithMetadataTuple>>

    @Query("""
        SELECT v.*, 
               (f.video_id IS NOT NULL) AS is_favorite,
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN favorites f ON v.id = f.video_id
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.display_name LIKE '%' || :query || '%'
        ORDER BY v.date_added DESC
    """)
    fun searchVideos(query: String): Flow<List<VideoWithMetadataTuple>>

    @Query("""
        SELECT folder_path, folder_name, COUNT(*) AS video_count, SUM(size_bytes) AS total_size_bytes
        FROM videos
        WHERE availability = 'AVAILABLE'
        GROUP BY folder_path, folder_name
        ORDER BY folder_name ASC
    """)
    fun observeFolders(): Flow<List<FolderTuple>>

    @Query("""
        SELECT v.*, 
               (f.video_id IS NOT NULL) AS is_favorite,
               p.current_position_ms AS resume_position_ms
        FROM videos v
        INNER JOIN playback_states p ON v.id = p.video_id
        LEFT JOIN favorites f ON v.id = f.video_id
        WHERE p.current_position_ms > 5000 
          AND p.duration_ms > 0
          AND (CAST(p.current_position_ms AS REAL) / CAST(p.duration_ms AS REAL)) < :threshold
          AND v.availability = 'AVAILABLE'
        ORDER BY p.last_updated_timestamp DESC
    """)
    fun observeContinueWatching(threshold: Float): Flow<List<VideoWithMetadataTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteVideo(videoId: String)

    @Query("DELETE FROM videos WHERE id IN (:videoIds)")
    suspend fun deleteVideos(videoIds: List<String>)

    @Query("SELECT id FROM videos")
    suspend fun getAllVideoIds(): List<String>

    @Query("SELECT uri FROM videos")
    suspend fun getAllVideoUris(): List<String>

    @Query("UPDATE videos SET availability = :availability WHERE id = :videoId")
    suspend fun updateAvailability(videoId: String, availability: String)

    @Query("UPDATE videos SET availability = 'UNAVAILABLE' WHERE id IN (:videoIds)")
    suspend fun markUnavailable(videoIds: List<String>)
}
