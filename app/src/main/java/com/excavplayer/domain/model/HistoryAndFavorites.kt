package com.excavplayer.domain.model

data class WatchHistoryEntry(
    val videoId: String,
    val firstPlayedTimestamp: Long,
    val lastPlayedTimestamp: Long,
    val totalWatchDurationMs: Long,
    val completionPercentage: Float,
    val isCompleted: Boolean,
    val lastPositionMs: Long
)

data class Favorite(
    val videoId: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)
