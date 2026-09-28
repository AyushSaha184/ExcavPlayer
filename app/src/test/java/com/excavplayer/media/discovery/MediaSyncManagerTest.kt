package com.excavplayer.media.discovery

import com.google.common.truth.Truth.assertThat
import com.excavplayer.core.coroutine.DefaultDispatcherProvider
import com.excavplayer.core.logging.AndroidAppLogger
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.data.media.MediaStoreDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaSyncManagerTest {

    private val mediaStoreDataSource = mockk<MediaStoreDataSource>()
    private val videoDao = mockk<VideoDao>(relaxed = true)
    private lateinit var syncManager: MediaSyncManager

    @Before
    fun setup() {
        syncManager = MediaSyncManager(
            mediaStoreDataSource = mediaStoreDataSource,
            videoDao = videoDao,
            dispatchers = DefaultDispatcherProvider(),
            logger = AndroidAppLogger()
        )
    }

    @Test
    fun `syncMediaStore discovers new videos and marks missing videos as unavailable`() = runBlocking {
        // Existing videos in Room
        coEvery { videoDao.getAllVideoIds() } returns listOf("ms_1", "ms_2", "ms_3")

        val lambdaSlot = slot<suspend (List<VideoEntity>) -> Unit>()
        coEvery {
            mediaStoreDataSource.queryAllVideosPaged(any(), capture(lambdaSlot))
        } coAnswers {
            // MediaStore only returns ms_1 and ms_2; ms_3 is missing/deleted
            val discoveredBatch = listOf(
                createVideoEntity("ms_1"),
                createVideoEntity("ms_2"),
                createVideoEntity("ms_4") // new video
            )
            lambdaSlot.captured.invoke(discoveredBatch)
            3
        }

        val result = syncManager.syncMediaStore()

        assertThat(result.isSuccess).isTrue()
        // Video ms_4 and ms_1, ms_2 inserted
        coVerify { videoDao.insertVideos(any()) }
        // Missing ms_3 should be marked unavailable
        coVerify { videoDao.markUnavailable(listOf("ms_3")) }
    }

    private fun createVideoEntity(id: String): VideoEntity {
        return VideoEntity(
            id = id,
            uri = "content://media/external/video/media/$id",
            displayName = "Video $id",
            mimeType = "video/mp4",
            durationMs = 60000L,
            sizeBytes = 1000L,
            dateAddedSeconds = 1000L,
            dateModifiedSeconds = 1000L,
            width = 1920,
            height = 1080
        )
    }
}
