package com.excavplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.PlaylistEntity
import com.excavplayer.data.database.entity.PlaylistItemEntity
import com.excavplayer.data.database.entity.SubtitlePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("""
        SELECT p.id, p.title, p.created_at, p.updated_at, COUNT(pi.id) AS item_count
        FROM playlists p
        LEFT JOIN playlist_items pi ON p.id = pi.playlist_id
        GROUP BY p.id
        ORDER BY p.updated_at DESC
    """)
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCountTuple>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Transaction
    @Query("""
        SELECT pi.*, 
               v.id AS v_id, v.uri AS v_uri, v.display_name AS v_display_name,
               v.mime_type AS v_mime_type, v.duration_ms AS v_duration_ms,
               v.size_bytes AS v_size_bytes, v.date_added AS v_date_added,
               v.date_modified AS v_date_modified, v.width AS v_width,
               v.height AS v_height, v.bitrate AS v_bitrate,
               v.frame_rate AS v_frame_rate, v.orientation AS v_orientation,
               v.folder_name AS v_folder_name, v.folder_path AS v_folder_path,
               v.relative_path AS v_relative_path, v.source_type AS v_source_type,
               v.availability AS v_availability, v.last_scanned_timestamp AS v_last_scanned_timestamp
        FROM playlist_items pi
        INNER JOIN videos v ON pi.video_id = v.id
        WHERE pi.playlist_id = :playlistId
        ORDER BY pi.order_index ASC
    """)
    fun observePlaylistItemsWithVideo(playlistId: Long): Flow<List<PlaylistItemWithVideoTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId AND video_id = :videoId")
    suspend fun deletePlaylistItem(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deletePlaylistItemById(itemId: Long)

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY order_index ASC")
    suspend fun getPlaylistItemsDirect(playlistId: Long): List<PlaylistItemEntity>

    @Update
    suspend fun updatePlaylistItems(items: List<PlaylistItemEntity>)

    @Query("SELECT COALESCE(MAX(order_index), -1) + 1 FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun getNextOrderIndex(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun getItemCount(playlistId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlist_id = :playlistId AND video_id = :videoId)")
    suspend fun hasVideo(playlistId: Long, videoId: String): Boolean
}

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
