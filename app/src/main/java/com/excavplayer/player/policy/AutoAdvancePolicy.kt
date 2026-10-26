package com.excavplayer.player.policy

/**
 * Pure decision: should playback advance to the next queue entry when the
 * current video ends? The player engine owns persistence and the actual
 * transition; this object owns only the decision.
 *
 * Repeat-one replay happens inside the player engine and never reaches this
 * decision (a looping video does not "end" from the queue's point of view).
 */
object AutoAdvancePolicy {

    fun shouldAdvance(autoplayNextVideo: Boolean, hasNext: Boolean): Boolean =
        autoplayNextVideo && hasNext
}
