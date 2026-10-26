package com.excavplayer.playback

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.repository.PlaybackRepository
import com.excavplayer.domain.repository.SettingsRepository
import com.excavplayer.player.playback.PlaybackPersistenceManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class NoopLogger : AppLogger {
    override fun d(tag: String, message: String) {}
    override fun i(tag: String, message: String) {}
    override fun w(tag: String, message: String, throwable: Throwable?) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}

private class ImmediateDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

class PlaybackTrackPersistenceTest {

    private fun manager(repo: PlaybackRepository): PlaybackPersistenceManager = PlaybackPersistenceManager(
        playbackRepository = repo,
        settingsRepository = mockkSettings(),
        dispatchers = ImmediateDispatcherProvider(),
        logger = NoopLogger()
    )

    private fun mockkSettings(): SettingsRepository =
        io.mockk.mockk {
            io.mockk.every { userSettings } returns kotlinx.coroutines.flow.MutableStateFlow(
                com.excavplayer.domain.model.UserSettings()
            )
        }

    @Test
    fun `existing row gains the audio preference and keeps its position`() = runTest {
        val saved = mutableListOf<PlaybackState>()
        val repo = io.mockk.mockk<PlaybackRepository>(relaxed = true)
        io.mockk.coEvery { repo.getPlaybackState("v1") } returns PlaybackState(
            videoId = "v1",
            currentPositionMs = 42_000L,
            durationMs = 100_000L
        )
        io.mockk.coEvery { repo.savePlaybackState(any()) } answers { saved.add(firstArg<PlaybackState>()); Unit }

        manager(repo).saveTrackSelection("v1", audioTrackId = "track-2")

        assertThat(saved).hasSize(1)
        val row = saved.single()
        assertThat(row.selectedAudioTrackId).isEqualTo("track-2")
        assertThat(row.currentPositionMs).isEqualTo(42_000L)
        assertThat(row.durationMs).isEqualTo(100_000L)
    }

    @Test
    fun `no existing row creates the bare preference row with video duration`() = runTest {
        val saved = mutableListOf<PlaybackState>()
        val repo = io.mockk.mockk<PlaybackRepository>(relaxed = true)
        io.mockk.coEvery { repo.getPlaybackState("v1") } returns null
        io.mockk.coEvery { repo.savePlaybackState(any()) } answers { saved.add(firstArg<PlaybackState>()); Unit }

        manager(repo).saveTrackSelection("v1", audioTrackId = "a|en|Stereo", videoDurationMs = 90_000L)

        val row = saved.single()
        assertThat(row.videoId).isEqualTo("v1")
        assertThat(row.selectedAudioTrackId).isEqualTo("a|en|Stereo")
        // The fabricated zero position row is gone: position is simply unset (0 default)
        assertThat(row.currentPositionMs).isEqualTo(0L)
        assertThat(row.durationMs).isEqualTo(90_000L)
    }

    @Test
    fun `repository failure is logged not thrown`() = runTest {
        val repo = io.mockk.mockk<PlaybackRepository>(relaxed = true)
        io.mockk.coEvery { repo.getPlaybackState("v1") } returns null
        io.mockk.coEvery { repo.savePlaybackState(any()) } throws IllegalStateException("disk full")

        manager(repo).saveTrackSelection("v1", audioTrackId = "track-1")
        // No exception escaped — that is the contract
    }

    @Test
    fun `subtitle selection merges without clobbering audio`() = runTest {
        val saved = mutableListOf<PlaybackState>()
        val repo = io.mockk.mockk<PlaybackRepository>(relaxed = true)
        io.mockk.coEvery { repo.getPlaybackState("v1") } returns PlaybackState(
            videoId = "v1",
            selectedAudioTrackId = "a-1",
            currentPositionMs = 5_000L,
            durationMs = 100_000L
        )
        io.mockk.coEvery { repo.savePlaybackState(any()) } answers { saved.add(firstArg<PlaybackState>()); Unit }

        manager(repo).saveTrackSelection("v1", audioTrackId = "a-1", subtitleTrackId = "s-3")

        val row = saved.single()
        assertThat(row.selectedAudioTrackId).isEqualTo("a-1")
        assertThat(row.selectedSubtitleTrackId).isEqualTo("s-3")
    }
}
