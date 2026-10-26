package com.excavplayer.lifecycle

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.data.database.dao.VideoWithMetadataTuple
import com.excavplayer.data.lifecycle.VideoLifecycleImpl
import com.excavplayer.domain.lifecycle.DeleteOutcome
import com.excavplayer.domain.lifecycle.VideoLifecycle
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.VideoRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingLogger : AppLogger {
    override fun d(tag: String, message: String) {}
    override fun i(tag: String, message: String) {}
    override fun w(tag: String, message: String, throwable: Throwable?) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}

private class TestDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

/** Fake repository recording the delete/sync/purge order. */
private class FakeVideoRepository : VideoRepository {
    val calls = mutableListOf<String>()
    var deleteResultFor: (String) -> ExcavResult<Unit> = { ExcavResult.Success(Unit) }
    var syncResult: ExcavResult<Int> = ExcavResult.Success(0)

    override fun observeVideos(): Flow<List<Video>> = emptyFlow()
    override fun observePagedVideos(): Flow<androidx.paging.PagingData<Video>> = TODO()
    override fun observeVideoById(id: String): Flow<Video?> = emptyFlow()
    override suspend fun getVideoById(id: String): Video? = null
    override fun observeFolders(): Flow<List<com.excavplayer.domain.model.Folder>> = emptyFlow()
    override fun observeVideosInFolder(folderPath: String): Flow<List<Video>> = emptyFlow()
    override fun searchVideos(query: String): Flow<List<Video>> = emptyFlow()
    override suspend fun renameVideo(videoId: String, newName: String): ExcavResult<Unit> = TODO()
    override suspend fun deleteVideo(videoId: String): ExcavResult<Unit> {
        calls += "delete:$videoId"
        return deleteResultFor(videoId)
    }
    override suspend fun deleteVideosAfterConfirmation(videoIds: List<String>): ExcavResult<Unit> {
        calls += "confirm:${videoIds.joinToString(",")}"
        return ExcavResult.Success(Unit)
    }
    override suspend fun updateAvailability(videoId: String, availability: MediaAvailability) = Unit
    override suspend fun syncWithMediaStore(force: Boolean): ExcavResult<Int> {
        calls += "sync:$force"
        return syncResult
    }
    override suspend fun purgeCorruptedEntries() {
        calls += "purge"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoLifecycleImplTest {

    private lateinit var repo: FakeVideoRepository
    private lateinit var dao: com.excavplayer.data.database.dao.VideoDao
    private lateinit var thumbnailLoader: com.excavplayer.media.thumbnail.ThumbnailLoader
    private lateinit var lifecycle: VideoLifecycle
    private lateinit var context: Context

    private fun video(
        id: String,
        uri: String,
        sourceType: MediaSourceType
    ) = Video(
        id = id,
        uri = uri,
        displayName = id,
        mimeType = "video/mp4",
        durationMs = 1L,
        sizeBytes = 1L,
        dateAddedSeconds = 0L,
        dateModifiedSeconds = 0L,
        width = 0,
        height = 0,
        folderName = "F",
        folderPath = "/F",
        relativePath = "",
        sourceType = sourceType,
        availability = MediaAvailability.AVAILABLE
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = FakeVideoRepository()
        dao = mockk(relaxed = true)
        thumbnailLoader = mockk(relaxed = true)
        lifecycle = VideoLifecycleImpl(
            context = context,
            videoRepository = repo,
            videoDao = dao,
            thumbnailLoader = thumbnailLoader,
            dispatchers = TestDispatcherProvider(),
            logger = RecordingLogger()
        )
    }

    @After
    fun tearDown() {
        val impl = lifecycle as VideoLifecycleImpl
        impl.approvalRequestFactory = { uris -> MediaStore.createDeleteRequest(context.contentResolver, uris) }
        io.mockk.unmockkAll()
    }

    @Test
    fun `refresh runs the purge ritual then the sync`() = runTest {
        repo.syncResult = ExcavResult.Success(7)

        val result = lifecycle.refresh(force = true)

        assertThat(result).isEqualTo(ExcavResult.Success(7))
        assertThat(repo.calls).containsExactly("purge", "sync:true").inOrder()
    }

    @Test
    fun `document video deletes directly without system approval`() = runTest {
        val saf = video("saf_1", "content://com.android.externalstorage.documents/document/primary%3AMovie.mp4", MediaSourceType.LOCAL_DOCUMENT)

        val outcome = lifecycle.deleteVideos(listOf(saf))

        assertThat(outcome).isInstanceOf(DeleteOutcome.Deleted::class.java)
        assertThat((outcome as DeleteOutcome.Deleted).videoIds).containsExactly("saf_1")
        assertThat(repo.calls).containsExactly("delete:saf_1")
    }

    @Test
    fun `tree video deletes directly`() = runTest {
        val tree = video("tree_1", "content://com.android.externalstorage.documents/tree/primary%3AMovies", MediaSourceType.LOCAL_TREE)

        val outcome = lifecycle.deleteVideos(listOf(tree))

        assertThat(outcome).isInstanceOf(DeleteOutcome.Deleted::class.java)
        assertThat(repo.calls).containsExactly("delete:tree_1")
    }

    @Test
    fun `file scheme video deletes directly`() = runTest {
        val file = video("file_1", "file:///storage/emulated/0/Movie.mp4", MediaSourceType.LOCAL_DOCUMENT)

        val outcome = lifecycle.deleteVideos(listOf(file))

        assertThat(outcome).isInstanceOf(DeleteOutcome.Deleted::class.java)
    }

    @Test
    fun `partial failure maps to Partial with both lists`() = runTest {
        repo.deleteResultFor = { id -> if (id == "a") ExcavResult.Success(Unit) else ExcavResult.Error(IllegalStateException("locked")) }
        val one = video("a", "file:///F/a.mp4", MediaSourceType.LOCAL_DOCUMENT)
        val two = video("b", "file:///F/b.mp4", MediaSourceType.LOCAL_DOCUMENT)

        val outcome = lifecycle.deleteVideos(listOf(one, two))

        assertThat(outcome).isEqualTo(DeleteOutcome.Partial(deletedIds = listOf("a"), failedIds = listOf("b")))
    }

    @Test
    fun `empty batch short-circuits to Deleted`() = runTest {
        val outcome = lifecycle.deleteVideos(emptyList())

        assertThat(outcome).isEqualTo(DeleteOutcome.Deleted(emptyList()))
        assertThat(repo.calls).isEmpty()
    }

    @Test
    fun `duplicates collapse to one delete call`() = runTest {
        val one = video("a", "file:///F/a.mp4", MediaSourceType.LOCAL_DOCUMENT)

        val outcome = lifecycle.deleteVideos(listOf(one, one))

        assertThat((outcome as DeleteOutcome.Deleted).videoIds).containsExactly("a")
        assertThat(repo.calls).containsExactly("delete:a")
    }

    @Test
    fun `mediaStore video on R plus maps to approval with wrapped sender`() = runTest {
        val mediaStore = video("ms_1", "content://media/external/video/media/42", MediaSourceType.LOCAL_MEDIASTORE)
        val sender = mockk<IntentSender>()

        val impl = lifecycle as VideoLifecycleImpl
        impl.approvalRequestFactory = { mockk { every { intentSender } returns sender } }

        val outcome = lifecycle.deleteVideos(listOf(mediaStore))

        assertThat(outcome).isEqualTo(DeleteOutcome.ApprovalRequired(sender, listOf("ms_1")))
        // The system dialog deletes the file; no direct repo delete happened
        assertThat(repo.calls).isEmpty()
    }

    @Test
    fun `mediaStore approval maps to cleanup after confirmation`() = runTest {
        val row = mockk<VideoWithMetadataTuple>(relaxed = true)
        coEvery { dao.getVideoById("ms_1") } returns row

        val result = lifecycle.finalizeApprovedDeletion(listOf("ms_1"))

        assertThat(result).isInstanceOf(ExcavResult.Success::class.java)
        coVerify { thumbnailLoader.evictThumbnail(any()) }
        coVerify { dao.deleteVideos(listOf("ms_1")) }
    }

    @Test
    fun `finalize skips missing rows and still cleans the batch`() = runTest {
        coEvery { dao.getVideoById("gone") } returns null

        val result = lifecycle.finalizeApprovedDeletion(listOf("gone"))

        assertThat(result).isInstanceOf(ExcavResult.Success::class.java)
        coVerify(exactly = 0) { thumbnailLoader.evictThumbnail(any()) }
        coVerify { dao.deleteVideos(listOf("gone")) }
    }

    @Test
    fun `purgeCorruptedEntries delegates to the repository`() = runTest {
        lifecycle.purgeCorruptedEntries()

        assertThat(repo.calls).containsExactly("purge")
    }

    @Test
    fun `out of range uri parses without crashing the classifier`() = runTest {
        // Uri.parse never throws, but a null-ish scheme must route to direct delete
        val weird = video("w_1", "//no-scheme", MediaSourceType.LOCAL_MEDIASTORE)

        val outcome = lifecycle.deleteVideos(listOf(weird))

        assertThat(outcome).isInstanceOf(DeleteOutcome.Deleted::class.java)
    }
}
