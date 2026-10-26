package com.excavplayer.policy

import com.excavplayer.domain.policy.PlaybackThresholdPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackThresholdPolicyTest {

    @Test
    fun `seek step ladder matches the historical thresholds`() {
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(1.00f)).isEqualTo(5)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.99f)).isEqualTo(5)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.96f)).isEqualTo(10)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.95f)).isEqualTo(10)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.93f)).isEqualTo(20)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.92f)).isEqualTo(20)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.91f)).isEqualTo(30)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.90f)).isEqualTo(30)
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.80f)).isEqualTo(60)
    }

    @Test
    fun `seek step ms is the seconds ladder times 1000`() {
        assertThat(PlaybackThresholdPolicy.seekStepMs(0.99f)).isEqualTo(5_000L)
        assertThat(PlaybackThresholdPolicy.seekStepMs(0.50f)).isEqualTo(60_000L)
    }

    @Test
    fun `completion is the fixed named fraction`() {
        assertThat(PlaybackThresholdPolicy.COMPLETED_FRACTION).isEqualTo(0.95f)
    }

    @Test
    fun `isCompleted at and past the fraction`() {
        assertThat(PlaybackThresholdPolicy.isCompleted(94_999L, 100_000L)).isFalse()
        assertThat(PlaybackThresholdPolicy.isCompleted(95_000L, 100_000L)).isTrue()
        assertThat(PlaybackThresholdPolicy.isCompleted(100_000L, 100_000L)).isTrue()
    }

    @Test
    fun `zero duration never counts as completed`() {
        assertThat(PlaybackThresholdPolicy.isCompleted(0L, 0L)).isFalse()
    }

    @Test
    fun `completion and resume threshold are separate concepts`() {
        // A user threshold of 0.90 does NOT lower the completion bar:
        // resume logic may reset a 91%-position to the start, but the video
        // is still not marked watched (that is the fixed 0.95 fraction).
        assertThat(PlaybackThresholdPolicy.seekStepSeconds(0.90f)).isEqualTo(30)
        assertThat(PlaybackThresholdPolicy.isCompleted(91_000L, 100_000L)).isFalse()
    }
}
