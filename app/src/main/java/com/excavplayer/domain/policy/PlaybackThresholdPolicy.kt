package com.excavplayer.domain.policy

/**
 * One policy for the time thresholds playback logic keeps re-deriving.
 *
 * Two separate concepts that historically collided under one variable:
 *  - **Completion** — a video counts as watched at a fixed fraction of its
 *    length (drives continue-watching row removal and the ENDED reset).
 *  - **Resume** — the user's resume threshold drives both the seek-step size
 *    and the "near the end" window that resets a resume point to the start.
 */
object PlaybackThresholdPolicy {

    /** Fraction of the duration at which playback counts as completed/watched. */
    const val COMPLETED_FRACTION = 0.95f

    /**
     * Seek-step ladder derived from the resume threshold: the further the
     * threshold sits from completion, the bigger the step.
     */
    fun seekStepSeconds(resumeThresholdPercent: Float): Int = when {
        resumeThresholdPercent >= 0.99f -> 5
        resumeThresholdPercent >= 0.95f -> 10
        resumeThresholdPercent >= 0.92f -> 20
        resumeThresholdPercent >= 0.90f -> 30
        else -> 60
    }

    fun seekStepMs(resumeThresholdPercent: Float): Long =
        seekStepSeconds(resumeThresholdPercent) * 1000L

    /** Position within [durationMs] counts as completed (fixed fraction). */
    fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs.toFloat() / durationMs.toFloat() >= COMPLETED_FRACTION
}
