package com.excavplayer.policy

import com.excavplayer.player.policy.PlaybackFallbackSelection
import com.excavplayer.player.policy.ScrubMath
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScrubMathTest {

    // ── origin ───────────────────────────────────────────────────────

    @Test
    fun `origin prefers the active scrub target over the live position`() {
        assertThat(ScrubMath.scrubOrigin(activeTargetMs = 40_000L, currentPositionMs = 10_000L))
            .isEqualTo(40_000L)
        assertThat(ScrubMath.scrubOrigin(activeTargetMs = null, currentPositionMs = 10_000L))
            .isEqualTo(10_000L)
        assertThat(ScrubMath.scrubOrigin(activeTargetMs = null, currentPositionMs = -5L))
            .isEqualTo(0L)
    }

    // ── swipe mapping ────────────────────────────────────────────────

    @Test
    fun `zero drag fraction keeps the origin`() {
        assertThat(ScrubMath.targetFromSwipe(30_000L, 0f, 120_000L)).isEqualTo(30_000L)
    }

    @Test
    fun `half-screen drag maps onto the clamp span for short media`() {
        // duration 120s -> span clamp: min(60s..180s) of 120/2.2 = 54.5s -> clamped to 60s
        val target = ScrubMath.targetFromSwipe(0L, 0.5f, 120_000L)
        assertThat(target).isEqualTo(30_000L)
    }

    @Test
    fun `span scales with duration until the cap`() {
        // duration 440s -> 440/2.2 = 200s -> capped at 180s; full drag adds 180s
        assertThat(ScrubMath.targetFromSwipe(0L, 1f, 440_000L)).isEqualTo(180_000L)
        // duration 660s -> 660/2.2 = 300s -> capped at 180s
        assertThat(ScrubMath.targetFromSwipe(0L, 1f, 660_000L)).isEqualTo(180_000L)
    }

    @Test
    fun `negative drag sweeps backwards and clamps at zero`() {
        assertThat(ScrubMath.targetFromSwipe(30_000L, -1f, 440_000L)).isEqualTo(0L)
    }

    @Test
    fun `drag target clamps to duration`() {
        // origin 400s + full forward drag cannot pass the 440s end
        assertThat(ScrubMath.targetFromSwipe(400_000L, 1f, 440_000L)).isEqualTo(440_000L)
    }

    @Test
    fun `zero duration leaves the origin untouched`() {
        assertThat(ScrubMath.targetFromSwipe(12_000L, 0.9f, 0L)).isEqualTo(12_000L)
    }

    // ── commit ───────────────────────────────────────────────────────

    @Test
    fun `commit uses the active scrub target when present`() {
        assertThat(ScrubMath.commitTarget(45_000L, 10_000L, 100_000L)).isEqualTo(45_000L)
    }

    @Test
    fun `commit without a scrub falls back to the live position`() {
        assertThat(ScrubMath.commitTarget(null, 10_000L, 100_000L)).isEqualTo(10_000L)
        assertThat(ScrubMath.commitTarget(null, -1L, 100_000L)).isEqualTo(0L)
    }

    @Test
    fun `commit clamps to duration and never goes negative`() {
        assertThat(ScrubMath.commitTarget(150_000L, 0L, 100_000L)).isEqualTo(100_000L)
        assertThat(ScrubMath.commitTarget(-10L, 5_000L, 100_000L)).isEqualTo(0L)
        assertThat(ScrubMath.commitTarget(null, 10_000L, 0L)).isEqualTo(0L)
    }
}

class PlaybackFallbackSelectionTest {

    @Test
    fun `fresh player read wins`() {
        assertThat(PlaybackFallbackSelection.pick(5_000L, 4_000L)).isEqualTo(5_000L)
    }

    @Test
    fun `zero player read falls back to the ticked value`() {
        assertThat(PlaybackFallbackSelection.pick(0L, 4_000L)).isEqualTo(4_000L)
    }

    @Test
    fun `both zero stays zero`() {
        assertThat(PlaybackFallbackSelection.pick(0L, 0L)).isEqualTo(0L)
    }
}
