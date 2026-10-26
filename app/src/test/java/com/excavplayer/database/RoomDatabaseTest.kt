package com.excavplayer.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.excavplayer.data.database.ExcavDatabase
import com.excavplayer.data.database.dao.PlaybackDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.PlaybackEntity
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

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExcavDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        videoDao = database.videoDao()
        playbackDao = database.playbackDao()
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
    fun `foreign key cascade deletes playback state on video deletion`() = runBlocking {
        val v1 = createVideoEntity("v1")
        videoDao.insertVideo(v1)

        playbackDao.upsertPlaybackState(
            PlaybackEntity(videoId = "v1", currentPositionMs = 50000L, durationMs = 100000L)
        )

        // Verify inserted
        assertThat(playbackDao.getPlaybackState("v1")).isNotNull()

        // Delete video
        videoDao.deleteVideo("v1")

        // Foreign keys should cascade delete all associated records
        assertThat(playbackDao.getPlaybackState("v1")).isNull()
    }

    // ── Exclusion-rule matrix ────────────────────────────────────────
    // These queries mirror LibraryDerivation.isExcludedFromLibrary (see VideoDao).
    // Each row of the matrix: the SQL must exclude exactly what the Kotlin
    // predicate excludes for DB-shaped videos.

    private fun excludedVideo(id: String, folderName: String, folderPath: String, uri: String? = null): VideoEntity =
        createVideoEntity(id).copy(folderName = folderName, folderPath = folderPath, uri = uri ?: createVideoEntity(id).uri)

    @Test
    fun `observeAllVideosWithMetadata excludes special folders and keeps normal ones`() = runBlocking {
        videoDao.insertVideos(
            listOf(
                createVideoEntity("normal"),
                excludedVideo("ex1", "SAF Files", "/storage/emulated/0/SAF Files"),
                excludedVideo("ex2", "External Videos", "External Videos"),
                excludedVideo("ex3", "Selected Folder", "/storage/emulated/0/Selected Folder"),
                excludedVideo("ex4", "private", "private"),
                excludedVideo("ex5", "Movies", "/storage/private"),
                excludedVideo("ex6", "Movies", "/storage/private/sub"),
                excludedVideo("ex7", "Movies", "/x", uri = "content://com.android.providers.media.documents/document/1"),
                excludedVideo("ex8", "Movies", "/x", uri = "content://com.android.externalstorage.documents/tree/1"),
                excludedVideo("ex9", "Movies", "/x").copy(id = "saf_doc_1")
            )
        )

        val visible = videoDao.observeAllVideosWithMetadata().first()
        assertThat(visible.map { it.video.id }).containsExactly("normal").inOrder()
    }

    @Test
    fun `observeFolders excludes special folders`() = runBlocking {
        videoDao.insertVideos(
            listOf(
                createVideoEntity("normal").copy(folderName = "Movies", folderPath = "/storage/emulated/0/Movies"),
                excludedVideo("ex1", "SAF Files", "SAF Files")
            )
        )

        val folders = videoDao.observeFolders().first()
        assertThat(folders.map { it.folderName }).containsExactly("Movies").inOrder()
    }

    @Test
    fun `purgePrivateStorageVideos removes every excluded row`() = runBlocking {
        videoDao.insertVideos(
            listOf(
                createVideoEntity("normal"),
                excludedVideo("ex1", "private", "private"),
                excludedVideo("ex2", "Movies", "/storage/private/x"),
                excludedVideo("ex3", "SAF Files", "SAF Files"),
                excludedVideo("ex4", "External Videos", "External Videos")
            )
        )

        videoDao.purgePrivateStorageVideos()

        val remaining = videoDao.getAllVideoIds()
        assertThat(remaining).containsExactly("normal").inOrder()
    }
}
