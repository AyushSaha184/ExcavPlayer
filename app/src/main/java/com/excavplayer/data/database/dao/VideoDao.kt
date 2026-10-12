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
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.availability = 'AVAILABLE'
          AND v.folder_name != 'External Videos'
          AND v.folder_path != 'External Videos'
          AND v.folder_path NOT LIKE '%External Videos%'
          AND v.folder_name != 'SAF Files'
          AND v.folder_path != 'SAF Files'
          AND v.folder_path NOT LIKE '%SAF Files%'
          AND v.folder_name != 'Selected Folder'
          AND v.folder_path != 'Selected Folder'
          AND v.folder_path NOT LIKE '%Selected Folder%'
          AND v.folder_name != 'private'
          AND v.folder_path != 'private'
          AND v.folder_path != '/storage/private'
          AND v.folder_path NOT LIKE '/storage/private/%'
          AND v.folder_path NOT LIKE '%/private'
          AND v.id NOT LIKE 'saf_%'
          AND v.uri NOT LIKE '%com.android.providers.media.documents%'
          AND v.uri NOT LIKE '%com.android.externalstorage.documents%'
        ORDER BY v.date_added DESC
    """)
    fun observeAllVideosWithMetadata(): Flow<List<VideoWithMetadataTuple>>

    @Query("""
        SELECT v.*, 
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.availability = 'AVAILABLE'
          AND v.folder_name != 'External Videos'
          AND v.folder_path != 'External Videos'
          AND v.folder_path NOT LIKE '%External Videos%'
          AND v.folder_name != 'SAF Files'
          AND v.folder_path != 'SAF Files'
          AND v.folder_path NOT LIKE '%SAF Files%'
          AND v.folder_name != 'Selected Folder'
          AND v.folder_path != 'Selected Folder'
          AND v.folder_path NOT LIKE '%Selected Folder%'
          AND v.folder_name != 'private'
          AND v.folder_path != 'private'
          AND v.folder_path != '/storage/private'
          AND v.folder_path NOT LIKE '/storage/private/%'
          AND v.folder_path NOT LIKE '%/private'
          AND v.id NOT LIKE 'saf_%'
          AND v.uri NOT LIKE '%com.android.providers.media.documents%'
          AND v.uri NOT LIKE '%com.android.externalstorage.documents%'
        ORDER BY v.date_added DESC
    """)
    fun pagingSourceAllVideos(): androidx.paging.PagingSource<Int, VideoWithMetadataTuple>

    @Query("""
        SELECT v.*, 
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.id = :id AND v.availability = 'AVAILABLE'
    """)
    fun observeVideoById(id: String): Flow<VideoWithMetadataTuple?>

    @Query("""
        SELECT v.*, 
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.id = :id
    """)
    suspend fun getVideoById(id: String): VideoWithMetadataTuple?

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getVideoByUri(uri: String): VideoEntity?

    @Query("""
        SELECT v.*, 
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE (
            v.folder_path = :path1 
            OR v.folder_path = :path2 
            OR v.folder_path = :path3 
            OR v.folder_path = :path4 
            OR v.relative_path = :relPath1 
            OR v.relative_path = :relPath2
        ) AND v.availability = 'AVAILABLE'
        ORDER BY v.date_added DESC
    """)
    fun observeVideosInFolder(
        path1: String,
        path2: String,
        path3: String,
        path4: String,
        relPath1: String,
        relPath2: String
    ): Flow<List<VideoWithMetadataTuple>>

    @Query("""
        SELECT v.*, 
               p.current_position_ms AS resume_position_ms
        FROM videos v
        LEFT JOIN playback_states p ON v.id = p.video_id
        WHERE v.display_name LIKE '%' || :query || '%' AND v.availability = 'AVAILABLE'
        ORDER BY v.date_added DESC
    """)
    fun searchVideos(query: String): Flow<List<VideoWithMetadataTuple>>

    @Query("""
        SELECT folder_path, folder_name, COUNT(*) AS video_count, SUM(size_bytes) AS total_size_bytes
        FROM videos
        WHERE availability = 'AVAILABLE'
          AND folder_name != 'External Videos'
          AND folder_path != 'External Videos'
          AND folder_path NOT LIKE '%External Videos%'
          AND folder_name != 'SAF Files'
          AND folder_path != 'SAF Files'
          AND folder_path NOT LIKE '%SAF Files%'
          AND folder_name != 'Selected Folder'
          AND folder_path != 'Selected Folder'
          AND folder_path NOT LIKE '%Selected Folder%'
          AND folder_name != 'private'
          AND folder_path != 'private'
          AND folder_path != '/storage/private'
          AND folder_path NOT LIKE '/storage/private/%'
          AND folder_path NOT LIKE '%/private'
          AND id NOT LIKE 'saf_%'
          AND uri NOT LIKE '%com.android.providers.media.documents%'
          AND uri NOT LIKE '%com.android.externalstorage.documents%'
        GROUP BY folder_path, folder_name
        ORDER BY folder_name ASC
    """)
    fun observeFolders(): Flow<List<FolderTuple>>

    @Query("""
        SELECT v.*, 
               p.current_position_ms AS resume_position_ms
        FROM videos v
        INNER JOIN playback_states p ON v.id = p.video_id
        LEFT JOIN dismissed_continue_watching d ON v.id = d.video_id
        WHERE p.current_position_ms >= 20000 
          AND p.duration_ms > 0
          AND (CAST(p.current_position_ms AS REAL) / CAST(p.duration_ms AS REAL)) < :threshold
          AND v.availability = 'AVAILABLE'
          AND d.video_id IS NULL
        ORDER BY p.last_updated_timestamp DESC
    """)
    fun observeContinueWatching(threshold: Float): Flow<List<VideoWithMetadataTuple>>

    @Query("INSERT OR REPLACE INTO dismissed_continue_watching (video_id) VALUES (:videoId)")
    suspend fun dismissFromContinueWatching(videoId: String)

    @androidx.room.Upsert
    suspend fun insertVideos(videos: List<VideoEntity>)

    @androidx.room.Upsert
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET display_name = :newName WHERE id = :videoId")
    suspend fun renameVideo(videoId: String, newName: String)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteVideo(videoId: String)

    @Query("DELETE FROM videos WHERE id IN (:videoIds)")
    suspend fun deleteVideos(videoIds: List<String>)

    @Query("SELECT id FROM videos")
    suspend fun getAllVideoIds(): List<String>

    @Query("SELECT uri FROM videos")
    suspend fun getAllVideoUris(): List<String>

    @Query("SELECT LOWER(display_name) || ':' || size_bytes FROM videos")
    suspend fun getAllVideoKeys(): List<String>

    @Query("SELECT id, uri FROM videos WHERE id LIKE 'fs_%'")
    suspend fun getAllFsVideos(): List<IdUriTuple>

    @Query("""
        DELETE FROM videos 
        WHERE folder_name = 'private' 
           OR folder_path = 'private'
           OR folder_path = '/storage/private'
           OR folder_path LIKE '/storage/private/%' 
           OR folder_path LIKE '%/private'
           OR uri LIKE '%/storage/private%'
           OR folder_name = 'External Videos'
           OR folder_path = 'External Videos'
           OR folder_path LIKE '%External Videos%'
           OR folder_name = 'SAF Files'
           OR folder_path = 'SAF Files'
           OR folder_path LIKE '%SAF Files%'
           OR folder_name = 'Selected Folder'
           OR folder_path = 'Selected Folder'
           OR folder_path LIKE '%Selected Folder%'
           OR id LIKE 'saf_%'
           OR uri LIKE '%com.android.providers.media.documents%'
           OR uri LIKE '%com.android.externalstorage.documents%'
    """)
    suspend fun purgePrivateStorageVideos()

    @Query("UPDATE videos SET availability = :availability WHERE id = :videoId")
    suspend fun updateAvailability(videoId: String, availability: String)

    @Query("UPDATE videos SET availability = 'UNAVAILABLE' WHERE id IN (:videoIds)")
    suspend fun markUnavailable(videoIds: List<String>)
}
