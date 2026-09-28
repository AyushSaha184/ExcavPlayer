package com.excavplayer.domain.model

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    ENDED
}

data class PlaybackState(
    val videoId: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val selectedVideoTrackId: String? = null,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    val progressPercentage: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isCompleted: Boolean
        get() = progressPercentage >= 0.95f
}
