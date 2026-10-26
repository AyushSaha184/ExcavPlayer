package com.excavplayer.player.policy

/**
 * Picks between the direct player read (freshest, but can be 0 while idle or
 * before prepare) and the last value the position ticker published.
 */
object PlaybackFallbackSelection {

    /** Player read when it carries data, otherwise the last ticked value. */
    fun pick(playerValue: Long, tickedValue: Long): Long =
        if (playerValue > 0) playerValue else tickedValue
}
