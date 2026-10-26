package com.excavplayer.domain.model

data class PlaybackPosition(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L
)

data class EqualizerState(
    val enabled: Boolean = false,
    val preset: String = "Custom",
    val bandLevels: String = "0,0,0,0,0",
    val bassBoostStrength: Int = 0
)

data class PlayerState(
    val currentVideo: Video? = null,
    val playback: PlaybackState = PlaybackState(),
    val availableAudioTracks: List<AudioTrack> = emptyList(),
    val availableVideoTracks: List<VideoTrack> = emptyList(),
    val availableSubtitleTracks: List<SubtitleTrack> = emptyList(),
    val chapters: List<MediaChapter> = emptyList(),
    val subtitleDelayMs: Long = 0L,
    val error: PlaybackError? = null,
    val isInPictureInPicture: Boolean = false,
    val isBackgroundAudio: Boolean = false,
    val equalizerState: EqualizerState = EqualizerState(),
    val isMuted: Boolean = false
)
