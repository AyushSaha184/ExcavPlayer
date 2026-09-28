package com.excavplayer.player

import com.google.common.truth.Truth.assertThat
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.PlaybackStatus
import org.junit.Test

class PlaybackCompletionTest {

    @Test
    fun `zero duration returns zero progress and not completed`() {
        val state = PlaybackState(
            videoId = "v1",
            currentPositionMs = 0L,
            durationMs = 0L
        )

        assertThat(state.progressPercentage).isEqualTo(0f)
        assertThat(state.isCompleted).isFalse()
    }

    @Test
    fun `progress is clamped between 0 and 1`() {
        val overflown = PlaybackState(
            videoId = "v1",
            currentPositionMs = 150000L,
            durationMs = 100000L
        )

        assertThat(overflown.progressPercentage).isEqualTo(1.0f)
        assertThat(overflown.isCompleted).isTrue()

        val negative = PlaybackState(
            videoId = "v1",
            currentPositionMs = -5000L,
            durationMs = 100000L
        )
        assertThat(negative.progressPercentage).isEqualTo(0.0f)
        assertThat(negative.isCompleted).isFalse()
    }

    @Test
    fun `completion threshold at 95 percent triggers isCompleted`() {
        val justBelow = PlaybackState(
            videoId = "v1",
            currentPositionMs = 94900L,
            durationMs = 100000L
        )
        assertThat(justBelow.progressPercentage).isLessThan(0.95f)
        assertThat(justBelow.isCompleted).isFalse()

        val atThreshold = PlaybackState(
            videoId = "v1",
            currentPositionMs = 95000L,
            durationMs = 100000L
        )
        assertThat(atThreshold.progressPercentage).isAtLeast(0.95f)
        assertThat(atThreshold.isCompleted).isTrue()

        val full = PlaybackState(
            videoId = "v1",
            currentPositionMs = 100000L,
            durationMs = 100000L
        )
        assertThat(full.isCompleted).isTrue()
    }

    @Test
    fun `extremely long video handles completion calculation without overflow`() {
        val tenHoursMs = 10L * 3600L * 1000L // 36,000,000 ms
        val nineHoursThirtyMinMs = (9.5 * 3600L * 1000L).toLong()

        val state = PlaybackState(
            videoId = "v_long",
            currentPositionMs = nineHoursThirtyMinMs,
            durationMs = tenHoursMs
        )

        assertThat(state.progressPercentage).isEqualTo(0.95f)
        assertThat(state.isCompleted).isTrue()
    }
}
