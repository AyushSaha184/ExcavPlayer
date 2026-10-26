package com.excavplayer.player.policy

/**
 * Pure scrub math for the player screen: where a drag or slider maps to in
 * time, and where a released scrub commits to. No Compose, no player state —
 * everything in, one number out.
 */
object ScrubMath {

    /** Start point of a new gesture: the active scrub target if one survives, else the live position. */
    fun scrubOrigin(activeTargetMs: Long?, currentPositionMs: Long): Long =
        (activeTargetMs ?: currentPositionMs).coerceAtLeast(0L)

    /**
     * Swipe-scrub target: the drag's fraction of the screen width maps onto a
     * span that scales with duration (half the movie, clamped 60–180 s of full
     * drag), starting from the gesture origin.
     */
    fun targetFromSwipe(originPositionMs: Long, dragFraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return originPositionMs.coerceAtLeast(0L)
        val maxSwipeSpan = (durationMs / 2.2f).coerceIn(60_000f, 180_000f)
        return (originPositionMs + dragFraction * maxSwipeSpan)
            .toLong()
            .coerceIn(0L, durationMs)
    }

    /** Where a released scrub commits: the active target, or the live position when no scrub ran. */
    fun commitTarget(activeTargetMs: Long?, currentPositionMs: Long, durationMs: Long): Long {
        val target = activeTargetMs ?: currentPositionMs.coerceAtLeast(0L)
        return target.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
}
