package com.excavplayer.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.excavplayer.data.database.ExcavDatabase
import com.excavplayer.data.database.dao.FavoriteDao
import com.excavplayer.data.database.dao.PlaybackDao
import com.excavplayer.data.database.dao.PlaylistDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.FavoriteEntity
import com.excavplayer.data.database.entity.PlaybackEntity
import com.excavplayer.data.database.entity.PlaylistEntity
import com.excavplayer.data.database.entity.PlaylistItemEntity
import com.excavplayer.data.database.entity.VideoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseTest {

    private lateinit var database: ExcavDatabase
    private lateinit var videoDao: VideoDao
    private lateinit var playbackDao: PlaybackDao
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExcavDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        videoDao = database.videoDao()
        playbackDao = database.playbackDao()
        favoriteDao = database.favoriteDao()
        playlistDao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createVideoEntity(id: String, displayName: String = "Test Video"): VideoEntity {
        return VideoEntity(
            id = id,
            uri = "content://media/external/video/media/$id",
            displayName = displayName,
            mimeType = "video/mp4",
            durationMs = 100000L,
            sizeBytes = 1024L * 1024L,
            dateAddedSeconds = 1000L,
            dateModifiedSeconds = 1000L,
            width = 1920,
            height = 1080,
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies",
            availability = "AVAILABLE"
        )
    }

    @Test
    fun `insert and observe videos returns items with metadata`() = runBlocking {
        val v1 = createVideoEntity("v1", "Action Movie").copy(dateAddedSeconds = 2000L)
        val v2 = createVideoEntity("v2", "Comedy Movie").copy(dateAddedSeconds = 1000L)
        videoDao.insertVideos(listOf(v1, v2))

        val allVideos = videoDao.observeAllVideosWithMetadata().first()
        assertThat(allVideos).hasSize(2)
        assertThat(allVideos[0].video.id).isEqualTo("v1")
        assertThat(allVideos[0].isFavorite).isFalse()
        assertThat(allVideos[0].resumePositionMs).isNull()
    }

    @Test
    fun `continue watching query excludes completed and unstarted videos`() = runBlocking {
        val v1 = createVideoEntity("v1") // In progress (20%)
        val v2 = createVideoEntity("v2") // Completed (98%)
        val v3 = createVideoEntity("v3") // Barely started (< 20 sec)
        videoDao.insertVideos(listOf(v1, v2, v3))

        playbackDao.upsertPlaybackState(
            PlaybackEntity(
                videoId = "v1",
                currentPositionMs = 20000L,
                durationMs = 100000L,
                lastUpdatedTimestamp = 1000L
            )
        )
        playbackDao.upsertPlaybackState(
            PlaybackEntity(
                videoId = "v2",
                currentPositionMs = 98000L,
                durationMs = 100000L,
                lastUpdatedTimestamp = 2000L
            )
        )
        playbackDao.upsertPlaybackState(
            PlaybackEntity(
                videoId = "v3",
                currentPositionMs = 2000L,
                durationMs = 100000L,
                lastUpdatedTimestamp = 3000L
            )
        )

        val continueWatching = videoDao.observeContinueWatching(threshold = 0.95f).first()
        assertThat(continueWatching).hasSize(1)
        assertThat(continueWatching[0].video.id).isEqualTo("v1")
        assertThat(continueWatching[0].resumePositionMs).isEqualTo(20000L)
    }

    @Test
    fun `foreign key cascade deletes playback state and favorites on video deletion`() = runBlocking {
        val v1 = createVideoEntity("v1")
        videoDao.insertVideo(v1)

        playbackDao.upsertPlaybackState(
            PlaybackEntity(videoId = "v1", currentPositionMs = 50000L, durationMs = 100000L)
        )
        favoriteDao.insertFavorite(FavoriteEntity(videoId = "v1"))

        // Verify inserted
        assertThat(playbackDao.getPlaybackState("v1")).isNotNull()
        assertThat(favoriteDao.isFavorite("v1").first()).isTrue()

        // Delete video
        videoDao.deleteVideo("v1")

        // Foreign keys should cascade delete all associated records
        assertThat(playbackDao.getPlaybackState("v1")).isNull()
        assertThat(favoriteDao.isFavorite("v1").first()).isFalse()
    }

    @Test
    fun `playlist item ordering and reordering works properly`() = runBlocking {
        val v1 = createVideoEntity("v1")
        val v2 = createVideoEntity("v2")
        val v3 = createVideoEntity("v3")
        videoDao.insertVideos(listOf(v1, v2, v3))

        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(title = "My Favorites Playlist")
        )

        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = playlistId, videoId = "v1", orderIndex = 0))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = playlistId, videoId = "v2", orderIndex = 1))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = playlistId, videoId = "v3", orderIndex = 2))

        val items = playlistDao.observePlaylistItemsWithVideo(playlistId).first()
        assertThat(items).hasSize(3)
        assertThat(items.map { it.item.videoId }).containsExactly("v1", "v2", "v3").inOrder()

        // Swap order index of v1 and v3
        val direct = playlistDao.getPlaylistItemsDirect(playlistId).toMutableList()
        val moved = direct.removeAt(0)
        direct.add(moved)
        val updated = direct.mapIndexed { idx, it -> it.copy(orderIndex = idx) }
        playlistDao.updatePlaylistItems(updated)

        val reorderedItems = playlistDao.observePlaylistItemsWithVideo(playlistId).first()
        assertThat(reorderedItems.map { it.item.videoId }).containsExactly("v2", "v3", "v1").inOrder()
    }
}

