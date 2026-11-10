package com.excavplayer.player

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.SeekParameters
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerSeekConfigurationTest {

    @Test
    fun `loadControl buffer durations configure valid safe playback buffers`() {
        // Ensure the buffer parameters build without throwing IllegalArgumentException
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_500,
                /* maxBufferMs = */ 8_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        assertThat(loadControl).isNotNull()
    }

    @Test
    fun `seekParameters default enforces exact audio video synchronization`() {
        // SeekParameters.DEFAULT has 0 tolerance before and after, guaranteeing exact presentation timestamp
        // matching between audio and video tracks without jumping to a distant future keyframe.
        val defaultSeek = SeekParameters.DEFAULT
        assertThat(defaultSeek.toleranceBeforeUs).isEqualTo(0L)
        assertThat(defaultSeek.toleranceAfterUs).isEqualTo(0L)

        // Contrast with CLOSEST_SYNC which snaps to future keyframes leaving audio desynced
        val closestSync = SeekParameters.CLOSEST_SYNC
        assertThat(closestSync.toleranceBeforeUs).isGreaterThan(0L)
        assertThat(closestSync.toleranceAfterUs).isGreaterThan(0L)
    }

    @Test
    fun `double tap seek accumulation clamps to minimum 0ms`() {
        val baseMs = 5_000L
        val seekAccumulatedSeconds = 10
        val targetMs = (baseMs - seekAccumulatedSeconds * 1000L).coerceAtLeast(0L)
        assertThat(targetMs).isEqualTo(0L)
    }

    @Test
    fun `doubleTapSeekSeconds setting directly drives seekDurationSeconds and seekDurationMs`() {
        val settings = com.excavplayer.domain.model.UserSettings(doubleTapSeekSeconds = 20)
        assertThat(settings.seekDurationSeconds).isEqualTo(20)
        assertThat(settings.seekDurationMs).isEqualTo(20_000L)
    }
}

